import { useState, useCallback, useRef } from 'react';
import { MOCK_USER_ID, MOCK_USER_NAME, http } from '@/lib/http';
import { parseSseEventData } from '@/lib/sse';
import { mutate } from 'swr';
import { toast } from 'sonner';

export interface Message {
  id: string;
  role: 'user' | 'assistant';
  content: string;
  isStreaming?: boolean;
}

/** 标题轮询间隔（毫秒） */
const TITLE_POLL_INTERVAL = 1500;
/** 标题轮询最大重试次数 */
const TITLE_POLL_MAX_RETRIES = 3;

/**
 * 精准标题轮询
 * <p>
 * 新会话创建后，后端虚拟线程异步调用 LLM 生成标题。
 * 前端不再全量刷新会话列表，而是定时查询单个 title 字段，
 * 拿到 AI 标题后局部更新 SWR 缓存中的对应会话。
 * </p>
 *
 * @param conversationId 会话ID
 * @param userContent    用户首条消息（用于判断标题是否仍为临时截断值）
 */
function pollConversationTitle(conversationId: string, userContent: string) {
  // 后端临时标题 = content.substring(0, 20)，这里用同样逻辑判断是否仍为临时标题
  const tempTitle = userContent.substring(0, 20);
  let retries = 0;

  const poll = async () => {
    retries++;
    try {
      const title: string = await http.get(`/api/chat/conversations/${conversationId}/title`);
      const isTempTitle = title === tempTitle || title === userContent.substring(0, 15);

      if (!isTempTitle || retries >= TITLE_POLL_MAX_RETRIES) {
        // 拿到 AI 标题（或超过最大重试次数），局部更新 SWR 缓存
        const sidebarKey = `/api/chat/conversations?userId=${MOCK_USER_ID}`;
        mutate(
          sidebarKey,
          (conversations: any[] | undefined) =>
            conversations?.map((conv) =>
              conv.conversationId === conversationId ? { ...conv, title } : conv
            ),
          false // revalidate=false → 纯本地更新，不触发网络请求
        );
        return;
      }

      // 标题仍是临时值，继续轮询
      setTimeout(poll, TITLE_POLL_INTERVAL);
    } catch (err) {
      console.warn('标题轮询失败，保留临时标题', err);
    }
  };

  // 延迟 1.5s 后启动首次轮询，给后端虚拟线程生成标题留出时间
  setTimeout(poll, TITLE_POLL_INTERVAL);
}

export function useChatStream() {
  const [messages, setMessages] = useState<Message[]>([]);
  const [isTyping, setIsTyping] = useState(false);
  const [conversationId, setConversationId] = useState<string | undefined>();
  const abortControllerRef = useRef<AbortController | null>(null);

  const sendMessage = useCallback(async (content: string) => {
    if (!content.trim()) return;

    // 标记是否为新会话（没有 conversationId 说明后端会新建）
    const isNewConversation = !conversationId;

    const userMessage: Message = { id: Date.now().toString(), role: 'user', content };
    const assistantMessageId = (Date.now() + 1).toString();

    setMessages((prev) => [...prev, userMessage, { id: assistantMessageId, role: 'assistant', content: '', isStreaming: true }]);
    setIsTyping(true);

    abortControllerRef.current = new AbortController();
    let streamCompleted = false;

    try {
      const response = await fetch('/api/chat/send', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          userId: MOCK_USER_ID,
          content,
          conversationId: conversationId,
        }),
        signal: abortControllerRef.current.signal,
      });

      if (!response.ok || !response.body) {
        const errText = await response.text().catch(() => '');
        throw new Error(`请求失败 (${response.status}): ${errText}`);
      }

      // 使用原生 ReadableStream 逐字节读取，手动解析 SSE 事件
      // 彻底避免 fetchEventSource 在 Next.js rewrite 代理下丢失换行符的问题
      const reader = response.body.getReader();
      const decoder = new TextDecoder();
      let sseBuffer = '';

      while (true) {
        const { done, value } = await reader.read();
        if (done) break;

        sseBuffer += decoder.decode(value, { stream: true });

        // SSE 事件以 \n\n 作为分隔符
        let eventEnd: number;
        while ((eventEnd = sseBuffer.indexOf('\n\n')) !== -1) {
          const eventText = sseBuffer.substring(0, eventEnd);
          sseBuffer = sseBuffer.substring(eventEnd + 2);

          const data = parseSseEventData(eventText);
          if (data === null) continue;

          // 处理控制事件: [START]
          if (data.startsWith('[START]')) {
            const convId = data.split(':')[1];
            if (convId) {
              setConversationId(convId);
              if (isNewConversation) {
                // 全量刷新列表，让新会话出现在侧边栏（此时显示临时标题）
                mutate(`/api/chat/conversations?userId=${MOCK_USER_ID}`);
                // 立即启动标题轮询：后端虚拟线程此时已在生成 AI 标题，
                // 1.5s 后开始查询，标题在流式回复过程中就能异步更新到侧边栏
                pollConversationTitle(convId, content);
              }
            }
            continue;
          }

          // 处理控制事件: [DONE]
          if (data.startsWith('[DONE]')) {
            streamCompleted = true;
            const convId = data.split(':')[1];
            if (convId) {
              setConversationId(convId);
            }
            continue;
          }

          // 解析 JSON 编码的 token（后端用 JSON 编码以保留换行符和空格）
          let token: string;
          try {
            token = JSON.parse(data);
          } catch {
            // 非 JSON 格式（兼容旧格式或未知数据），直接使用原始值
            token = data;
          }

          // 追加 AI 回复内容
          setMessages((prev) =>
            prev.map((msg) => {
              if (msg.id === assistantMessageId) {
                return { ...msg, content: msg.content + token };
              }
              return msg;
            })
          );
        }
      }

      // 流正常结束检查
      if (!streamCompleted) {
        console.warn('SSE 流结束但未收到 [DONE] 标记');
      }
    } catch (err: any) {
      // 用户主动取消不弹错误提示
      if (err?.name === 'AbortError') return;

      console.error('流式对话异常:', err);
      toast.error('对话连接异常，请重试');
      setMessages((prev) =>
        prev.map((msg) =>
          msg.id === assistantMessageId
            ? { ...msg, content: msg.content || 'Oops, 出错了，请稍后再试。', isStreaming: false }
            : msg
        )
      );
    } finally {
      // 无论成功/失败/取消，都标记流结束
      setMessages((prev) =>
        prev.map((msg) =>
          msg.id === assistantMessageId ? { ...msg, isStreaming: false } : msg
        )
      );
      setIsTyping(false);
      abortControllerRef.current = null;
    }
  }, [conversationId]);

  const cancelStream = useCallback(() => {
    if (abortControllerRef.current) {
      abortControllerRef.current.abort();
      abortControllerRef.current = null;
      setIsTyping(false);

      setMessages((prev) => {
        const lastMsg = prev[prev.length - 1];
        if (lastMsg && lastMsg.role === 'assistant' && lastMsg.isStreaming) {
          return prev.map((msg) => msg.id === lastMsg.id ? { ...msg, isStreaming: false } : msg);
        }
        return prev;
      });
    }
  }, []);

  return { messages, setMessages, sendMessage, isTyping, cancelStream, conversationId, setConversationId };
}
