# SS-Assistent

A personal Android assistant by S•S.

## Current stage

The app has an English dark/deep-purple UI, an external GGUF model manager, an assistant chat screen, and a real local inference layer based on the pinned `hokanosekai/llama.kt` Android binding for llama.cpp.

Large model files such as `.gguf` files are intentionally **not** stored in this repository. They are imported locally through the app's model manager.

## Native runtime requirements

The current llama.kt integration targets:

- Android API 29+
- `arm64-v8a`
- Android NDK `27.2.12479018`
- JDK 21
- Compile SDK 35

The llama.kt dependency is pinned as a Git submodule under `libs/llama.kt` so the native source revision is reproducible.

After cloning the repository, initialize the submodule before opening/building the project:

```bash
git submodule update --init --recursive
```

Then open the project in Android Studio and let Gradle sync.

## Model testing

The intended first real-device test model is `Qwen3-4B-Q4_K_M.gguf`.

Do **not** put the model into the APK or repository. Import it through the Models screen after the app builds successfully. The first runtime configuration intentionally uses a conservative 4096-token context and CPU inference so the target device can be validated before performance tuning.

## Planned capabilities

- Local assistant chat
- Strong long-term memory with user-controlled stored memories
- Configurable writing style and response style
- Conversation persistence
- Human-confirmed actions
- Opening supported Android apps such as Google Docs
- Supported text insertion workflows
- Later keyboard AI drafting and insertion without automatically sending
- Clear permission and action history
- Later integration with the other S•S AI apps

See [ROADMAP.md](ROADMAP.md) for the full development plan.
