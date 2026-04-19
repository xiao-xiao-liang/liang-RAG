"use client";

import React, { memo, useMemo } from "react";
import ReactMarkdown from "react-markdown";
import remarkGfm from "remark-gfm";
import { Copy, Check, Sparkles, ThumbsUp, ThumbsDown, RotateCcw, ChevronDown } from "lucide-react";
import { cn } from "@/lib/utils";
import { Message } from "@/hooks/useChatStream";
import { CodeBlock } from "./CodeBlock";
import { ErrorBoundary } from "./ErrorBoundary";

interface MessageBubbleProps {
  message: Message;
}

export const MessageBubble = memo(function MessageBubble({ message }: MessageBubbleProps) {
  const isUser = message.role === "user";

  const processedContent = useMemo(() => {
    let content = message.content || "";
    if (isUser || !content) return content;
    
    // Fix LLM generating keywords directly attached to language tag (e.g., ```javaimport -> ```java\nimport)
    content = content.replace(/```([a-zA-Z]+)(import|public|class|const|let|var|function|def|fn|export|#|\/\/|<)\b/gi, "```$1\n$2");
    
    // Fix LLM using space instead of newline (e.g., ```java import -> ```java\nimport)
    // This prevents losing the first line of code in react-markdown v10+
    content = content.replace(/(```[a-zA-Z0-9_\-\+\.]+)[ \t]+([^ \t\n]+.*)/g, "$1\n$2");

    // Fix missing spaces in markdown headings (e.g., ###总结 -> ### 总结)
    content = content.replace(/^(#{1,6})([^#\s])/gm, '$1 $2');

    // Fix streaming markdown table block
    // 采用追加换行符的方式，帮助 remark-gfm 引擎在流式输出中识别未闭合的 markdown 表格
    if (message.isStreaming && !content.endsWith("\n")) {
      content += "\n";
    }

    return content;
  }, [message.content, isUser, message.isStreaming]);

  return (
    <div className={cn("flex w-full group relative mb-2 sm:mb-4", isUser ? "justify-end" : "justify-start")}>
      <ErrorBoundary>
        <div className={cn(
        "flex w-full", 
        isUser ? "max-w-[90%] md:max-w-[75%] justify-end" : "justify-start"
      )}>
        {/* Message Content */}
        <div className={cn("flex flex-col gap-1.5 min-w-0 relative", isUser ? "items-end" : "w-full")}>
          <div
            className={cn(
              "text-[15px] leading-relaxed relative min-w-0",
              isUser 
                ? "max-w-full bg-zinc-100 dark:bg-zinc-800 text-zinc-900 dark:text-zinc-100 px-5 py-3.5 rounded-3xl" 
                : "w-full text-zinc-800 dark:text-zinc-200 overflow-hidden"
            )}
          >
            {isUser ? (
              <div className="whitespace-pre-wrap">{message.content}</div>
            ) : (
              <div className="prose prose-sm dark:prose-invert max-w-none prose-p:my-1 prose-pre:my-2 prose-pre:bg-zinc-950 prose-pre:p-0 prose-a:text-indigo-500 hover:prose-a:text-indigo-600 prose-hr:my-3 prose-hr:border-zinc-200/80 dark:prose-hr:border-zinc-800/80">
                {!message.content && message.isStreaming ? (
                  <div className="flex items-center gap-2 h-[24px]">
                    <Sparkles className="w-5 h-5 text-indigo-500 animate-pulse" />
                  </div>
                ) : (
                  <ReactMarkdown
                    remarkPlugins={[remarkGfm]}
                  components={{
                    pre({ children }) {
                      return <div className="my-4">{children}</div>;
                    },
                    table({ children, ...props }: any) {
                      return (
                        <div className="w-full overflow-x-auto custom-scrollbar">
                          <table {...props}>{children}</table>
                        </div>
                      );
                    },
                    code({ node, inline, className, children, ...props }: any) {
                      const match = /language-([a-zA-Z0-9_\-\+\.]+)/.exec(className || "");
                      const code = String(children).replace(/\n$/, "");
                      
                      if (!inline && match) {
                        return <CodeBlock language={match[1]} code={code} />;
                      }
                      return (
                        <code className={cn("bg-zinc-100 dark:bg-zinc-800/60 text-zinc-700 dark:text-zinc-300 px-1.5 py-0.5 mx-[2px] rounded-md text-[13.5px] font-mono whitespace-pre-wrap break-words", className)}>
                          {children}
                        </code>
                      );
                    },
                  }}
                >
                  {processedContent}
                </ReactMarkdown>
                )}
              </div>
            )}
          </div>
          
          {/* Action Toolbar */}
          {!isUser && !message.isStreaming && <MessageActions content={message.content} />}
        </div>
      </div>
      </ErrorBoundary>
    </div>
  );
});



function MessageActions({ content }: { content: string }) {
  const [copied, setCopied] = React.useState(false);
  const [showDropdown, setShowDropdown] = React.useState(false);
  const dropdownRef = React.useRef<HTMLDivElement>(null);

  React.useEffect(() => {
    function handleClickOutside(event: MouseEvent) {
      if (dropdownRef.current && !dropdownRef.current.contains(event.target as Node)) {
        setShowDropdown(false);
      }
    }
    document.addEventListener("mousedown", handleClickOutside);
    return () => document.removeEventListener("mousedown", handleClickOutside);
  }, []);

  const handleCopy = async (isMarkdown: boolean = false) => {
    try {
      await navigator.clipboard.writeText(content);
      setCopied(true);
      setShowDropdown(false);
      setTimeout(() => setCopied(false), 2000);
    } catch (err) {
      console.error("Failed to copy text: ", err);
    }
  };

  return (
    <div className="flex items-center gap-1.5 px-0.5 mt-1 text-zinc-400 dark:text-zinc-500 pb-1 w-full">
      <div className="relative" ref={dropdownRef}>
        <div className="flex items-center rounded-md border border-transparent hover:bg-zinc-100 dark:hover:bg-zinc-800/50 transition-colors group/copy">
          <button
            onClick={() => handleCopy(false)}
            className="p-1.5 rounded-l-md hover:text-zinc-700 dark:hover:text-zinc-300"
            title="复制"
          >
            {copied ? <Check className="w-[15px] h-[15px] stroke-[2px] text-emerald-500" /> : <Copy className="w-[15px] h-[15px] stroke-[2px]" />}
          </button>
          <button 
            onClick={() => setShowDropdown(!showDropdown)}
            className="p-1.5 pl-0.5 rounded-r-md hover:text-zinc-700 dark:hover:text-zinc-300 flex items-center justify-center opacity-80"
          >
            <ChevronDown className="w-3.5 h-3.5" />
          </button>
        </div>

        {showDropdown && (
          <div className="absolute top-10 left-0 z-10 w-36 bg-white dark:bg-zinc-900 border border-zinc-200 dark:border-zinc-800 rounded-lg shadow-lg py-1.5 text-[13px] text-zinc-700 dark:text-zinc-300 animate-in fade-in slide-in-from-top-2 duration-200">
            <button 
              onClick={() => handleCopy(false)} 
              className="w-full text-left px-3 py-1.5 hover:bg-zinc-100 dark:hover:bg-zinc-800 transition-colors"
            >
              复制
            </button>
            <button 
              onClick={() => handleCopy(true)} 
              className="w-full text-left px-3 py-1.5 hover:bg-zinc-100 dark:hover:bg-zinc-800 transition-colors"
            >
              复制为Markdown
            </button>
          </div>
        )}
      </div>

      <button className="p-1.5 rounded-md hover:bg-zinc-100 dark:hover:bg-zinc-800/50 hover:text-zinc-700 dark:hover:text-zinc-300 transition-colors" title="赞同">
        <ThumbsUp className="w-[15px] h-[15px] stroke-[2px]" />
      </button>
      <button className="p-1.5 rounded-md hover:bg-zinc-100 dark:hover:bg-zinc-800/50 hover:text-zinc-700 dark:hover:text-zinc-300 transition-colors" title="反对">
        <ThumbsDown className="w-[15px] h-[15px] stroke-[2px] mt-[1px]" />
      </button>
      <button className="p-1.5 rounded-md hover:bg-zinc-100 dark:hover:bg-zinc-800/50 hover:text-zinc-700 dark:hover:text-zinc-300 transition-colors" title="重新生成">
        <RotateCcw className="w-[15px] h-[15px] stroke-[2px]" />
      </button>
    </div>
  );
}
