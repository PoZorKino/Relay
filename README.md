# Relay

An Android chat app for your own model endpoints **and** your Claude / ChatGPT subscriptions.
Written in Kotlin with Jetpack Compose.

- **Bring your own endpoint:** any OpenAI-compatible server (llama.cpp, Ollama, LM Studio, vLLM, proxies), the Anthropic API or the Gemini API, with an API key or none. Replies stream in; "Test connection" lists the server's models.
- **Use a subscription:** sign in with a **Claude** plan (through Claude Code) or a **ChatGPT** plan (through Codex). Relay runs the vendors' official CLIs on the phone, in a private Linux sandbox. No Termux, no root.
- **Tools** (subscription chats): web search, reading web pages, running Python and shell commands, reading and writing files. Every call shows as an expandable card in the reply.
- **Chat history** in a side panel (swipe right), Markdown (code blocks, lists, links), attachments (text files).
- **4 themes:** Classic, Midnight, Terminal, Lilac.
- **Languages:** English, Русский, Українська (follows the system, or pick one in Connections).

## Install

Download the APK from [Releases](../../releases) and open it on the phone. Android will ask you to allow installing from that source, and Play Protect may warn that the app was built for an older version of Android. That's expected (see below).

Subscriptions need an **ARM64** phone, which covers essentially every modern Android phone. API connections work on any device with Android 8.0+.

## How subscriptions work

Tapping **Sign in** under *Subscriptions* runs three steps:

1. **Linux sandbox:** Relay unpacks [proot](https://github.com/termux/proot) (bundled) and downloads an Alpine Linux minirootfs into its own private storage. proot fakes the filesystem and root in user space, so no root access is needed.
2. **Official CLI:** Relay installs Claude Code with Anthropic's installer (`claude.ai/install.sh`), or Codex from its [GitHub release](https://github.com/openai/codex/releases).
3. **Sign-in:** Relay runs the CLI's own login (`claude auth login` / `codex login --device-auth`) and opens your browser. The credentials stay in the CLI's config inside the sandbox; Relay never reads them.

Chats then run `claude -p --output-format stream-json …` or `codex exec --json …`, and follow-up messages resume the same CLI session. The first setup downloads roughly 100–200 MB.

> Relay only drives the official CLIs through their documented headless modes, on your own device. Whether a vendor considers this fine for your account is up to their terms; check them.

### Why `targetSdk 28` / sideload-only

Since Android 10, apps targeting API 29+ may not execute files they wrote themselves (W^X). The Linux sandbox has to, so Relay targets API 28, just as Termux does. The consequence is that Relay can't be published on Google Play.

On x86_64 devices (emulators, Chromebooks), Android's system-call filter for apps blocks the legacy `fork` call that musl uses there, so the sandbox can't start processes. Subscriptions are therefore ARM64-only.

## Build

Requirements: JDK 17+ and the Android SDK (platform 35, build-tools 35.0.0).

```sh
./gradlew assembleDebug        # app/build/outputs/apk/debug/app-debug.apk
./gradlew assembleRelease      # signed with the debug key so it installs as-is
```

Create `local.properties` with `sdk.dir=/path/to/Android/Sdk` if `ANDROID_HOME` isn't set.

## Project layout

```
app/src/main/java/app/relay/chat/
  MainActivity.kt        navigation, drawer, theme and language wiring
  AppViewModel.kt        chats, streaming (paced + fade-in), plans, tools
  Lang.kt                language override, error translation
  data/                  connections (keys encrypted with Android Keystore), chat history
  net/LlmClient.kt       OpenAI / Anthropic / Gemini streaming clients
  runtime/               proot + Alpine sandbox, Claude Code / Codex driver, foreground service
  ui/                    screens (Chat, Connections, Add connection, Plan setup, Model picker, drawer)
app/src/main/assets/runtime/   proot binaries (see SOURCES.txt)
```

## Third-party components

- **proot** (GPL-2.0), **libtalloc** (LGPL-3.0) and **libandroid-shmem** (BSD-3), prebuilt for Android by the Termux project and bundled unmodified. Sources and versions are listed in [`app/src/main/assets/runtime/SOURCES.txt`](app/src/main/assets/runtime/SOURCES.txt).
- Fonts (SIL Open Font License): DM Sans, Bricolage Grotesque, IBM Plex Mono, Figtree, Fraunces, JetBrains Mono.
- Downloaded at runtime, not bundled: Alpine Linux, Claude Code, Codex.
