# Tom's Diary

This is a monorepo for Tom's Diary, a proof-of-concept written response journal, inspired by Tom Riddle's diary from Harry Potter and the Chamber of Secrets.

The basic idea is that the user can write notes to the AI agent, and it can read these, wipe the page, and "write" out its response.

## Hardware

The initial proof-of-concept will be done on a Supernote Nomad (Android 11, Wacom EMR).

## Software

- Android app
- VLM (vLLM + Qwen3.5 27b 4-bit AWQ will be used for testing) OR LLM+handwriting recognition model
- Backend service (to keep as much work off the Supernote as possible)

### Android app

Just needs to be able to take the written messages, wait a bit, clear the screen, and send the images off to the service

### Backend service

The backend will be written in TypeScript, run on Node 24, and communicate with the app via WebSocket.

It will take the images sent by the app, send the images off for recognition & response (maybe 2 steps if not VLM), streams the response to the Handwriting rendering model, and streams its output back to the app.

## Handwriting recognition

No idea how well Qwen3.5 will do with handwriting, should be one of the first things tested.

Should we try to send incremental images to the VLM? Is there prefix caching for images?

## Handwriting rendering

No idea - needs research. Ideally it'd render the words as the LLM spits them out, even better if animated like writing.

Should it try to copy the user's handwriting? Would that be odd?

