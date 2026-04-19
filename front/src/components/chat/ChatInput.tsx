"use client";

import React, { useRef, useEffect } from "react";
import { Send, Square, Sparkles } from "lucide-react";
import { cn } from "@/lib/utils";

interface ChatInputProps {
  onSend: (value: string) => void;
  isTyping: boolean;
  onCancel?: () => void;
}

export function ChatInput({ onSend, isTyping, onCancel }: ChatInputProps) {
  const [value, setValue] = React.useState("");
  const textareaRef = useRef<HTMLTextAreaElement>(null);

  const handleSend = React.useCallback(() => {
    if (!value.trim() || isTyping) return;
    onSend(value);
    setValue("");
  }, [value, isTyping, onSend]);

  const handleKeyDown = React.useCallback((e: React.KeyboardEvent<HTMLTextAreaElement>) => {
    if (e.key === "Enter" && !e.shiftKey) {
      e.preventDefault();
      handleSend();
    }
  }, [handleSend]);

  useEffect(() => {
    if (textareaRef.current) {
      textareaRef.current.style.height = "auto";
      textareaRef.current.style.height = `${Math.min(textareaRef.current.scrollHeight, 200)}px`;
    }
  }, [value]);

  return (
    <div className="relative flex items-end w-full border border-zinc-200/80 dark:border-zinc-800/80 rounded-[1.5rem] bg-white/80 dark:bg-zinc-900/80 backdrop-blur-xl shadow-sm pl-4 pr-2 py-2 focus-within:ring-2 focus-within:ring-indigo-500/20 focus-within:border-indigo-500/50 transition-all duration-300">
      
      {/* Decorative left icon */}
      <div className="hidden sm:flex mb-2 mr-2 text-zinc-400">
        <Sparkles className={cn("w-5 h-5 transition-all", isTyping ? "animate-pulse text-indigo-500" : "text-indigo-400/70")} />
      </div>

      <textarea
        ref={textareaRef}
        value={value}
        onChange={(e) => setValue(e.target.value)}
        onKeyDown={handleKeyDown}
        placeholder="向 AI 大脑提问，输入 Shift + Enter 换行..."
        className="flex-1 max-h-[200px] min-h-[24px] resize-none bg-transparent py-2 focus:outline-none text-sm placeholder:text-zinc-400 outline-none leading-relaxed text-zinc-800 dark:text-zinc-100 [&::-webkit-scrollbar]:hidden [-ms-overflow-style:none] [scrollbar-width:none]"
        rows={1}
      />
      
      <div className="ml-2 mb-1 flex shrink-0">
        {isTyping ? (
          <button
            onClick={onCancel}
            className="p-2 sm:p-2.5 rounded-xl bg-zinc-800 dark:bg-zinc-200 text-white dark:text-black hover:opacity-80 transition-opacity flex items-center justify-center shadow-md"
            title="停止生成"
          >
            <Square className="w-4 h-4 sm:w-5 sm:h-5 fill-current" />
          </button>
        ) : (
          <button
            onClick={handleSend}
            disabled={!value.trim()}
            className={cn(
              "p-2 sm:p-2.5 rounded-xl transition-all duration-300 flex items-center justify-center",
              value.trim()
                ? "bg-gradient-to-r from-indigo-500 to-blue-600 text-white shadow-md hover:shadow-lg hover:from-indigo-600 hover:to-blue-700 hover:-translate-y-0.5"
                : "bg-zinc-100 dark:bg-zinc-800 text-zinc-400 dark:text-zinc-500 cursor-not-allowed"
            )}
            title="发送"
          >
            <Send className="w-4 h-4 sm:w-5 sm:h-5 ml-0.5" />
          </button>
        )}
      </div>
    </div>
  );
}
