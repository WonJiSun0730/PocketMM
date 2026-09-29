package com.example.projectmm.infrastructure.memory;

import com.example.projectmm.domain.character.Character;
import com.example.projectmm.domain.chat.Conversation;
import com.example.projectmm.domain.memory.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class MemoryCompressionPromptBuilderTest {
    private final MemoryAccessStateRepository access = mock(MemoryAccessStateRepository.class);
    private final MemoryCompressionPromptBuilder builder = new MemoryCompressionPromptBuilder(access, mock(com.example.projectmm.application.memory.MemoryProvenanceAccessPolicy.class));

    @Test
    void summaryUsesOnlyOneMemoryAndPreservesContinuityInstructions() {
        var text = builder.build(memory(1, "core event"), MemoryDetailLevel.SUMMARY,
                List.of(memory(2, "UNRELATED_SECRET"))).orElseThrow();
        assertThat(text).contains("Target: SUMMARY", "single event only", "relationship", "narrative continuity",
                "Do not add new facts", "core event").doesNotContain("UNRELATED_SECRET", "Related evidence");
        verify(access, never()).findByMemoryId(2L);
    }

    @Test
    void patternAndSchemaRequireEvidenceAndHaveDifferentInstructions() {
        var current = memory(1, "helped once");
        current.compress("helped once");
        assertThat(builder.build(current, MemoryDetailLevel.PATTERN, List.of())).isEmpty();
        String pattern = builder.build(current, MemoryDetailLevel.PATTERN, List.of(memory(2, "helped again"))).orElseThrow();
        assertThat(pattern).contains("helped again", "Never fabricate repetition", "mere lexical overlap", "summary=null");
        current.compress("helping tendency");
        assertThat(builder.build(current, MemoryDetailLevel.SCHEMA, List.of(memory(2, "helped again")))).isEmpty();
        String schema = builder.build(current, MemoryDetailLevel.SCHEMA,
                List.of(memory(2, "helped again"), memory(3, "another situation"))).orElseThrow();
        assertThat(schema).contains("current PATTERN", "Avoid overgeneralization", "항상, 절대, 무조건", "uncertainty and exceptions");
        assertThat(schema).isNotEqualTo(pattern);
    }

    @ParameterizedTest
    @EnumSource(value = MemoryAccessLevel.class, names = {"IMPLICIT", "RESIDUAL", "BLOCKED"})
    void restrictedCurrentMemoryNeverProducesPrompt(MemoryAccessLevel level) {
        var current = memory(1, "RAW_SECRET");
        when(access.findByMemoryId(1L)).thenReturn(Optional.of(new MemoryAccessState(current, level, LocalDateTime.now())));
        assertThat(builder.build(current, MemoryDetailLevel.SUMMARY, List.of())).isEmpty();
    }

    @ParameterizedTest
    @EnumSource(value = MemoryAccessLevel.class, names = {"IMPLICIT", "RESIDUAL", "BLOCKED"})
    void restrictedEvidenceIsRemovedBeforeEvidenceGate(MemoryAccessLevel level) {
        var current = memory(1, "event");
        current.compress("event");
        var secret = memory(2, "RAW_SECRET");
        when(access.findByMemoryId(2L)).thenReturn(Optional.of(new MemoryAccessState(secret, level, LocalDateTime.now())));
        assertThat(builder.build(current, MemoryDetailLevel.PATTERN, List.of(secret))).isEmpty();
        assertThat(builder.build(current, MemoryDetailLevel.PATTERN, List.of(secret, memory(3, "safe evidence"))).orElseThrow())
                .doesNotContain("RAW_SECRET");
    }

    @Test
    void excludesOtherCharactersRoomsAndWorldRoomMixing() {
        var current = memory(1, "event");
        current.compress("event");
        var foreign = memory(2, "FOREIGN");
        var other = new Character("other");
        ReflectionTestUtils.setField(other, "id", 99L);
        ReflectionTestUtils.setField(foreign, "character", other);
        var room = new Conversation(current.getCharacter(), LocalDateTime.now());
        ReflectionTestUtils.setField(room, "id", 10L);
        var roomMemory = memory(3, "ROOM_SECRET");
        ReflectionTestUtils.setField(roomMemory, "conversation", room);
        assertThat(builder.build(current, MemoryDetailLevel.PATTERN, List.of(foreign, roomMemory))).isEmpty();
        ReflectionTestUtils.setField(current, "conversation", room);
        assertThat(builder.build(current, MemoryDetailLevel.PATTERN, List.of(memory(4, "WORLD_SECRET")))).isEmpty();
        assertThat(builder.build(current, MemoryDetailLevel.PATTERN, List.of(roomMemory))).isPresent();
    }

    @Test
    void excludesDuplicateIdsAndCapsRelatedEvidence() {
        var current = memory(1, "event");
        current.compress("event");
        assertThat(builder.build(current, MemoryDetailLevel.PATTERN, List.of(current, memory(1, "same row")))).isEmpty();
        String text = builder.build(current, MemoryDetailLevel.PATTERN,
                LongStream.rangeClosed(2, 15).mapToObj(id -> memory(id, "evidence " + id)).toList()).orElseThrow();
        assertThat(text).contains("Related evidence 8").doesNotContain("Related evidence 9");
        current.compress("pattern");
        assertThat(builder.build(current, MemoryDetailLevel.SCHEMA, List.of(memory(2, "a"), memory(2, "a")))).isEmpty();
    }

    static Memory memory(long id, String summary) {
        var character = new Character("NPC");
        ReflectionTestUtils.setField(character, "id", 1L);
        var memory = new Memory(character, null, summary, 5, 50, LocalDateTime.of(2026, 1, 1, 12, 0));
        ReflectionTestUtils.setField(memory, "id", id);
        return memory;
    }
}
