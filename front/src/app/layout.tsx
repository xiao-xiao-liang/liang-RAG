import type { Metadata } from "next";
import "./globals.css";
import { TooltipProvider } from "@/components/ui/tooltip";
import { Toaster } from "@/components/ui/sonner";
import { Navbar } from "@/components/Navbar";

export const metadata: Metadata = {
  title: "Liang RAG - AI Knowledge Chatbot",
  description: "An intelligent chatbot connected to your enterprise knowledge base.",
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html
      lang="zh-CN"
      className="h-full antialiased font-sans"
    >
      <body className="flex flex-col h-screen overflow-hidden bg-background text-foreground">
        <TooltipProvider>
          {/* 顶部全局导航栏 */}
          <Navbar />
          
          {/* 主工作台区域 */}
          <main className="flex-1 w-full flex flex-col overflow-hidden relative">
            {children}
          </main>
          
          {/* 全局 Toast 通知 */}
          <Toaster position="top-center" />
        </TooltipProvider>
      </body>
    </html>
  );
}
