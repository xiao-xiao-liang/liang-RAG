"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { MessageSquare, Database, Settings, Menu, X } from "lucide-react";
import { cn } from "@/lib/utils";
import { useState } from "react";

const NAV_ITEMS = [
  { name: "会话", href: "/", icon: MessageSquare },
  { name: "企业知识库", href: "/knowledge", icon: Database },
];

export function Navbar() {
  const pathname = usePathname();
  const [mobileMenuOpen, setMobileMenuOpen] = useState(false);

  return (
    <header className="w-full h-14 md:h-16 flex items-center justify-between px-4 lg:px-8 border-b border-zinc-200/80 dark:border-zinc-800/80 bg-white/80 dark:bg-zinc-950/80 backdrop-blur-md shrink-0 z-50">
      
      {/* Left section: Logo & Nav */}
      <div className="flex items-center gap-8">
        <Link href="/" className="flex items-center gap-2.5 transition-transform hover:scale-105">
          <div className="w-8 h-8 rounded-xl bg-gradient-to-br from-indigo-500 to-blue-600 flex items-center justify-center shrink-0 shadow-sm">
            <span className="text-white font-bold text-sm">LR</span>
          </div>
          <span className="font-bold text-lg hidden sm:block bg-gradient-to-r from-zinc-900 to-zinc-600 dark:from-zinc-100 dark:to-zinc-400 bg-clip-text text-transparent">Liang RAG</span>
        </Link>
        
        {/* Desktop Nav */}
        <nav className="hidden md:flex items-center gap-1">
          {NAV_ITEMS.map((item) => {
            const isActive = pathname === item.href || (pathname?.startsWith(`${item.href}/`) && item.href !== "/");
            return (
              <Link
                key={item.href}
                href={item.href}
                className={cn(
                  "flex items-center gap-2 px-3 py-2 rounded-lg text-sm font-medium transition-colors relative group",
                  isActive
                    ? "text-indigo-600 dark:text-indigo-400"
                    : "text-zinc-500 dark:text-zinc-400 hover:bg-zinc-100 hover:text-zinc-900 dark:hover:bg-zinc-900 dark:hover:text-zinc-100"
                )}
              >
                <item.icon className="w-4 h-4" />
                {item.name}
                {/* Active Indicator */}
                {isActive && (
                  <span className="absolute bottom-0 left-0 w-full h-[2px] bg-indigo-500 rounded-t-sm" style={{ bottom: '-18px' }} />
                )}
              </Link>
            );
          })}
        </nav>
      </div>

      {/* Right section: Profile & Settings */}
      <div className="hidden md:flex items-center gap-4">
        <button className="p-2 text-zinc-500 hover:text-zinc-900 hover:bg-zinc-100 dark:hover:bg-zinc-900 dark:hover:text-zinc-100 rounded-full transition-colors">
          <Settings className="w-5 h-5" />
        </button>
        <div className="w-8 h-8 rounded-full bg-gradient-to-br from-blue-400 to-indigo-500 border-2 border-white dark:border-zinc-900 shadow-sm flex items-center justify-center text-white text-[10px] font-bold cursor-pointer hover:shadow-md transition-shadow">
          ME
        </div>
      </div>

      {/* Mobile Menu Toggle */}
      <div className="md:hidden flex items-center">
        <button 
          onClick={() => setMobileMenuOpen(!mobileMenuOpen)}
          className="p-2 text-zinc-600 dark:text-zinc-400"
        >
          {mobileMenuOpen ? <X className="w-6 h-6" /> : <Menu className="w-6 h-6" />}
        </button>
      </div>
      
      {/* Mobile Menu Dropdown */}
      {mobileMenuOpen && (
        <div className="absolute top-14 left-0 w-full bg-white dark:bg-zinc-950 border-b border-zinc-200 dark:border-zinc-800 p-4 flex flex-col gap-2 shadow-lg md:hidden animate-in slide-in-from-top-2">
          {NAV_ITEMS.map((item) => {
            const isActive = pathname === item.href || (pathname?.startsWith(`${item.href}/`) && item.href !== "/");
            return (
              <Link
                key={item.href}
                href={item.href}
                onClick={() => setMobileMenuOpen(false)}
                className={cn(
                  "flex items-center gap-3 px-4 py-3 rounded-xl font-medium transition-colors",
                  isActive
                    ? "bg-indigo-50 text-indigo-600 dark:bg-indigo-500/10 dark:text-indigo-400"
                    : "text-zinc-600 hover:bg-zinc-100 dark:text-zinc-400 dark:hover:bg-zinc-900"
                )}
              >
                <item.icon className="w-5 h-5" />
                {item.name}
              </Link>
            );
          })}
        </div>
      )}
    </header>
  );
}
