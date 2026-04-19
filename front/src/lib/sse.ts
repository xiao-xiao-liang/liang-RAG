/**
 * 解析 SSE 原始数据行，提取 data 字段并按规范拼接换行符。
 * 
 * SSE 协议中事件块以 \n\n 结束。每个事件块中可能存在多行 data: 字段。
 *
 * @param eventText  一个完整 SSE 事件的原始文本（不含结尾的 \n\n）
 * @returns data 字段的值；如果没有 data 行返回 null
 */
export function parseSseEventData(eventText: string): string | null {
  const dataLines: string[] = [];
  for (const line of eventText.split('\n')) {
    if (line.startsWith('data:')) {
      // 提取 data: 后的值（我们的后端不在 data: 后添加分隔空格，因此无需去除）
      const value = line.substring(5);
      dataLines.push(value);
    }
    // 忽略 id:, event:, retry:, 注释(:) 等其他字段
  }
  if (dataLines.length === 0) return null;
  return dataLines.join('\n');
}
