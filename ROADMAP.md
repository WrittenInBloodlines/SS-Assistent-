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
- [x] Make assistant lifecycle cleanup cancellation-safe
- [x] Add clearer GGUF model diagnostics in the Models screen
- [ ] Build and run on a real arm64 Android device
- [ ] Validate `Qwen3-4B-Q4_K_M.gguf` on the target phone
- [x] Add runtime error recovery and safe model unload/reload handling

## Phase 2 — Assistant personality and conversation quality

- [x] Add a dedicated assistant system prompt
- [x] Add a configurable writing style / response style system
- [x] Add concise, normal, detailed and custom style controls
- [x] Preserve the user's chosen style between conversations
- [x] Persist a user-defined custom style instruction
- [x] Add conversation persistence
- [x] Add a clear-conversation action
- [x] Bound short-term conversation context before local generation
- [x] Add generation controls for maximum response length, temperature, Top-K and Top-P
- [ ] Add conversation rename and export
- [ ] Add a more advanced per-conversation generation profile

## Phase 3 — Real memory

Memory must be structured and useful rather than simply dumping the entire chat history into every prompt.

- [x] Local long-term memory store foundation
- [x] Explicit memories the user can review and delete
- [x] Memory categories such as preferences, projects, people, routines and facts
- [x] Memory controls and a clear delete action
- [x] Add deterministic relevance scoring / retrieval for saved memories
- [x] Add memory-aware prompt assembly with a strict local character budget
- [x] Add memory editing
- [x] Add memory search/filtering
- [x] Add duplicate protection for saved memories
- [x] Add a clear "Forget all" workflow
- [x] Add explicit "Remember" capture from conversation messages
- [x] Limit saved memory text length and normalize whitespace for normal memories
- [x] Add category-aware memory counting and clearing primitives
- [x] Add sealed/exact memory storage primitives that preserve explicitly supplied text without trimming, whitespace normalization or shortening
- [x] Mark sealed memories clearly in the Memory UI and require an explicit replacement to change their contents
- [x] Add category-specific Forget controls
- [ ] Detect explicit natural-language intent such as "save this exactly as written" / "store this exactly" and route it to sealed memory automatically
- [ ] When exact/sealed memory is requested, show a confirmation preview and clearly state that the text will be stored exactly as supplied
- [ ] Guarantee that sealed memory retrieval does not paraphrase, summarize, shorten or silently rewrite the stored value
- [ ] If a user explicitly requests a change to a sealed memory, apply only the requested change and preserve every other character/content exactly
- [ ] Add a dedicated memory version/history trail so exact replacements can be reviewed safely
- [ ] Avoid storing sensitive information unless the user explicitly chooses to remember it
- [ ] Add local encrypted storage where appropriate
- [ ] Add stronger semantic memory retrieval when the local architecture supports it

## Phase 3B — Story, lore and continuity intelligence

This is designed primarily for story/character work. The assistant should not treat every surprising detail as an error; it should compare new text against the established canon, timeline, character facts and intentional hidden-information rules before raising anything.

### Plot-hole detection

- [ ] Detect likely continuity gaps in a story, timeline, scene transition or character location
- [ ] Show a clear non-destructive notice such as **"Plot hole detected"** instead of silently rewriting the story
- [ ] Explain the exact conflicting or missing information in a compact review window
- [ ] Offer three practical resolution choices where possible, such as `Ignore`, `Edit` and a suggested fix
- [ ] Suggested fixes must be optional and must never be inserted automatically
- [ ] Example: if a character is described as being in a car, then later walks into the kitchen without the story establishing that they returned home, flag the missing transition and suggest ways to resolve it
- [ ] Keep plot-hole detection separate from the actual story text so the user remains in control

### Secret / hidden-information system

- [ ] Add a story-level **Secrets** system for information that is known to the author/assistant but intentionally unknown to one or more characters
- [ ] Let a secret specify who knows it, who does not know it, when it becomes discoverable and whether it may be revealed in narration
- [ ] Prevent the assistant from accidentally explaining a hidden cause just because the assistant itself knows the secret
- [ ] Preserve uncertainty when a character experiences an unexplained event
- [ ] Example: if the canon says the creator is secretly observing Alex and Ciro, but Ciro only feels strangely watched and cannot know why, the assistant should keep the scene at "Ciro feels strangely watched but cannot explain why" rather than exposing the observer
- [ ] Add a review warning when a generated sentence would reveal a secret to a character who is not supposed to know it
- [ ] Offer `Keep hidden`, `Reveal`, `Edit` and `Ignore` controls when a possible secret leak is detected
- [ ] Never reveal a secret merely because it exists in the assistant's internal story data

### Lore contradiction detection

- [ ] Detect when new user-provided story information conflicts with established lore
- [ ] Show a clear review window such as **"Lore conflict detected"** with `Ignore`, `Change` and a suggested resolution
- [ ] Show the established canon value and the newly supplied value side by side
- [ ] Never silently overwrite established canon because the latest message contains a different fact
- [ ] Example: if canon says Ciro has dark brown eyes and a new draft says he has blue eyes, flag the contradiction before changing anything
- [ ] `Ignore` keeps the established canon and does not modify the new draft automatically
- [ ] `Change` lets the user explicitly update the canon or the current draft, depending on what they choose
- [ ] Suggested resolutions remain suggestions and require user confirmation
- [ ] Track intentional canon changes so an old fact is not repeatedly reported as a contradiction after the user deliberately changed it

## Phase 4 — Device actions and app integrations

The model should propose actions; Android code should execute them through explicit, visible mechanisms. App integrations must respect Android permissions, each app's APIs, and the user's control.

- [ ] Action planner separate from the language model
- [ ] Permission manager with per-capability controls
- [ ] Activity / action history
- [ ] Open installed apps through Android intents where supported
- [ ] Open Google Docs and other supported document apps
- [ ] Add supported text insertion workflows
- [ ] Accessibility integration only where necessary and clearly explained to the user
- [ ] Add controlled access to supported messaging and social apps such as WhatsApp and TikTok where Android provides a permitted integration path
- [ ] Allow the user to explicitly invoke the assistant from a supported app context so it can work with the visible conversation/content they chose to share
- [ ] For supported chat workflows, let the assistant draft a reply from the selected conversation context without sending it automatically
- [ ] Show a clear draft action sheet with `Send`, `Edit` and `Delete` after text is prepared
- [ ] `Send` must require an explicit user action and must never happen because the model generated text alone
- [ ] Keep sensitive app content local where possible and clearly indicate what content is being shared with the assistant
- [ ] Never silently send messages, publish content, delete user data or make purchases
- [ ] Require explicit confirmation for consequential actions

## Phase 5 — Keyboard assistant

- [ ] Build or integrate a supported Android IME workflow
- [ ] Add an AI action button for drafting replies
- [ ] Insert drafted text into the current text field without sending it
- [ ] Provide `Send`, `Edit` and `Delete` controls after insertion where the host app allows it
- [ ] Keep sending as a separate user-confirmed action
- [ ] Support a context-aware drafting flow for venting / support conversations when the user explicitly invokes the assistant
- [ ] Never press Enter, trigger a send action, or otherwise submit the draft automatically

## Phase 6 — S•S AI ecosystem

SS Assistent, the writing AI and the image AI remain separate applications while they are developed. Later they can share a deliberate S•S integration layer.

- [ ] Shared account / identity model if needed
- [ ] Controlled hand-off between the three S•S AI apps
- [ ] Shared settings where useful
- [ ] Keep local-only assistant functionality independent from cloud services
