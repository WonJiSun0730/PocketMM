# Prompt module migration — stage A

Source: GitHub `review/experience-qa`, `27ba06f94e5fcfb26d412a33eb6839b95c46f70b`.
The API branch was checked before editing. The local source snapshot was initialized as a separate
audit Git repository because authenticated Git clone is unavailable; publication uses GitHub's
expected-head lease. Pre-existing untracked `.mvn/jvm.config`, Surefire temporary directories and
`tools/experience-qa-model-limits.json` are preserved and excluded from commits.

## Responsibility and retained code

- SYSTEM.md: authority, memory/perception boundaries, profile scoping, meta commands and common output conventions.
- SCENE.md: player participation, scene/cast continuity, legacy time authority, pacing, automatic scope and live/absence distinction.
- Existing prose guidance stays in SCENE during this equivalence-only step. Its wording is not rewritten.
- IDENTITY/SOUL and character examples remain in the Java character-profile builder; CharacterProfile and DialogueCharacterProfile are unchanged.
- Runtime state, lore, world systems, player settings and recalled memories still come from existing builders. Markdown contains templates, never stored session values.
- Provider-specific JSON schemas, validation, parsing, request envelopes, elapsed-time/location/inventory delta mappings and structured extraction tasks stay in Java.
- PocketActivityPrompt's serialized plan schema and task-specific constraints stay in Java. This is explicitly an absence task, not live-scene delegation.
- Memory extraction, embeddings, compression, routing and the shared QA hard-cap guard are unchanged.

## Composition and loading

The original fragment order is retained, including interleaved templates, followed by existing live policy and the selected strict/elaboration policy. Dynamic time, system values, player state, presence and recalled memories remain in the existing dynamic suffix. Automatic continuation selects the same STRICT/LOW/MODERATE/HIGH directives.

Resources load as UTF-8 from the classpath once, including from a packaged jar. Missing files, missing required sections, empty or duplicate sections fail closed. Section markers and documentation headings are never sent to the model. The gateway validates the complete required section catalog at initialization. No filesystem hot reload or extra model call is introduced.

## Equivalence evidence

`pre-modularization-sha256.json` was captured by a passing test before modifying any existing prompt. It fingerprints normalized complete world stable/dynamic prompts under both player modes, the legacy request's complete system prompt, all six continuation scope fixtures and the structured absence plan. Only line endings and trailing whitespace are normalized. The capture switch was removed afterward, so tests cannot silently regenerate the baseline.

Stage A must pass before introducing NARRATOR. Provider request schemas remain covered by existing Gemini/OpenAI parsing and mock request tests.

## Moved fragments

- `infrastructure/dialogue/GeminiDialogueGateway.java:402` → `(PromptModules.section("SYSTEM", "world.setup") +                 PromptModules.section("SCENE", "world.player-settings") +                 PromptModules.section("SYSTEM", "world.context-template") +                 PromptModules.section("SYSTEM", "world.authority") +                 PromptModules.section("SCENE", "world.cast") +                 PromptModules.section("SYSTEM", "world.character-output"))`
- `infrastructure/dialogue/GeminiDialogueGateway.java:898` → `PromptModules.section("SCENE", "legacy.continue")`
- `infrastructure/dialogue/GeminiDialogueGateway.java:918` → `PromptModules.section("SYSTEM", "legacy.resolved-input")`
- `infrastructure/dialogue/GeminiDialogueGateway.java:1046` → `(PromptModules.section("SYSTEM", "legacy.setup") +                 PromptModules.section("SCENE", "legacy.time") +                 PromptModules.section("SYSTEM", "legacy.memory-character") +                 PromptModules.section("SCENE", "legacy.prose-conventions") +                 PromptModules.section("SCENE", "legacy.scene-focus") +                 PromptModules.section("SCENE", "legacy.authorship") +                 PromptModules.section("SYSTEM", "legacy.perception"))`
- `infrastructure/dialogue/WorldRoomNarrativeStyle.java:6` → `PromptModules.section("SCENE", "live.strict")`
- `infrastructure/dialogue/WorldRoomNarrativeStyle.java:12` → `PromptModules.section("SCENE", "live.elaborate")`
- `infrastructure/dialogue/WorldRoomNarrativeStyle.java:51` → `PromptModules.section("SCENE", "live.pacing")`
- `application/dialogue/WorldRoomContinuationPrompt.java:16` → `PromptModules.section("SCENE", "continuation.progress")`
- `application/dialogue/WorldRoomContinuationPrompt.java:27` → `PromptModules.section("SCENE", "continuation.strict")`
- `application/dialogue/WorldRoomContinuationPrompt.java:35` → `PromptModules.section("SCENE", "continuation.low")`
- `application/dialogue/WorldRoomContinuationPrompt.java:49` → `PromptModules.section("SCENE", "continuation.moderate")`
- `application/dialogue/WorldRoomContinuationPrompt.java:61` → `PromptModules.section("SCENE", "continuation.high")`
- `application/dialogue/WorldRoomContinuationPrompt.java:70` → `PromptModules.section("SCENE", "continuation.delegated-intro")`
- `application/dialogue/WorldRoomContinuationPrompt.java:75` → `PromptModules.section("SCENE", "continuation.delegated-end")`
