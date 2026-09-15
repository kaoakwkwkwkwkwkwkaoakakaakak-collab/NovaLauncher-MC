# NovaLauncher

A Minecraft: Java Edition launcher for Android.

NovaLauncher is a fork of [MojoLauncher](https://github.com/MojoLauncher/MojoLauncher),
which is itself based on PojavLauncher. It keeps the proven native launch pipeline and
adds device-aware performance tooling on top of it.

## Features

### FPS Boosters
A dedicated settings screen with a master switch and nine independent toggles:

| Toggle | Effect |
| --- | --- |
| Parallel chunk building | Spreads world loading across more cores |
| Low pause garbage collection | Serial GC with larger allocation buffers |
| Fast JIT warmup | Compiles hot code sooner |
| Aggressive texture streaming | Shrinks and mipmaps textures to save GPU memory |
| Unlock frame rate | Disables V-Sync |
| Pin to performance cores | Keeps the game on the big cores |
| Pre-allocate heap | Reserves memory up front to avoid hitches |
| Reduce sound channels | Fewer simultaneous sounds |
| Disable launcher animations | Snappier interface |

Toggles map to real JVM arguments and renderer environment variables at launch time.

### Ai Optimizer
Found in Settings, Advanced. Sends a description of the device (SoC, cores, RAM,
Android version, GPU) and the installed mods to an OpenRouter model, then proposes a
renderer, a RAM allocation and a set of FPS Booster toggles. Nothing is changed until
the suggestion is reviewed and applied.

### Ai Error Fixer
Every error dialog gains a "Fix with Ai" action that sends the message and stack trace,
together with the current renderer and RAM settings, and returns a plain language
explanation with numbered repair steps.

### Mod management
Modrinth search and one tap install, filtered automatically by the selected instance's
Minecraft version and mod loader. Mods install into the instance `mods` directory and
can be enabled or disabled in place.

### Renderers
Five renderers ship inside the APK: holy-gl4es, Zink (Mesa), Freedreno/Turnip,
LTW and MobileGlues. Each is offered only when the device supports it.

| Renderer | Best for |
| --- | --- |
| holy-gl4es | Older versions, widest compatibility |
| MobileGlues | 1.17+ on GLES 3.2 devices, Sodium |
| LTW | Modern versions on Adreno |
| Zink | Vulkan translation, broad GL support |
| Freedreno | Adreno GPUs via Turnip |

### Java runtimes
The full APK bundles Java 8, 17 and 21, so every Minecraft version runs offline.
The noruntime APK downloads the runtime it needs on first launch.

### Shizuku support (optional)
Removes the Android 12+ phantom process limit that kills the game mid-session,
boosts the game process priority, reads the full system logcat for crash reports
and grants storage/notification/battery permissions in one tap. Works with
Shizuku or Sui, and the launcher runs normally without it.

### Appearance
Accent colours, Android 12 dynamic colour, UI scaling, a custom background with
adjustable opacity, and app-wide screen transitions (slide, fade, zoom, bounce).

### Launcher tools
Upload the latest log to mclo.gs and get a shareable link, export and import a
backup of instances, controls and settings, and scan the Wi-Fi network for
Minecraft worlds opened to LAN.

## AI configuration

Both AI features use [OpenRouter](https://openrouter.ai). Provide a key in one of two
ways:

1. Settings, Advanced, OpenRouter API key. Stored on device only.
2. `NovaAI.OPENROUTER_API_KEY` in
   `app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/nova/NovaAI.java`.

A key compiled into an APK can be extracted from it. Prefer the in app field, and use a
revocable key.

Model: `nvidia/nemotron-3-ultra-550b-a55b:free`, with reasoning enabled. Multi turn
exchanges pass `reasoning_details` back unmodified so the model continues from where it
left off.

## Building

Requires JDK 17, Android SDK with NDK 29.0.14206865, and CMake 3.31 or newer. SDL3 does
not configure under the CMake 3.22 bundled with the SDK.

```
git clone --recurse-submodules <this repo>
cd NovaLauncher-MC
./gradlew :app_pojavlauncher:assembleNoruntimeDebug
```

Flavours: `full` bundles a Java runtime, `noruntime` does not.

## Licence

GNU LGPL v3, inherited from MojoLauncher and PojavLauncher. See `LICENSE`.

Upstream credit: MojoLauncher by artdeell, PojavLauncher by the PojavLauncher team.
