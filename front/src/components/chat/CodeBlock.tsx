"use client";

import React, { memo } from "react";
import { Prism as SyntaxHighlighter } from "react-syntax-highlighter";
import { oneDark, oneLight } from "react-syntax-highlighter/dist/cjs/styles/prism";
import { Copy, Check, Download, Play, Maximize } from "lucide-react";
import { cn } from "@/lib/utils";

interface CodeBlockProps {
  language: string;
  code: string;
}

export const CodeBlock = memo(function CodeBlock({ language, code }: CodeBlockProps) {
  return (
    <div className="rounded-xl border border-zinc-200/80 dark:border-zinc-800/80 shadow-sm bg-[#faf8f5] dark:bg-[#1e1e1e] flex flex-col pt-0 my-5 overflow-hidden">
      <div className="flex items-center justify-between px-4 py-2 border-b border-zinc-200/80 dark:border-zinc-800/80 bg-[#faf8f5] dark:bg-[#1e1e1e]">
        <span className="text-[14px] text-zinc-900 dark:text-zinc-100 font-bold font-mono tracking-wide lowercase">{language}</span>
        <div className="flex items-center space-x-0.5 text-zinc-500 dark:text-zinc-400">
          <button className="flex items-center gap-1.5 px-2 py-1.5 rounded-md hover:bg-zinc-200/60 dark:hover:bg-zinc-800 transition-colors text-[13px]" title="下载代码">
            <Download className="w-[15px] h-[15px] stroke-[1.5px]" /> <span>下载</span>
          </button>
          <CopyButton content={code} showText={true} />
          <button className="flex items-center gap-1.5 px-2 py-1.5 rounded-md hover:bg-zinc-200/60 dark:hover:bg-zinc-800 transition-colors text-[13px]" title="在环境中运行">
            <Play className="w-[15px] h-[15px] stroke-[1.5px] fill-current" /> <span>运行</span>
          </button>
          <div className="w-[1px] h-3.5 bg-zinc-300 dark:bg-zinc-700 mx-1"></div>
          <button className="flex items-center px-1.5 py-1.5 rounded-md hover:bg-zinc-200/60 dark:hover:bg-zinc-800 transition-colors" title="全屏显示">
            <Maximize className="w-[15px] h-[15px] stroke-[1.5px]" />
          </button>
        </div>
      </div>
      <div className="overflow-x-auto text-[14px] w-full font-mono leading-relaxed">
        {/* Light Mode Highlighter */}
        <div className="block dark:hidden w-full">
          <SyntaxHighlighter
            style={oneLight}
            language={language}
            PreTag="div"
            CodeTag="div"
            className="!my-0 !bg-transparent custom-scrollbar w-full"
            customStyle={{ margin: 0, padding: '1.25rem', background: 'transparent', whiteSpace: 'pre-wrap' }}
          >
            {code}
          </SyntaxHighlighter>
        </div>
        {/* Dark Mode Highlighter */}
        <div className="hidden dark:block w-full">
          <SyntaxHighlighter
            style={oneDark}
            language={language}
            PreTag="div"
            CodeTag="div"
            className="!my-0 !bg-transparent custom-scrollbar w-full"
            customStyle={{ margin: 0, padding: '1.25rem', background: 'transparent', whiteSpace: 'pre-wrap' }}
          >
            {code}
          </SyntaxHighlighter>
        </div>
      </div>
    </div>
  );
});

export function CopyButton({ content, showText = false }: { content: string, showText?: boolean }) {
  const [copied, setCopied] = React.useState(false);

  const handleCopy = async () => {
    try {
      await navigator.clipboard.writeText(content);
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    } catch (err) {
      console.error("Failed to copy text: ", err);
    }
  };

  return (
    <button
      onClick={handleCopy}
      className={cn(
        "flex items-center gap-1.5 rounded-md hover:text-zinc-800 dark:hover:text-zinc-200 hover:bg-zinc-200/60 dark:hover:bg-zinc-800 transition-colors",
        showText ? "px-2 py-1.5 text-[13px]" : "p-1 text-zinc-400"
      )}
      title="复制代码"
    >
      {copied ? <Check className="w-[15px] h-[15px] stroke-[2px] text-emerald-500" /> : <Copy className="w-[15px] h-[15px] stroke-[1.5px]" />}
      {showText && <span>{copied ? "已复制" : "复制"}</span>}
    </button>
  );
}
