# Backend Performance Optimization Opportunities

## Summary

The backend is already well-optimized with good practices in place. Here are the identified optimization opportunities, ranked by impact:

---

## 🔴 HIGH PRIORITY

### 1. **Canvas Context Reuse in `handwriting.ts`**

**Issue:** Creating new canvas contexts for each word measurement is expensive.

**Location:** `src/renderer/handwriting.ts` - `renderWordStream()`

**Current:**
```typescript
for (const word of words) {
  const wordCtx = createCanvas(1, 1).getContext('2d');
  wordCtx.font = `${fontSize}px ${fontFamily}`;
  const textMetrics = wordCtx.measureText(word);
  // ...
}
```

**Optimization:**
```typescript
async *renderWordStream(
  text: string,
  options: RenderOptions = {}
): AsyncIterable<WordRenderResult> {
  // ... existing setup ...
  
  // Reuse a single measurement canvas
  const measureCanvas = createCanvas(1, 1);
  const measureCtx = measureCanvas.getContext('2d');
  measureCtx.font = `${fontSize}px ${fontFamily}`;
  
  for (const word of words) {
    const textMetrics = measureCtx.measureText(word);
    // ... rest of logic
  }
}
```

**Impact:** Eliminates ~1 canvas creation per word. For 50-word responses, that's 50 fewer canvas allocations.

---

### 2. **Image Scaling is Unnecessary**

**Issue:** `scaleDownImage()` function exists but is never used (`renderScale = 1.0`).

**Location:** `src/stream/coordinator.ts`

**Current:**
```typescript
const renderScale = 1.0;
// ...
if (base64 && renderScale < 1) {
  base64 = await scaleDownImage(base64, renderScale);
}
```

**Optimization:** Remove the `scaleDownImage()` function entirely and the conditional check. This is dead code that adds ~50 lines and unnecessary complexity.

**Impact:** Cleaner code, no runtime overhead.

---

### 3. **WebSocket ReadyState Checks are Redundant**

**Issue:** Checking `ws.readyState !== WebSocket.OPEN` in every callback is good, but we also check `cancelToken` first. The order should be optimized.

**Location:** `src/server/websocket.ts`

**Current:**
```typescript
if (activeSession?.cancelToken || ws.readyState !== WebSocket.OPEN) return;
```

**Optimization:** Reverse the order - check readyState first (faster native check):
```typescript
if (ws.readyState !== WebSocket.OPEN || activeSession?.cancelToken) return;
```

**Impact:** Minimal, but readyState check is a native property access vs. object property traversal.

---

### 4. **JSON Stringification in Hot Path**

**Issue:** `JSON.stringify()` is called multiple times per token/render chunk.

**Location:** `src/server/websocket.ts` - `handleImageMessage()`

**Optimization:** Consider using a faster JSON serializer like `fast-json-stable-stringify` or `quickjs` for the hot path.

```typescript
// npm install fast-json-stable-stringify
import stringify from 'fast-json-stable-stringify';

// Replace:
ws.send(JSON.stringify(tokenMessage));

// With:
ws.send(stringify(tokenMessage));
```

**Impact:** ~20-30% faster serialization for simple objects.

---

## 🟡 MEDIUM PRIORITY

### 5. **Session Cleanup Should Be Periodic**

**Issue:** `cleanupInactiveSessions()` exists but is never called automatically.

**Location:** `src/session/manager.ts`

**Optimization:** Add automatic cleanup interval in `websocket.ts`:

```typescript
// In startWebSocketServer()
const CLEANUP_INTERVAL = 10 * 60 * 1000; // 10 minutes
setInterval(() => {
  sessionManager.cleanupInactiveSessions(30 * 60 * 1000);
}, CLEANUP_INTERVAL);
```

**Impact:** Prevents memory leak from abandoned sessions.

---

### 6. **Image Cache is Unused**

**Issue:** `src/image/cache.ts` exists but is never used.

**Optimization:** Either:
- **Remove it** (if not needed)
- **Use it** to cache processed images if the same image is sent multiple times

**Impact:** If removed, ~100 lines of dead code eliminated.

---

### 7. **Pre-allocate Canvas in `handwriting.ts`**

**Issue:** `renderText()` creates a new canvas for every full render.

**Location:** `src/renderer/handwriting.ts`

**Optimization:** For `renderTextStream()`, reuse the same canvas and clear it:

```typescript
async *renderTextStream(...) {
  // ...
  const canvas = createCanvas(maxWidth, 200); // Reuse
  const ctx = canvas.getContext('2d');
  
  for (const word of words) {
    ctx.clearRect(0, 0, canvas.width, canvas.height);
    // ... render to existing canvas
  }
}
```

**Impact:** Reduces GC pressure during streaming.

---

### 8. **String Concatenation in Stream**

**Issue:** Using `+=` for string concatenation in hot path.

**Location:** Multiple files

**Current:**
```typescript
fullText += event.data;  // coordinator.ts
accumulatedText += token; // websocket.ts
currentText += (chunkIndex > 0 ? ' ' : '') + word; // handwriting.ts
```

**Optimization:** Use array + join for better performance:

```typescript
const tokens: string[] = [];
// ...
tokens.push(event.data);
// ...
const fullText = tokens.join('');
```

**Impact:** ~2-5x faster for many concatenations.

---

## 🟢 LOW PRIORITY

### 9. **Logger Timer Overhead**

**Issue:** `logger.startTimer()` is called in almost every function. While useful for debugging, it adds overhead.

**Optimization:** Make timers conditional on log level:

```typescript
startTimer(name: string, sessionId?: string): Timer {
  if (this.level < LogLevel.DEBUG) {
    return new NoOpTimer();
  }
  // ... existing logic
}
```

**Impact:** Negligible in production, but measurable in high-throughput scenarios.

---

### 10. **Regex Compilation**

**Issue:** Regex patterns are recompiled on every use.

**Location:** `src/stream/coordinator.ts`, `src/renderer/handwriting.ts`

**Current:**
```typescript
const transMatch = fullText.match(/\[TRANSCRIPTION\]([\s\S]+)\[\/TRANSCRIPTION\]/);
const words = responseText.split(/\s+/).filter((w: string) => w.length > 0);
```

**Optimization:** Pre-compile as constants:

```typescript
// coordinator.ts
const TRANSCRIPTION_REGEX = /\[TRANSCRIPTION\]([\s\S]+)\[\/TRANSCRIPTION\]/;
const WORD_SPLIT_REGEX = /\s+/;

// Usage:
const transMatch = fullText.match(TRANSCRIPTION_REGEX);
const words = responseText.split(WORD_SPLIT_REGEX).filter(w => w.length > 0);
```

**Impact:** Minimal, but good practice.

---

### 11. **Zod Validation Schema Caching**

**Issue:** Validation schemas in `src/utils/validation.ts` should be checked for caching.

**Optimization:** Ensure schemas are created once at module load, not per-request.

**Impact:** Prevents schema recompilation overhead.

---

### 12. **Reduce Object Creation in Loops**

**Issue:** Creating objects inside loops in `orchestrator.ts`:

```typescript
for (const turn of history) {
  messages.push({
    role: 'user',
    content: userText,
  });
  messages.push({
    role: 'assistant',
    content: turn.assistant,
  });
}
```

**Optimization:** Use array mapping:

```typescript
const historyMessages = history.flatMap(turn => [
  { role: 'user' as const, content: userText },
  { role: 'assistant' as const, content: turn.assistant },
]);
messages.push(...historyMessages);
```

**Impact:** Cleaner code, potentially better V8 optimization.

---

## 📊 Expected Impact Summary

| Optimization | Effort | Impact | Priority |
|--------------|--------|--------|----------|
| Canvas context reuse | Low | Medium | High |
| Remove scaleDownImage | Low | Low | High |
| JSON serialization | Medium | Medium | High |
| Session cleanup | Low | Medium | Medium |
| String concatenation | Low | Low | Medium |
| Regex pre-compilation | Low | Low | Low |

---

## ✅ Already Well Optimized

1. **LLM streaming** - Already using async generators
2. **vLLM optimization** - Using `enable_thinking: false` for fast inference
3. **Image detail** - Using `detail: 'low'` to reduce token usage
4. **History as text** - Only sending current image, history as text only
5. **Cancellation support** - Proper cancel token implementation
6. **Timeout handling** - 120s timeout with proper cleanup
7. **Session tracking** - Good session management with cleanup capability

---

## 🎯 Recommended Action Plan

1. **Immediate (5 min):** Remove `scaleDownImage()` dead code
2. **Quick Win (15 min):** Pre-compile regex patterns
3. **High Impact (30 min):** Canvas context reuse in `renderWordStream()`
4. **Medium Term (1 hour):** Add periodic session cleanup
5. **Nice to Have (1 hour):** Fast JSON serialization
