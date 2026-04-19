import { NextRequest } from 'next/server';

/**
 * SSE 流式代理 Route Handler
 *
 * Next.js 的 rewrites 会 buffer 整个响应，导致 SSE 无法实现逐字流式输出。
 * 此 Route Handler 使用原生 fetch + ReadableStream 透传后端的 SSE 流，
 * 确保每个 SSE 事件实时推送到浏览器。
 *
 * 当此文件存在时，Next.js 会优先使用 Route Handler 处理 /api/chat/send，
 * 而非 next.config.ts 中的 rewrites。
 */

const BACKEND_URL = process.env.BACKEND_URL || 'http://127.0.0.1:8009';

export async function POST(request: NextRequest) {
  const body = await request.text();

  const backendResponse = await fetch(`${BACKEND_URL}/api/chat/send`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'Accept': 'text/event-stream',
    },
    body,
  });

  if (!backendResponse.ok) {
    return new Response(await backendResponse.text(), {
      status: backendResponse.status,
      headers: { 'Content-Type': 'application/json' },
    });
  }

  // 直接透传后端的 ReadableStream，不做任何缓冲
  return new Response(backendResponse.body, {
    status: 200,
    headers: {
      'Content-Type': 'text/event-stream',
      'Cache-Control': 'no-cache, no-transform',
      'Connection': 'keep-alive',
      'X-Accel-Buffering': 'no',
    },
  });
}
