package com.example.projectmm.application.world;

import com.example.projectmm.api.world.*;
import com.example.projectmm.domain.member.MemberRepository;
import com.example.projectmm.domain.world.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class WorldSystemService {
    private final WorldRepository worlds;
    private final WorldSystemRepository systems;
    private final WorldSystemStateRepository states;
    private final MemberRepository members;

    @Transactional(readOnly = true)
    public WorldSystemsResponse get(Long worldId, String loginId) {
        World world = owned(worldId, loginId);
        return new WorldSystemsResponse(world.getPlayGuide(), systems.findByWorldIdOrderBySortOrderAsc(worldId).stream()
                .map(system -> new WorldSystemItem(system.getName(), system.getChangeCondition(), system.getMinValue(), system.getMaxValue(), system.getInitialValue(),
                        states.findBySystemIdOrderBySortOrderAsc(system.getId()).stream()
                                .map(state -> new WorldSystemStateItem(state.getMinValue(), state.getMaxValue(), state.getLabel())).toList()))
                .toList());
    }

    @Transactional
    public WorldSystemsResponse replace(Long worldId, WorldSystemsRequest request, String loginId) {
        World world = owned(worldId, loginId);
        String playGuide = blankToNull(request.playGuide());
        if (playGuide != null && playGuide.length() > 1000) throw new IllegalArgumentException("Play guide must not exceed 1000 characters.");
        List<WorldSystemItem> normalized = new ArrayList<>();
        List<WorldSystemItem> requested = request.systems() == null ? List.of() : request.systems();
        long definedSystemCount = requested.stream().filter(item -> item.name() != null && !item.name().isBlank()).count();
        if (definedSystemCount > 5) throw new IllegalArgumentException("World systems must not exceed 5.");
        // Validate the entire request before replacing definitions or changing the play guide.
        for (WorldSystemItem item : requested) {
            if (item.name() == null || item.name().isBlank()) continue;
            if (item.name().trim().length() > 100) throw new IllegalArgumentException("System name must not exceed 100 characters.");
            if (item.minValue() >= item.maxValue()) throw new IllegalArgumentException("System minimum must be lower than maximum.");
            if (item.initialValue() == null) throw new IllegalArgumentException("시스템 초기값을 입력해주세요.");
            if (item.initialValue() < item.minValue() || item.initialValue() > item.maxValue())
                throw new IllegalArgumentException("System initial value is outside system bounds.");
            for (WorldSystemStateItem state : item.states() == null ? List.<WorldSystemStateItem>of() : item.states()) {
                if (state.label() == null || state.label().isBlank()) continue;
                if (state.minValue() > state.maxValue() || state.minValue() < item.minValue() || state.maxValue() > item.maxValue())
                    throw new IllegalArgumentException("System state range is outside system bounds.");
            }
        }
        world.updatePlayGuide(playGuide);
        // Existing pockets reference these immutable definitions; preserve their ranges and state labels.
        for (WorldSystem system : systems.findByWorldIdOrderBySortOrderAsc(worldId)) system.archive();
        for (int i = 0; i < requested.size(); i++) {
            WorldSystemItem item = requested.get(i);
            if (item.name() == null || item.name().isBlank()) continue;
            if (item.minValue() >= item.maxValue()) throw new IllegalArgumentException("System minimum must be lower than maximum.");
            if (item.initialValue() < item.minValue() || item.initialValue() > item.maxValue())
                throw new IllegalArgumentException("System initial value is outside system bounds.");
            WorldSystem saved = systems.save(new WorldSystem(
                    world,
                    item.name().trim(),
                    value(item.changeCondition()),
                    item.minValue(),
                    item.maxValue(),
                    item.initialValue(),
                    i
            ));
            List<WorldSystemStateItem> stateItems = item.states() == null ? List.of() : item.states();
            List<WorldSystemStateItem> kept = new ArrayList<>();
            for (int j = 0; j < stateItems.size(); j++) {
                WorldSystemStateItem state = stateItems.get(j);
                if (state.label() == null || state.label().isBlank()) continue;
                if (state.minValue() > state.maxValue() || state.minValue() < item.minValue() || state.maxValue() > item.maxValue())
                    throw new IllegalArgumentException("System state range is outside system bounds.");
                String label = state.label().trim();
                states.save(new WorldSystemState(saved, state.minValue(), state.maxValue(), label, j));
                kept.add(new WorldSystemStateItem(state.minValue(), state.maxValue(), label));
            }
            normalized.add(new WorldSystemItem(
                    saved.getName(),
                    saved.getChangeCondition(),
                    saved.getMinValue(),
                    saved.getMaxValue(),
                    saved.getInitialValue(),
                    kept
            ));
        }
        return new WorldSystemsResponse(world.getPlayGuide(), normalized);
    }

    private World owned(Long worldId, String loginId) {
        Long ownerId = members.findByLoginId(loginId).orElseThrow(() -> new IllegalArgumentException("Member not found.")).getId();
        return worlds.findByIdAndOwnerId(worldId, ownerId).orElseThrow(() -> new IllegalArgumentException("World not found."));
    }
    private String blankToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private String value(String value) { return value == null ? "" : value.trim(); }
}
