"use client";

import React from "react";
import { AlertCircle } from "lucide-react";

interface ErrorBoundaryProps {
  children: React.ReactNode;
  fallback?: React.ReactNode;
}

interface ErrorBoundaryState {
  hasError: boolean;
  error: Error | null;
}

export class ErrorBoundary extends React.Component<ErrorBoundaryProps, ErrorBoundaryState> {
  constructor(props: ErrorBoundaryProps) {
    super(props);
    this.state = { hasError: false, error: null };
  }

  static getDerivedStateFromError(error: Error): ErrorBoundaryState {
    return { hasError: true, error };
  }

  componentDidCatch(error: Error, errorInfo: React.ErrorInfo) {
    console.error("ErrorBoundary caught an error:", error, errorInfo);
  }

  render() {
    if (this.state.hasError) {
      if (this.props.fallback) {
        return this.props.fallback;
      }
      return (
        <div className="flex items-center gap-2 p-3 text-sm text-red-500 bg-red-50 dark:bg-red-500/10 dark:text-red-400 rounded-lg border border-red-200 dark:border-red-900/50">
          <AlertCircle className="w-4 h-4 shrink-0" />
          <div className="flex-1 min-w-0">
            <p className="font-medium">此内容渲染出错</p>
            <p className="text-xs opacity-80 truncate">{this.state.error?.message}</p>
          </div>
          <button 
            onClick={() => this.setState({ hasError: false, error: null })}
            className="px-2 py-1 bg-white/50 dark:bg-black/20 hover:bg-white dark:hover:bg-black/50 rounded text-xs transition-colors"
          >
            重试
          </button>
        </div>
      );
    }

    return this.props.children;
  }
}
