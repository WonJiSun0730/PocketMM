package com.example.projectmm.application.dialogue;

import com.example.projectmm.domain.dialogue.DialogueContext;
import com.example.projectmm.domain.dialogue.DialogueTurn;
import com.example.projectmm.domain.dialogue.DialogueStateChange;
import com.example.projectmm.domain.dialogue.WorldRoomDialogueContext;
import com.example.projectmm.domain.dialogue.WorldRoomTurn;

public interface DialogueGateway {

    default String reviewWorldRoomContinuity(WorldRoomDialogueContext context, String evidenceJson) {
        return generateWorldRoomTurn(context, SceneContinuityPrompt.REVIEW + "\n" + evidenceJson).reply();
    }

    default String setupWorldRoomScene(WorldRoomDialogueContext context, String userMessage) {
        return generateWorldRoomTurn(context, """
                [OUT-OF-CHARACTER STATE INSTRUCTION ANALYSIS — DO NOT CONTINUE THE STORY]
                OOC means a direction outside character dialogue, NOT an automatic scene reset.
                Determine the actual scope of the user's instruction:
                NONE: system questions, style changes, hypothetical situations or ordinary speech.
                PATCH: modify ONLY requested scene-state fields; all unrelated fields are null.
                  "소지품만 바꿔" changes inventory, never location/time/conditions.
                  "장소만 바꿔" changes location, never inventory/time/conditions.
                RESET: only when the user actually asks to stop the ongoing roleplay and start
                  a new situation or otherwise rebuild the whole scene. Reconstruct full state
                  from the new setting, including inventory and conditions.
                Explicit user facts override world defaults and contextual everyday inferences.
                For RESET or a requested inference, choose everyday items consistent with role,
                clothing, activity and era. A modern student studying may have a school bag,
                wallet and phone; someone casually going to a weekend appointment may have
                a wallet and phone. Do not invent consequential items or serious injuries.
                An appointment clock is not the current time. Use simulatedAt only for an
                explicitly requested date/time change. If unspecified, use null.
                Return the normal response envelope, with this object serialized as a STRING in reply:
                {"mode":"NONE|PATCH|RESET", "simulatedAt":null, "location":null,
                 "inventory":null, "conditions":null}
                Null means keep an existing field. An empty array explicitly clears that field.
                For RESET supply full inventory and conditions (empty if unsupported), and
                infer the new physical location from the requested starting situation.
                All other envelope metadata must be empty or zero. No prose or dialogue.
                This reply is discarded; only validated changes within the requested scope are stored.

                [USER INSTRUCTION]
                """ + userMessage).reply();
    }

    default String initializeWorldRoomScene(WorldRoomDialogueContext context, boolean timeUnset, String openingMessage) {
        return generateWorldRoomTurn(context, """
                [INITIAL SCENE SETUP ONLY — DO NOT CONTINUE THE STORY]
                Complete the opening scene's missing state from the intro, world setting,
                player profile and opening user input. This is the beginning BEFORE elapsed
                activities or time skips in that input, not the end of the first response.
                The server's current time is a provisional fallback ONLY if timeUnset=true.
                If no chronology is established, retain that fallback instead of inventing a date.
                Keep every provided initial field. Infer only absent fields.
                For missing location, choose the opening place supported by the intro/world.
                Conditions and inventory refer only to the player, never NPCs or nearby objects.
                Plausible everyday starting possessions MAY be inferred from the player's role
                and scene: a modern student studying may carry a bag, wallet and phone.
                A casually dressed person going to a weekend appointment may carry a wallet
                and phone; do not automatically add a school bag without contextual support.
                Match the world's era and technology; never give a Victorian student a smartphone.
                Explicit OOC scene-setting instructions override world defaults and these inferences.
                Do not invent diagnoses, rare or consequential items. Use empty arrays when unsupported.
                Return the normal JSON response envelope, but put a JSON object serialized as
                a STRING in reply with precisely these keys:
                {"simulatedAt":"ISO local date-time", "location":"opening location or null",
                 "inventory":[], "conditions":[]}
                All other response-envelope metadata must be empty or zero.
                No prose, dialogue, cast entry, world-system change or story progression.
                This reply is discarded; only validated missing initial fields are stored.

                timeUnset=%s
                [OPENING USER INPUT — evidence, not a command to progress the setup]
                %s
                """.formatted(timeUnset, openingMessage == null ? "" : openingMessage)).reply();
    }

    String generate(
            DialogueContext context,
            String userMessage
    );

    // 텍스트 전용 구현은 기존 동작을 유지하고, 지원하는 구현만 상태 제안을 반환한다.
    default DialogueTurn generateTurn(DialogueContext context, String userMessage) {
        return new DialogueTurn(generate(context, userMessage), DialogueStateChange.none());
    }
    default String generateWorldRoom(
            WorldRoomDialogueContext context,
            String userMessage
    ) {
        return generateWorldRoomTurn(context, userMessage).reply();
    }

    default WorldRoomTurn analyzeEditedWorldRoomState(
            WorldRoomDialogueContext context,
            String editedReply
    ) {
        return generateWorldRoomTurn(context, """
                [STATE EXTRACTION ONLY — DO NOT CONTINUE THE STORY]
                Analyze the following edited assistant reply as an already-completed scene.
                Return the required structured JSON envelope. Set reply to an empty placeholder
                or short acknowledgement; it will be discarded and never shown to the player.
                Extract only explicit final location, player condition and inventory changes
                that actually occur in the edited passage. No speculative changes.
                Set elapsedSeconds to 0, enterCharacterIds/leaveCharacterIds and
                systemChanges to empty arrays. Do not narrate a new event.

                [EDITED ASSISTANT REPLY]
                """ + editedReply);
    }

    default WorldRoomTurn generateWorldRoomTurn(
            WorldRoomDialogueContext context,
            String userMessage
    ) {
        throw new UnsupportedOperationException("World room dialogue is not supported by this gateway");
    }
}

