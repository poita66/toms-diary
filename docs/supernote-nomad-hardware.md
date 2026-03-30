# Supernote Nomad Hardware Reference

This document contains detailed specifications and technical information about the Supernote Nomad device, which serves as the target hardware for the Tom's Diary Android app.

## Device Overview

The Supernote Nomad (A6 X2) is an e-ink tablet designed for digital note-taking and reading. It runs Android 11 and features Wacom EMR (Electromagnetic Resonance) stylus technology.

## Display Specifications

- **Screen Size**: 7.8 inch
- **Type**: Glass E Ink display
- **Resolution**: 1404 × 1872 pixels
- **Pixel Density**: 300 PPI
- **Surface**: FeelWrite 2 self-recovery soft film (paper-like texture)
- **Features**:
  - No frontlight/backlight (design choice for optimal handwriting feel)
  - Black and white only
  - Self-healing surface that prevents scratching from ceramic nibs
  - Anti-glare properties
  - Rougher surface texture for natural writing feel

## Stylus/Pen Specifications

- **Technology**: Wacom One EMR (Electromagnetic Resonance)
- **Pressure Sensitivity**: 4096 levels
- **Nib Type**: Ceramic NeverReplace nib
- **Nib Size**: 0.7 mm diameter (super-fine)
- **Power**: No battery required (passive EMR)
- **Maintenance**: Replace-free, recharge-free
- **Compatibility**: Works with Wacom One EMR compatible pens

## System & OS

- **Operating System**: Android 11
- **Development**: Standard Android SDK/NDK compatible
- **Customization**: Future Linux-based system planned for community customization
- **Sideloading**: Supported (apps can be sideloaded via Android platform tools)

## Storage & Memory

- **Internal Storage**: 32 GB
- **Expandable Storage**: microSD card slot (up to 2 TB)
- **User Replaceable**: Both battery and microSD card are user-replaceable

## Connectivity

- **WiFi**: Supported (for cloud sync, updates, local file transfer)
- **Bluetooth**: Supported
- **USB**: USB-C port
- **OTG**: USB OTG thumb drive support
- **Local WiFi Transfer**: File transfer via local WiFi network

## Battery & Power

- **Battery Life**: Extended (no frontlight contributes to longevity)
- **User Replaceable**: Yes
- **Power Management**: Battery life significantly improved with WiFi/Bluetooth off

## Physical Design

- **Weight**: ~375g (0.83 lb) with pen loop
- **Form Factor**: A6-sized (approximately)
- **Orientation**: Supports both portrait and landscape
- **Handedness**: Dual sidebars support both left and right-handed users
- **Editions**: Crystal (transparent back) and White
- **Repairability**: Modular design for easy repair and upgrades

## Supported File Formats

- **Native**: .note (Supernote's native format)
- **Documents**: PDF, EPUB, Word, Text (.txt)
- **Images**: PNG, JPG, WebP
- **Comics**: CBZ
- **E-books**: FB2, XPS, MOBI (via Kindle app)

## Key Features for App Development

### Handwriting Input

- Wacom EMR provides low-latency, high-precision input
- 4096 pressure levels enable natural writing experience
- Tilt support available on compatible pens
- Palm rejection built into EMR technology

### Screen Capture

- E-ink display is monochrome (grayscale)
- High resolution (1404×1872) suitable for handwriting recognition
- Static display (no refresh artifacts like LCD/OLED)

### Limitations to Consider

- **No microphone**: Cannot capture audio input
- **No speakers**: Cannot play audio output
- **No fingerprint reader**: Limited biometric options
- **No frontlight/backlight**: Limited low-light visibility
- **E-ink refresh rate**: Slow refresh (not suitable for animation/video)
- **No color**: Display is monochrome only

## Development Considerations

### Android Development

- Standard Android 11 APIs available
- Android Ink API can be used for handwriting (though latency may be higher than native EMR)
- Sideloading enabled for testing and distribution
- May need to work with Supernote's native EMR input system for optimal latency

### Performance Considerations

- E-ink display refresh is slow (~3-4 seconds for full refresh typically)
- App should minimize full-screen refreshes
- Consider partial updates where possible
- Battery optimization important (WiFi/Bluetooth consumption is significant)

### Network Connectivity

- WiFi and Bluetooth can be toggled for battery conservation
- Local WiFi file transfer available for offline scenarios
- Cloud sync supported via Supernote Cloud

## E-Ink Display Behavior

### Refresh Characteristics

- **Full Refresh**: ~3-4 seconds
- **Partial Refresh**: Faster but may leave ghosting
- **Ghosting Prevention**: Periodic full refreshes required
- **Update Frequency**: Limit updates to prevent user frustration

### Optimization Strategies

1. **Partial Updates**: Use for incremental content changes
2. **Region Refreshing**: Only refresh changed areas when possible
3. **Debounce Updates**: Batch rapid changes together
4. **User Feedback**: Show loading indicators during refreshes

## Wacom EMR Integration

### Technical Details

- **Frequency**: 4096 pressure levels
- **Tracking**: Pen position tracked at high frequency
- **Latency**: Very low (typically <10ms)
- **Power**: Pen is passive (no battery)
- **Compatibility**: Wacom One EMR standard

### Integration Options

1. **Android Ink API**: Standard approach, may have higher latency
2. **Supernote Native**: Direct EMR access, optimal performance
3. **Custom Input Handler**: Balance between standard and native

## References

- [Supernote Nomad Official Page](https://supernote.com/pages/supernote-nomad)
- [Supernote Support](https://support.supernote.com)
- [Software Roadmap](https://trello.com/b/l0COP24j/supernote-a5-x-a6-x-manta-nomad-software-roadmap-2025)
- [Reddit Community](https://www.reddit.com/r/Supernote/)
