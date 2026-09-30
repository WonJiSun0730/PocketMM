package com.example.projectmm.application.simulation;

import com.example.projectmm.domain.character.CharacterState;
import com.example.projectmm.domain.simulation.CatchUpSegment;
import com.example.projectmm.domain.simulation.EventCandidate;
import com.example.projectmm.domain.simulation.SegmentPeriod;
import com.example.projectmm.domain.simulation.StateDelta;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class CatchUpEventCandidateService {

    public List<EventCandidate> generate(
            CharacterState state,
            CatchUpSegment segment
    ) {
        List<EventCandidate> candidates = new ArrayList<>();

        if (segment.period() == SegmentPeriod.NIGHT
                && state.getEnergy() < 50) {

            candidates.add(
                    new EventCandidate(
                            segment.start(),
                            "REST",
                            "피로가 누적되어 휴식을 취했다.",
                            2,
                            false,
                            new StateDelta(30, 0)
                    )
            );

            return candidates;
        }

        if (state.getMood() <= 20) {

            candidates.add(
                    new EventCandidate(
                            segment.start(),
                            "MOOD_CHANGE",
                            "기분이 크게 가라앉아 있었다.",
                            4,
                            true,
                            StateDelta.none()
                    )
            );

            return candidates;
        }

        candidates.add(
                new EventCandidate(
                        segment.start(),
                        "DAILY_ACTIVITY",
                        "현재 상태와 최근 맥락에 맞는 일상 행동을 했다.",
                        2,
                        true,
                        StateDelta.none()
                )
        );

        return candidates;
    }
}