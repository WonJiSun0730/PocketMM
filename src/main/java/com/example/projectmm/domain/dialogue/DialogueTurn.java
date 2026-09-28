package com.example.projectmm.domain.dialogue;

import com.example.projectmm.domain.state.CustomStateChangeProposal;

import java.util.List;

// 화면에 표시할 RP 본문과 서버가 검증할 상태 제안을 분리한다.
public record DialogueTurn(
        String reply,
        DialogueStateChange stateChange,
        DialoguePlayerStateChange playerStateChange,
        List<CustomStateChangeProposal> customStateChanges
) {
    public DialogueTurn {
        // 호출자가 원본 목록을 수정해도 이미 생성된 대화 결과는 변하지 않는다.
        customStateChanges = customStateChanges == null
                ? List.of()
                : List.copyOf(customStateChanges);

        playerStateChange = playerStateChange == null
                ? DialoguePlayerStateChange.invalid()
                : playerStateChange;
    }

    public DialogueTurn(
            String reply,
            DialogueStateChange stateChange
    ) {
        this(
                reply,
                stateChange,
                DialoguePlayerStateChange.none(),
                List.of()
        );
    }

    public DialogueTurn(
            String reply,
            DialogueStateChange stateChange,
            List<CustomStateChangeProposal> customStateChanges
    ) {
        this(
                reply,
                stateChange,
                DialoguePlayerStateChange.none(),
                customStateChanges
        );
    }
}