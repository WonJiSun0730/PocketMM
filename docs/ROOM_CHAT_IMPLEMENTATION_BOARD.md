# PocketMM Room Chat Implementation Board

## Product model
- World -> Room Definition -> per-user Play Session.
- Room Definition is creator-owned. Players cannot edit scenario, Room Cast, or Intros.
- Public Room is a shared scenario definition; each user has independent session/history/state.
- Room Cast is the allowed character pool. Active Cast is runtime state per session and starts empty.
- Selected Room Intro establishes the opening context but does not mechanically activate characters.

## Chat history
- Preserve original Room chat messages on the server. AI context-window limits must never delete user-visible history.
- Load recent messages first and page older messages on demand.
- AI receives a bounded recent-message window plus summaries/memory/state as appropriate.
- Users must be able to revisit old RP logs.

## Message editing
- AI prose must be directly editable.
- The edited text becomes the canonical text read by subsequent AI turns.
- Editing must eventually reconcile any derived turn state if the edited turn already affected runtime state.

## Delete / regenerate
- Deleting a turn means that situation is rejected.
- Because later turns may depend on it, destructive rewind removes/truncates that point and dependent later turns.
- Regenerate after deletion must not use the rejected AI response as context.
- This is intentionally different from reroll and is the fastest way to request a substantially different reaction.

## Reroll / variants
- Reroll keeps the same preceding canonical history and user input.
- Each reroll uses the immediately previous variant as a variation reference; it does not need to reference the original response.
- Early rerolls may stay near the same event trajectory; repeated rerolls may naturally explore increasingly different plausible reactions.
- Do not enforce a mechanical divergence counter/cooldown initially.
- Preserve variants. The user can browse and select the canonical variant.
- Selecting a variant must also select/reconcile that variant's runtime side effects.

## Turn side effects
Treat generated prose and structured side effects as one turn result:
- Active Cast enter/leave changes.
- World System value changes.
- Future narrative/relationship/memory changes that become turn-bound.

Reroll, variant selection, edit, delete, and rewind must not leave side effects from a rejected branch active.

## Session World Systems / RP HUD
- WorldSystem defines a world's stat/rule: name, range, change conditions, display states.
- Each Play Session owns its current values.
- Example: Pieta is defined by the World; J's session may be 100% while another user's session may be 63%.
- Values may change from structured AI output during RP.
- Users may explicitly correct/set values through chat/OOC controls.
- RP header/HUD reads actual session state, e.g. `✨: [100%]` for Pieta.
- This is user-facing story state, not a developer cheat.

## Room chat UI target
- Room create/edit: title, PRIVATE/PUBLIC.
- Room Cast selection.
- Multiple Room Intros; choose one when starting.
- PlayerProfile selection.
- Chat message actions: Edit / Reroll / Delete.
- Variant navigation such as `‹ 2 / 5 ›`.
- Paginated full-history browsing.
- Session system-state/HUD display and correction.
- Multi-character speaker format: `CharacterName: "dialogue"`; narration in `*...*`.

## Implementation order
1. Preserve full message history + history pagination API.
2. Introduce canonical turn/variant model.
3. AI response edit.
4. Delete/rewind + clean regenerate.
5. Chained reroll + variant selection.
6. Turn-bound state snapshot/reconciliation for Active Cast.
7. Session WorldSystem current values + manual/AI changes.
8. Room chat UI and runtime dogfood.
9. Sherlock Holmes + Victor Hale Room test, then QWL/Marbas dogfood.

## Test principles
- Storage retention and AI context limits are separate.
- Rejected variants must never leak into canonical AI history/state.
- A newly started session has zero Active Cast.
- Registered Room Cast never means all characters appear at once.
- Direct edit is used for local prose/voice correction; delete+regenerate is for rejecting the situation; reroll is for nearby/branching alternatives.
