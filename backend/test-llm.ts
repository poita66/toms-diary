import { llmClient } from './dist/llm/client.js';

async function testLLM() {
  console.log('Testing LLM connection...\n');

  try {
    console.log('Test 1: Simple text generation');
    const response = await llmClient.chat('test-1', [
      {
        role: 'user',
        content: 'What is 2 + 2? Answer with just the number.',
      },
    ]);

    console.log('Response:', response.text.trim());
    console.log('Duration:', response.duration, 'ms\n');

    console.log('Test 2: Streaming');
    let fullText = '';
    for await (const event of llmClient.chatStream('test-2', [
      {
        role: 'user',
        content: 'Say hello briefly in one sentence.',
      },
    ])) {
      if (event.type === 'token' && event.data) {
        process.stdout.write(event.data);
        fullText += event.data;
      }
      if (event.type === 'complete') {
        console.log('\n\nStream complete!');
        break;
      }
      if (event.type === 'error') {
        console.error('Error:', event.error);
        break;
      }
    }

    console.log('\n✅ All tests passed!');
  } catch (error) {
    console.error('❌ Test failed:', error);
    process.exit(1);
  }
}

testLLM();
