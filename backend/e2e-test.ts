import dotenv from 'dotenv';
dotenv.config();

import { llmOrchestrator } from './dist/llm/orchestrator.js';
import { handwritingRenderer } from './dist/renderer/handwriting.js';
import { writeFileSync } from 'fs';

async function endToEndTest() {
  console.log('🚀 Starting End-to-End Test\n');
  console.log('Pipeline: Image → LLM → Handwriting Rendering\n');

  // Simulate a handwritten note (we'll use a simple text prompt instead of actual image)
  console.log('Step 1: Sending to LLM for response...');
  
  // Note: In real usage, this would be an actual handwritten image
  // For this test, we'll directly use the LLM client with text
  const { llmClient } = await import('./dist/llm/client.js');
  
  const messages = [
    {
      role: 'system' as const,
      content: 'You are a helpful AI diary companion. Respond warmly and conversationally to the user\'s note.',
    },
    {
      role: 'user' as const,
      content: 'Dear Diary, today was a wonderful day. I learned something new about programming and felt inspired to keep building.',
    },
  ];

  console.log('📝 User input: "Dear Diary, today was a wonderful day..."');
  console.log('⏳ Waiting for LLM response...\n');

  let fullResponse = '';
  let tokenCount = 0;

  console.log('📡 Streaming response:');
  process.stdout.write('  ');
  
  for await (const event of llmClient.chatStream('e2e-test', messages)) {
    if (event.type === 'token' && event.data) {
      process.stdout.write(event.data);
      fullResponse += event.data;
      tokenCount++;
    }
    if (event.type === 'complete') {
      console.log('\n');
      break;
    }
    if (event.type === 'error') {
      console.error('  ❌ Error:', event.error);
      return;
    }
  }

  console.log(`\n✅ Received ${tokenCount} tokens\n`);
  console.log('Step 2: Rendering response as handwriting...');

  const renderResult = handwritingRenderer.renderText(fullResponse, {
    fontSize: 48,
    padding: 40,
    maxWidth: 1000,
    addVariation: true,
  });

  const buffer = Buffer.from(renderResult.base64, 'base64');
  writeFileSync('e2e-response.png', buffer);

  console.log(`  ✓ Rendered: ${renderResult.width}x${renderResult.height}px`);
  console.log(`  ✓ Saved: e2e-response.png\n`);

  console.log('🎉 End-to-End Test Complete!');
  console.log('─'.repeat(50));
  console.log('User wrote:');
  console.log('  "Dear Diary, today was a wonderful day..."');
  console.log('\nAI responded (as handwriting):');
  console.log(`  "${fullResponse.substring(0, 100)}${fullResponse.length > 100 ? '...' : ''}"`);
  console.log('\nOutput image: ./e2e-response.png');
  console.log('─'.repeat(50));
}

endToEndTest().catch(console.error);
