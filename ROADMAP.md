# SS Assistent Roadmap

SS Assistent is being built as a real Android device assistant. The local language model is one component of the system, not the system itself.

## Phase 1 — Local model foundation

- [x] Android app shell with English UI
- [x] Model manager for external `.gguf` files
- [x] Active-model selection
- [x] Assistant chat UI
- [x] Runtime abstraction
- [x] Pin the `hokanosekai/llama.kt` llama.cpp Android runtime
- [x] Connect the assistant to real local GGUF inference
- [x] Add model metadata and available-memory preflight checks
- [x] Keep the local context bounded instead of using the model's full advertised context by default
- [x] Add streamed token output and generation cancellation
- [ ] Build and run on a real arm64 Android device
- [ ] Validate `Qwen3-4B-Q4_K_M.gguf` on the target phone
- [ ] Add clearer model diagnostics in the Models screen
- [ ] Add robust runtime error recovery and model unload/reload handling

## Phase 2 — Assistant personality and conversation quality

- [x] Add a dedicated assistant system prompt
- [x] Add a configurable writing style / response style system
- [x] Add concise, normal, detailed and custom style controls
- [x] Preserve the user's chosen style between conversations
- [x] Add conversation persistence
- [x] Add a clear-conversation action
- [ ] Add conversation rename and export
- [ ] Add generation settings UI

## Phase 3 — Real memory

Memory must be structured and useful rather than simply dumping the entire chat history into every prompt.

- [x] Local long-term memory store foundation
- [x] Explicit memories the user can review and delete
- [x] Memory categories such as preferences, projects, people, routines and facts
- [x] Memory controls and a clear delete action
- [ ] Add memory editing
- [ ] Add memory relevance scoring / retrieval
- [ ] Avoid storing sensitive information unless the user explicitly chooses to remember it
- [ ] Add a clear "Forget" workflow for individual memories and categories
- [ ] Add local encrypted storage where appropriate
- [ ] Add short-term context management
- [ ] Add memory-aware prompt assembly with a strict context budget

## Phase 4 — Device actions

The model should propose actions; Android code should execute them through explicit, visible mechanisms.

- [ ] Action planner separate from the language model
- [ ] Permission manager with per-capability controls
- [ ] Activity / action history
- [ ] Open installed apps through Android intents where supported
- [ ] Open Google Docs and other supported document apps
- [ ] Add supported text insertion workflows
- [ ] Accessibility integration only where necessary and clearly explained to the user
- [ ] Never silently send messages, publish content, delete user data or make purchases
- [ ] Require explicit confirmation for consequential actions

## Phase 5 — Keyboard assistant

- [ ] Build or integrate a supported Android IME workflow
- [ ] Add an AI action button for drafting replies
- [ ] Insert drafted text into the current text field without sending it
- [ ] Provide `Send`, `Edit` and `Delete` controls after insertion where the host app allows it
- [ ] Keep sending as a separate user-confirmed action

## Phase 6 — S•S AI ecosystem

SS Assistent, the writing AI and the image AI remain separate applications while they are developed. Later they can share a deliberate S•S integration layer.

- [ ] Shared account / identity model if needed
- [ ] Controlled hand-off between the three S•S AI apps
- [ ] Shared settings where useful
- [ ] Keep local-only assistant functionality independent from cloud services
