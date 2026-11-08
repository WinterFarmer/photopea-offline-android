# Photopea Offline Wrapper (Android)

An ultra-lightweight (45KB) wrapper app built on Android System WebView to run **Photopea** offline.

## Key Features
- **Persistent Engine Lock:** Stores Photopea's core assets (`photopea.com` & `vecpea.com`) directly in the *User Data* directory (`getFilesDir()`), making them immune to system cache cleanups (*Clear Cache*).
- **Auto-Immersive Landscape:** Automatically enters *Fullscreen* mode (hiding the Status Bar, Navigation Bar, and display cutout/notch area) when in Landscape orientation.
- **Native File I/O:** Supports opening local files (`File > Open`) and saving/exporting directly (`Save as PSD` / `Export as PNG, JPG, etc.`) to the device's `Downloads` folder via `MediaStore`.
- **Adaptive Vector Icon:** Uses crisp XML vector drawables supporting both *Adaptive Icons* and *Monochrome Themed Icons*.
- **Ultra-Lightweight:** Configured with aggressive R8/ProGuard optimization.
