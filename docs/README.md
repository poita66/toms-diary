# Documentation

This directory contains documentation for the Tom's Diary project, including hardware references, research notes, and technical specifications.

## Project Documentation

### Hardware

- [Supernote Nomad Hardware Reference](supernote-nomad-hardware.md) - Complete specifications for the target device

### Components

- [Android App Documentation](../android-app/README.md) - Mobile application details

## Research Areas

### Handwriting Recognition

**Status**: Implemented — uses Caveat font with word-by-word streaming rendering

### Handwriting Rendering

**Status**: Implemented — uses Caveat font with word-by-word streaming rendering

## Development Notes

### System Architecture
### Data Flow

1. User writes on Supernote Nomad
2. App captures and crops handwriting to bounds
3. Image sent to LLM via OpenAI API
4. LLM reads handwriting
5. LLM returns streaming tokens
6. App renders tokens as handwriting locally
7. Words displayed on canvas with natural spacing

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
