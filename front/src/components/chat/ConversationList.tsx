"use client";

import React, { useRef, useCallback, useEffect, useState } from "react";
import useSWR from "swr";
import { MOCK_USER_ID, swrFetcher, http } from "@/lib/http";
import { MessageSquare, Sparkles, Ellipsis, Pencil, Share2, Pin, Trash2 } from "lucide-react";
import { cn } from "@/lib/utils";
import { toast } from "sonner";
import { Skeleton } from "@/components/ui/skeleton";
import { useVirtualizer } from "@tanstack/react-virtual";

interface Conversation {
  id: string;
  conversationId: string;
  title: string;
  createTime: string;
}

interface ConversationListProps {
  activeId?: string;
  onSelect: (id: string | undefined) => void;
}

export function ConversationList({ activeId, onSelect }: ConversationListProps) {
  const { data: conversations, error, isLoading, mutate } = useSWR<Conversation[]>(
    `/api/chat/conversations?userId=${MOCK_USER_ID}`,
    swrFetcher,
    {
      revalidateOnFocus: false,
      shouldRetryOnError: false,
      errorRetryCount: 0
    }
  );

  const [menuOpenId, setMenuOpenId] = useState<string | null>(null);
  const menuRef = useRef<HTMLDivElement>(null);
  const previousFocusRef = useRef<HTMLElement | null>(null);

  const [editingId, setEditingId] = useState<string | null>(null);
  const [editTitle, setEditTitle] = useState<string>("");

  const parentRef = useRef<HTMLDivElement>(null);

  // 虚拟化列表
  const safeConversations = conversations || [];
  const rowVirtualizer = useVirtualizer({
    count: safeConversations.length,
    getScrollElement: () => parentRef.current,
    estimateSize: () => 48, 
    overscan: 5,
  });

  // 点击外部关闭菜单并恢复焦点
  useEffect(() => {
    function handleClickOutside(event: MouseEvent) {
      if (menuRef.current && !menuRef.current.contains(event.target as Node)) {
        setMenuOpenId(null);
        previousFocusRef.current?.focus();
      }
    }
    if (menuOpenId) {
      document.addEventListener("mousedown", handleClickOutside);
      return () => document.removeEventListener("mousedown", handleClickOutside);
    }
  }, [menuOpenId]);

  const handleDelete = useCallback(async (id: string) => {
    setMenuOpenId(null);
    try {
      await http.delete(`/api/chat/conversations/${id}`);
      if (activeId === id) {
        onSelect(undefined);
      }
      mutate();
      toast.success("会话已删除");
    } catch {
      toast.error("删除失败");
    }
  }, [activeId, mutate, onSelect]);

  const handleRename = useCallback((id: string, currentTitle: string) => {
    setMenuOpenId(null);
    setEditingId(id);
    setEditTitle(currentTitle || "新对话");
  }, []);

  const submitRename = useCallback(async (id: string) => {
    if (!editTitle.trim()) {
      setEditingId(null);
      return;
    }
    try {
      await http.put(`/api/chat/conversations/${id}/title?title=${encodeURIComponent(editTitle.trim())}`);
      mutate();
      toast.success("重命名成功");
    } catch {
      toast.error("重命名失败");
    } finally {
      setEditingId(null);
      previousFocusRef.current?.focus();
    }
  }, [editTitle, mutate]);

  const handlePin = useCallback((id: string) => {
    setMenuOpenId(null);
    toast.info("置顶功能开发中");
  }, []);

  const handleShare = useCallback((id: string) => {
    setMenuOpenId(null);
    toast.info("分享功能开发中");
  }, []);

  // 键盘导航菜单
  const handleDropdownKeyDown = useCallback((e: React.KeyboardEvent<HTMLDivElement>, id: string, title: string) => {
    const focusableElements = menuRef.current?.querySelectorAll('button') || [];
    const currentIndex = Array.from(focusableElements).findIndex(el => el === document.activeElement);

    if (e.key === 'ArrowDown') {
      e.preventDefault();
      const nextIndex = (currentIndex + 1) % focusableElements.length;
      (focusableElements[nextIndex] as HTMLElement).focus();
    } else if (e.key === 'ArrowUp') {
      e.preventDefault();
      const prevIndex = (currentIndex - 1 + focusableElements.length) % focusableElements.length;
      (focusableElements[prevIndex] as HTMLElement).focus();
    } else if (e.key === 'Escape') {
      e.preventDefault();
      setMenuOpenId(null);
      previousFocusRef.current?.focus();
    }
  }, []);

  const openMenu = (e: React.MouseEvent, id: string) => {
    e.stopPropagation();
    previousFocusRef.current = document.activeElement as HTMLElement;
    setMenuOpenId(menuOpenId === id ? null : id);
  };

  return (
    <div className="flex flex-col h-full bg-zinc-50/50 dark:bg-zinc-950/30 border-r border-zinc-200/80 dark:border-zinc-800/80">
      <div className="p-4 border-b border-zinc-200/80 dark:border-zinc-800/80 bg-white/50 dark:bg-zinc-900/30 backdrop-blur-sm">
        <button
          onClick={() => onSelect(undefined)}
          className="w-full flex items-center justify-center gap-2 bg-gradient-to-r from-indigo-500 to-blue-600 text-white hover:from-indigo-600 hover:to-blue-700 py-3 rounded-xl transition-all shadow-sm hover:shadow text-[15px] font-medium group"
        >
          <Sparkles className="w-4 h-4 group-hover:animate-pulse" />
          新建智能对话
        </button>
      </div>
      
      <div 
        ref={parentRef} 
        className="flex-1 overflow-y-auto px-2 py-3 custom-scrollbar"
      >
        {isLoading && (
          <div className="space-y-1.5 px-1">
            {[1, 2, 3, 4, 5, 6].map((k) => (
              <Skeleton key={k} className="h-9 w-full rounded-lg dark:bg-zinc-800/50" />
            ))}
          </div>
        )}
        
        {!isLoading && !error && safeConversations.length === 0 && (
          <div className="flex flex-col items-center justify-center text-center py-10 opacity-60">
            <MessageSquare className="w-10 h-10 mb-3 text-zinc-400 stroke-[1]" />
            <p className="text-sm font-medium text-zinc-500">尚无对话历史</p>
          </div>
        )}

        {!isLoading && safeConversations.length > 0 && (
          <div
            style={{
              height: `${rowVirtualizer.getTotalSize()}px`,
              width: "100%",
              position: "relative",
            }}
          >
            {rowVirtualizer.getVirtualItems().map((virtualRow) => {
              const conv = safeConversations[virtualRow.index];
              const isActive = activeId === conv.conversationId;
              const isMenuOpen = menuOpenId === conv.conversationId;

              return (
                <div
                  key={virtualRow.key}
                  style={{
                    position: "absolute",
                    top: 0,
                    left: 0,
                    width: "100%",
                    height: `${virtualRow.size}px`,
                    transform: `translateY(${virtualRow.start}px)`,
                    paddingBottom: "4px"
                  }}
                >
                  <div
                    onClick={() => onSelect(conv.conversationId)}
                    className={cn(
                      "group relative flex items-center gap-2 px-3 py-2.5 rounded-lg cursor-pointer transition-all duration-150 h-full",
                      isActive
                        ? "bg-white dark:bg-zinc-800/80 shadow-sm text-indigo-600 dark:text-indigo-400"
                        : "text-zinc-700 dark:text-zinc-300 hover:bg-zinc-100/80 dark:hover:bg-zinc-800/40"
                    )}
                  >
                    {/* 标题 */}
                    <div className={cn(
                      "flex-1 min-w-0 truncate text-[13.5px] leading-snug",
                      isActive ? "font-semibold" : "font-medium"
                    )}>
                      {editingId === conv.conversationId ? (
                        <input
                          autoFocus
                          value={editTitle}
                          aria-label="编辑会话名称"
                          onChange={(e) => setEditTitle(e.target.value)}
                          onKeyDown={(e) => {
                            if (e.key === "Enter") {
                              e.stopPropagation();
                              submitRename(conv.conversationId);
                            } else if (e.key === "Escape") {
                              e.stopPropagation();
                              setEditingId(null);
                              previousFocusRef.current?.focus();
                            }
                          }}
                          onBlur={() => submitRename(conv.conversationId)}
                          onClick={(e) => e.stopPropagation()}
                          className="w-full bg-transparent outline-none border-b border-indigo-400 dark:border-indigo-600 focus:border-indigo-500 rounded-none px-0 py-0 text-inherit"
                        />
                      ) : (
                        conv.title || "新对话"
                      )}
                    </div>

                    {/* 更多操作按钮：悬浮出现 */}
                    <div className={cn(
                      "shrink-0 transition-opacity duration-150",
                      isMenuOpen ? "opacity-100" : "opacity-0 group-hover:opacity-100"
                    )}>
                      <button
                        onClick={(e) => openMenu(e, conv.conversationId)}
                        aria-label="更多选项"
                        aria-haspopup="menu"
                        aria-expanded={isMenuOpen}
                        className={cn(
                          "p-1 rounded-md transition-colors",
                          isActive
                            ? "hover:bg-indigo-100 dark:hover:bg-indigo-500/20 text-indigo-500"
                            : "hover:bg-zinc-200/80 dark:hover:bg-zinc-700/60 text-zinc-400 hover:text-zinc-600 dark:hover:text-zinc-300"
                        )}
                      >
                        <Ellipsis className="w-4 h-4" />
                      </button>
                    </div>

                    {/* 下拉菜单 */}
                    {isMenuOpen && (
                      <div
                        ref={menuRef}
                        role="menu"
                        onKeyDown={(e) => handleDropdownKeyDown(e, conv.conversationId, conv.title)}
                        className="absolute right-2 top-full mt-1 z-50 w-40 bg-white dark:bg-zinc-900 border border-zinc-200/80 dark:border-zinc-800 rounded-xl shadow-lg py-1 text-[13px] text-zinc-700 dark:text-zinc-300 animate-in fade-in slide-in-from-top-2 duration-150"
                        onClick={(e) => e.stopPropagation()}
                      >
                        <button
                          role="menuitem"
                          onClick={() => handleRename(conv.conversationId, conv.title)}
                          className="w-full flex items-center gap-2.5 px-3 py-2 hover:bg-zinc-100 dark:hover:bg-zinc-800 transition-colors focus:bg-zinc-100 dark:focus:bg-zinc-800 outline-none"
                        >
                          <Pencil className="w-3.5 h-3.5 text-zinc-400" />
                          编辑名称
                        </button>
                        <button
                          role="menuitem"
                          onClick={() => handleShare(conv.conversationId)}
                          className="w-full flex items-center gap-2.5 px-3 py-2 hover:bg-zinc-100 dark:hover:bg-zinc-800 transition-colors focus:bg-zinc-100 dark:focus:bg-zinc-800 outline-none"
                        >
                          <Share2 className="w-3.5 h-3.5 text-zinc-400" />
                          分享
                        </button>
                        <button
                          role="menuitem"
                          onClick={() => handlePin(conv.conversationId)}
                          className="w-full flex items-center gap-2.5 px-3 py-2 hover:bg-zinc-100 dark:hover:bg-zinc-800 transition-colors focus:bg-zinc-100 dark:focus:bg-zinc-800 outline-none"
                        >
                          <Pin className="w-3.5 h-3.5 text-zinc-400" />
                          置顶
                        </button>
                        <div className="my-1 border-t border-zinc-100 dark:border-zinc-800" />
                        <button
                          role="menuitem"
                          onClick={() => handleDelete(conv.conversationId)}
                          className="w-full flex items-center gap-2.5 px-3 py-2 text-red-500 hover:bg-red-50 dark:hover:bg-red-500/10 transition-colors focus:bg-red-50 dark:focus:bg-red-500/10 outline-none"
                        >
                          <Trash2 className="w-3.5 h-3.5" />
                          删除
                        </button>
                      </div>
                    )}
                  </div>
                </div>
              );
            })}
          </div>
        )}
      </div>
    </div>
  );
}
