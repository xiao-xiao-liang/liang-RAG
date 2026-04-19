/**
 * SSE 流诊断脚本
 * 直连后端（绕过 Next.js 代理），抓取原始 SSE 字节流，
 * 确认换行符在哪一层丢失。
 *
 * 用法: node test-sse-debug.mjs
 */

const BACKEND_URL = 'http://127.0.0.1:8009/api/chat/send';

async function main() {
  console.log('=== SSE 流诊断工具 ===');
  console.log(`直连后端: ${BACKEND_URL}\n`);

  const response = await fetch(BACKEND_URL, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      userId: '25',
      content: '用表格对比一下ArrayList和LinkedList，不超过5行',
    }),
  });

  if (!response.ok) {
    console.error(`请求失败: ${response.status} ${response.statusText}`);
    process.exit(1);
  }

  const reader = response.body.getReader();
  const decoder = new TextDecoder();

  let rawTotal = '';    // 全部原始字节
  let sseBuffer = '';   // SSE 解析缓冲区
  let eventCount = 0;
  let contentAccumulator = ''; // 模拟前端消息拼接

  console.log('--- 开始接收原始 SSE 数据 ---\n');

  while (true) {
    const { done, value } = await reader.read();
    if (done) break;

    const rawChunk = decoder.decode(value, { stream: true });
    rawTotal += rawChunk;

    // 打印原始字节的可视化表示（将 \n 替换为 ⏎ 方便观察）
    const visible = rawChunk.replace(/\n/g, '⏎\n');
    process.stdout.write(`[RAW CHUNK] ${visible}`);

    sseBuffer += rawChunk;

    // 解析 SSE 事件
    let eventEnd;
    while ((eventEnd = sseBuffer.indexOf('\n\n')) !== -1) {
      const eventText = sseBuffer.substring(0, eventEnd);
      sseBuffer = sseBuffer.substring(eventEnd + 2);

      eventCount++;

      // 提取 data: 行
      const dataLines = [];
      for (const line of eventText.split('\n')) {
        if (line.startsWith('data:')) {
          let val = line.substring(5);
          if (val.startsWith(' ')) val = val.substring(1);
          dataLines.push(val);
        }
      }

      if (dataLines.length === 0) continue;

      const data = dataLines.join('\n');

      // 判断事件类型
      if (data.startsWith('[START]') || data.startsWith('[DONE]')) {
        console.log(`\n  [EVENT #${eventCount}] 控制事件: "${data}"`);
        continue;
      }

      // 打印每个 data 事件的详细信息
      const hasNewline = data.includes('\n');
      const escData = data.replace(/\n/g, '\\n');
      console.log(`  [EVENT #${eventCount}] dataLines=${dataLines.length} hasNewline=${hasNewline} data="${escData}"`);

      contentAccumulator += data;
    }
  }

  console.log('\n\n--- 诊断结果 ---');
  console.log(`总事件数: ${eventCount}`);
  console.log(`原始字节总长度: ${rawTotal.length}`);
  console.log(`\n--- 拼接后的完整内容 (前 2000 字) ---`);
  console.log(contentAccumulator.substring(0, 2000));
  console.log(`\n--- 换行符统计 ---`);
  console.log(`内容总长度: ${contentAccumulator.length}`);
  console.log(`换行符数量: ${(contentAccumulator.match(/\n/g) || []).length}`);

  // 检查关键 Markdown 结构
  const hasTable = contentAccumulator.includes('|---');
  const hasHeading = /^#{1,6}\s/m.test(contentAccumulator);
  console.log(`包含表格分隔符(|---): ${hasTable}`);
  console.log(`包含标题(# ): ${hasHeading}`);
}

main().catch(console.error);
