"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { Message, useChatStream } from "@/hooks/useChatStream";
import { ChatInput } from "@/components/chat/ChatInput";
import { MessageBubble } from "@/components/chat/MessageBubble";
import { ConversationList } from "@/components/chat/ConversationList";
import { http } from "@/lib/http";
import { Sparkles, LibraryBig, Zap } from "lucide-react";

export default function ChatPage() {
  const { messages, setMessages, sendMessage, isTyping, cancelStream, conversationId, setConversationId } = useChatStream();
  const scrollRef = useRef<HTMLDivElement>(null);
  const [loadingHistory, setLoadingHistory] = useState(false);

  // Auto-scroll to bottom
  useEffect(() => {
    if (scrollRef.current) {
      scrollRef.current.scrollTo({
        top: scrollRef.current.scrollHeight,
        behavior: "smooth",
      });
    }
  }, [messages]);

  // 显式加载历史：仅在用户主动点击侧边栏会话时调用
  // 不再使用 useEffect 监听 conversationId，避免流式过程中 [START] 事件设置 conversationId 触发 loadHistory 覆盖正在接收的消息
  const handleSelectConversation = useCallback(async (id: string | undefined) => {
    if (isTyping) cancelStream();
    setConversationId(id);

    if (!id) {
      setMessages([]);
      return;
    }

    setLoadingHistory(true);
    try {
      const historyData: any[] = await http.get(`/api/chat/conversations/${id}/messages`);
      const formattedMessages: Message[] = historyData.map((m) => ({
        id: m.id || m.messageId,
        role: m.type === 'USER' ? 'user' : 'assistant',
        content: m.content
      }));
      setMessages(formattedMessages);
    } catch (err) {
      console.error("加载历史记录失败", err);
    } finally {
      setLoadingHistory(false);
    }
  }, [isTyping, cancelStream, setConversationId, setMessages]);

  return (
    <div className="flex flex-1 w-full h-full overflow-hidden relative bg-zinc-50 dark:bg-zinc-950">
      {/* Background Decor */}
      <div className="absolute inset-0 bg-[radial-gradient(ellipse_at_top,_var(--tw-gradient-stops))] from-indigo-100/40 via-zinc-50/0 to-zinc-50/0 dark:from-indigo-900/10 dark:via-zinc-950/0 dark:to-zinc-950/0 pointer-events-none" />

      {/* Sub-sidebar for Conversation List */}
      <div className="hidden lg:block w-72 h-full shrink-0 relative z-10 shadow-[1px_0_10px_rgba(0,0,0,0.02)]">
        <ConversationList
          activeId={conversationId}
          onSelect={handleSelectConversation}
        />
      </div>

      {/* Main Chat Area */}
      <div className="flex-1 flex flex-col h-full min-w-0 relative z-10">

        {/* Messages Scroll Area */}
        <div ref={scrollRef} className="flex-1 chat-scroll scroll-smooth min-h-0">
          {messages.length === 0 && !loadingHistory ? (
            <div className="flex flex-col items-center justify-center h-full min-h-[500px]">
              <div className="max-w-xl w-full flex flex-col items-center text-center animate-in fade-in slide-in-from-bottom-4 duration-700">
                <div className="w-20 h-20 md:w-24 md:h-24 rounded-[2.5rem] flex items-center justify-center bg-gradient-to-br from-indigo-500 to-blue-600 shadow-xl shadow-indigo-500/20 mb-8 transform hover:scale-105 transition-transform">
                  <Sparkles className="w-10 h-10 md:w-12 md:h-12 text-white animate-pulse" />
                </div>
                <h2 className="text-2xl md:text-3xl font-bold bg-gradient-to-r from-zinc-900 to-zinc-600 dark:from-zinc-100 dark:to-zinc-400 bg-clip-text text-transparent mb-4">
                  你好，我是企业智能大脑
                </h2>
                <p className="text-zinc-500 dark:text-zinc-400 text-sm md:text-base mb-10 max-w-md mx-auto leading-relaxed">
                  我已接入您的私有企业文档。您可以向我提问规章制度、技术规范、新人指南等任何知识库中的内容。
                </p>

                <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 w-full px-4">
                  <div className="flex items-start gap-3 p-4 rounded-2xl bg-white dark:bg-zinc-900 border border-zinc-200/60 dark:border-zinc-800/60 hover:shadow-md hover:border-indigo-200 dark:hover:border-indigo-800/50 cursor-pointer transition-all shrink-0 text-left" onClick={() => sendMessage('总结一下知识库中最新的技术规范文档概要')}>
                    <LibraryBig className="w-5 h-5 text-indigo-500 shrink-0 mt-0.5" />
                    <div>
                      <p className="font-semibold text-sm text-zinc-700 dark:text-zinc-300 mb-1">文档摘要归纳</p>
                      <p className="text-xs text-zinc-500">&ldquo;总结一下最新的技术规范&rdquo;</p>
                    </div>
                  </div>
                  <div className="flex items-start gap-3 p-4 rounded-2xl bg-white dark:bg-zinc-900 border border-zinc-200/60 dark:border-zinc-800/60 hover:shadow-md hover:border-blue-200 dark:hover:border-blue-800/50 cursor-pointer transition-all shrink-0 text-left" onClick={() => sendMessage('入职流程是怎样的？帮我列出前3天的必需待办事项')}>
                    <Zap className="w-5 h-5 text-blue-500 shrink-0 mt-0.5" />
                    <div>
                      <p className="font-semibold text-sm text-zinc-700 dark:text-zinc-300 mb-1">精准信息抽取</p>
                      <p className="text-xs text-zinc-500">&ldquo;列出新入职流程的待办事项&rdquo;</p>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          ) : (
            <div className="max-w-4xl mx-auto px-4 md:px-8 py-8 space-y-6 md:space-y-8 pb-8">
              {messages.map((msg, i) => (
                <div key={msg.id + i} className="animate-in fade-in slide-in-from-bottom-2 duration-300">
                  <MessageBubble message={msg} />
                </div>
              ))}
            </div>
          )}
        </div>

        {/* Input Area — 渐变遮罩从输入框顶部边界向上延伸 */}
        <div className="shrink-0 bg-zinc-50 dark:bg-zinc-950 relative">
          <div className="absolute bottom-full left-0 right-0 h-16 bg-gradient-to-t from-zinc-50 to-transparent dark:from-zinc-950 pointer-events-none" />
          <div className="max-w-4xl mx-auto px-4 md:px-8 pb-5">
            <ChatInput onSend={sendMessage} isTyping={isTyping} onCancel={cancelStream} />
            <div className="text-center text-xs text-zinc-400 dark:text-zinc-500 mt-3 font-medium">
              基于 RAG 模型生成的智能回答，内容推荐仅供参考。
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
