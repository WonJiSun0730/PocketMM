package com.example.projectmm.application.memory;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class PocketMemoryCommitListener {

    private final PocketMemoryService pocketMemoryService;
    private final PocketTurnTestSnapshotService testSnapshots;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onUserSpeechCommitted(PocketUserSpeechCommitted event) {
        pocketMemoryService.rememberUserSpeech(
                event.sessionId(),
                event.sourceMessageId(),
                event.userMessage(),
                event.at(),
                event.audienceCharacterIds()
        );
        testSnapshots.captureAfterExtraction(event.sessionId(), event.sourceMessageId());
    }
}
