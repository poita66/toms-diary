# Documentation

This directory contains documentation for the Tom's Diary project, including hardware references, research notes, and technical specifications.

## Project Documentation

### Hardware

- [Supernote Nomad Hardware Reference](supernote-nomad-hardware.md) - Complete specifications for the target device

### Components

- [Android App Documentation](../android-app/README.md) - Mobile application details
- [Backend Service Documentation](../backend/README.md) - Server-side implementation

## Research Areas

### Handwriting Recognition

**Status**: To be researched

**Key Questions**:
- How well does Qwen3.5 VLM handle handwritten text?
- What's the accuracy compared to dedicated OCR models?
- Can we use incremental/partial images?
- Is there image prefix caching in vLLM?

**Potential Approaches**:
- VLM-based (Qwen3.5, GPT-4V, etc.)
- Dedicated OCR (TrOCR, PaddleOCR, Tesseract)
- Hybrid approaches

### Handwriting Rendering

**Status**: To be researched

**Key Questions**:
- What models exist for text-to-handwriting conversion?
- Can rendering be streamed incrementally?
- Should we attempt to copy user's handwriting style?
- How to optimize for e-ink display constraints?

**Potential Approaches**:
- GAN-based generation
- Transformer sequence-to-stroke models
- Rule-based with handwriting fonts
- Hybrid pre-rendered stroke composition

## Development Notes

### System Architecture

```
┌──────────────┐         WebSocket          ┌──────────────┐
│   Android    │◄──────────────────────────►│   Backend    │
│     App      │  Images, Render Chunks      │   Service    │
└──────────────┘                             └──────┬───────┘
                                                     │
                                                     ▼
                                            ┌──────────────┐
                                            │      VLM      │
                                            │  (vLLM +      │
                                            │   Qwen3.5)    │
                                            └──────────────┘
```

### Data Flow

1. User writes on Supernote Nomad
2. Android app captures screen as image
3. Image sent to backend via WebSocket
4. Backend sends image to VLM for recognition
5. VLM returns text transcription
6. Backend generates AI response via LLM
7. Response streamed to handwriting renderer
8. Rendering instructions streamed back to app
9. App displays rendered handwriting on e-ink

## Contributing to Documentation

When adding new documentation:

1. **Research Findings**: Create new markdown files in this directory
2. **Component Docs**: Keep in respective component directories
3. **Links**: Update this README with new resources
4. **Versioning**: Note the date and context of research/decisions

## External Resources

- [Supernote Official Site](https://supernote.com)
- [Supernote Community](https://www.reddit.com/r/Supernote/)
- [vLLM Documentation](https://docs.lmformat.io/)
- [Qwen Models](https://qwenlm.github.io/)
- [Android Developers](https://developer.android.com)
