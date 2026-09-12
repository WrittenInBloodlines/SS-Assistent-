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
- [x] Add deterministic natural-language detection for explicit requests such as "save this exactly as written" / "store this exactly"
- [x] Route detected exact-memory intent directly through the chat send flow
- [x] When exact/sealed memory is requested, show a confirmation preview and clearly state that the text will be stored exactly as supplied
- [x] Prioritize sealed memory during retrieval and label it as exact/sealed in the model prompt
- [x] Enforce the exact-memory size limit at the storage layer
- [x] Provide a sealed-memory replacement primitive that requires an explicit override and preserves the replacement exactly
- [x] Add a local sealed-memory version/history trail and review UI
- [x] Add deterministic parsing for explicit sealed-memory replacement commands with quoted old/new values
- [x] Resolve parsed sealed-memory replacement against the user's stored memory without mutating it
- [x] Show a sealed-memory replacement confirmation preview in the chat UI
- [x] Apply a confirmed replacement while preserving every other character/content exactly
- [x] Add a deterministic sensitivity classifier for memory candidates
- [x] Route sensitive memory candidates through an explicit user-choice policy in the save UI
- [x] Avoid storing sensitive information unless the user explicitly chooses to remember it
- [x] Encrypt persisted memory and sealed-memory history with Android Keystore-backed AES-GCM
- [x] Migrate legacy plaintext memory/history payloads into encrypted storage on first successful read
- [x] Add stronger semantic-ish memory retrieval using deterministic offline token normalization, conservative stemming, phrase matching and a small multilingual synonym map
- [ ] Add embedding-based semantic retrieval only if it can run locally without undermining privacy or device performance

## Phase 3B — Story, lore and continuity intelligence

This is designed primarily for story/character work. The assistant should not treat every surprising detail as an error; it should compare new text against the established canon, timeline, character facts and intentional hidden-information rules before raising anything.

### Plot-hole detection

- [x] Add a local story continuity workspace
- [x] Detect the initial high-confidence car → kitchen missing-transition pattern
- [x] Show a clear non-destructive notice such as **"Plot hole detected"** instead of silently rewriting the story
- [x] Explain the exact conflicting or missing information in a compact review card
- [x] Offer `Ignore`, `Edit` and a suggested fix for the implemented plot-hole warning
- [x] Suggested fixes are optional and are never inserted automatically
- [x] Example: if a character is described as being in a car, then later walks into the kitchen without the story establishing that they returned home, flag the missing transition and suggest ways to resolve it
- [x] Keep plot-hole detection separate from the actual story text so the user remains in control
- [x] Expand location-transition detection beyond the initial car → kitchen pattern
- [ ] Expand plot-hole detection to broader timelines and character movement across scene boundaries

### Secret / hidden-information system

- [x] Add a story-level **Secrets** store for information intentionally unknown to one or more characters
- [x] Store who knows a secret and keep the secret separate from normal lore facts
- [x] Prevent the continuity checker from treating the assistant's knowledge as character knowledge
- [x] Preserve uncertainty by warning when a draft appears to reveal a stored secret
- [x] Add a review warning when a generated/draft sentence appears to reveal hidden information
- [x] Offer `Keep hidden` and `Reveal` controls for the implemented secret-leak warning
- [x] Add `Edit` and `Ignore` controls to the secret-leak review
- [x] Make secret `Edit` open a real structured editor for the stored secret and its known-by list
- [ ] Add per-character discovery timing and explicit narration/reveal permissions
- [x] Never reveal a secret merely because it exists in the assistant's story data

### Lore contradiction detection

- [x] Add structured local canon facts such as subject + attribute + value
- [x] Detect the implemented subject/attribute/value contradiction pattern before changing canon
- [x] Show a clear **"Lore conflict detected"** review card with `Ignore` and `Change`
- [x] Show the established canon value and the newly detected value in the warning details
- [x] Never silently overwrite established canon because the latest draft contains a different fact
- [x] Example: if canon says Ciro has dark brown eyes and a new draft says he has blue eyes, the continuity workspace can flag the contradiction
- [x] `Ignore` keeps the established canon and does not modify the new draft automatically
- [x] `Change` requires an explicit new canon value before changing the stored fact
- [x] Suggested resolutions remain suggestions and require user confirmation
- [x] Track intentional canon changes with local version/history metadata so old facts can be audited safely
- [x] Link lore warnings to their exact canon fact ID instead of reverse-matching warning text
- [ ] Expand contradiction parsing beyond the initial explicit attribute/value patterns

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
