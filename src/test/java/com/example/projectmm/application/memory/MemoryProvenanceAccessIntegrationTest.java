package com.example.projectmm.application.memory;

import com.example.projectmm.application.ai.AiContextBuilder;
import com.example.projectmm.domain.character.Character;
import com.example.projectmm.domain.character.CharacterState;
import com.example.projectmm.domain.character.CharacterStateRepository;
import com.example.projectmm.domain.chat.Conversation;
import com.example.projectmm.domain.memory.*;
import com.example.projectmm.domain.relationship.RelationshipRepository;
import com.example.projectmm.domain.simulation.EventCandidate;
import com.example.projectmm.domain.simulation.StateDelta;
import com.example.projectmm.infrastructure.memory.MemoryCompressionPromptBuilder;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.LocalDateTime;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@DataJpaTest(properties = {"spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop", "spring.jpa.show-sql=false"}, showSql = false)
@Import(MemoryProvenanceAccessPolicy.class)
class MemoryProvenanceAccessIntegrationTest {
    @Autowired EntityManager em;
    @Autowired MemoryRepository memories;
    @Autowired MemoryAccessStateRepository access;
    @Autowired MemoryCompressionEvidenceRepository provenance;
    @Autowired MemoryProvenanceAccessPolicy policy;
    private final LocalDateTime now = LocalDateTime.of(2026, 9, 29, 12, 0);
    private Character character;
    private MemoryRecallService recall;

    @BeforeEach void setup() {
        character = new Character("NPC");
        em.persist(character);
        recall = new MemoryRecallService(memories, new MemoryDecayPolicy(), mock(MemoryCompressionService.class),
                mock(CharacterStateRepository.class), access, mock(MemoryLifecycleService.class), policy);
    }

    private Memory memory(MemoryDetailLevel level) {
        var item = new Memory(character, null, "기밀 원문 위험한 상황 도움", 9, 100, 2, now);
        while (item.getDetailLevel() != level) item.compress(item.getSummary());
        em.persist(item);
        return item;
    }

    private void edge(Memory source, Memory evidence, MemoryDetailLevel stage) {
        provenance.saveAndFlush(new MemoryCompressionEvidence(source, stage, evidence));
    }

    private void change(Memory memory, MemoryAccessLevel level) {
        // 실제 access 서비스로 저장한다. lifecycle timestamp 부작용은 이 테스트의 관심사가 아니다.
        new MemoryAccessService(memories, access, mock(MemoryLifecycleService.class))
                .changeAccess(memory.getId(), level, now);
        em.flush();
        em.clear();
    }

    private EffectiveMemory recalled(Memory memory) {
        return recall.recall(character.getId(), now, 20).stream()
                .filter(item -> item.memoryId().equals(memory.getId())).findFirst().orElse(null);
    }

    @Test void storedPatternAndSchemaFollowEvidenceRestrictionAndRecoverWithoutMutation() {
        var evidence = memory(MemoryDetailLevel.DETAILED);
        var other = memory(MemoryDetailLevel.SUMMARY);
        var pattern = memory(MemoryDetailLevel.PATTERN);
        var schema = memory(MemoryDetailLevel.SCHEMA);
        edge(pattern, evidence, MemoryDetailLevel.PATTERN);
        edge(schema, pattern, MemoryDetailLevel.SCHEMA);
        edge(schema, other, MemoryDetailLevel.SCHEMA);
        assertThat(recalled(schema).accessLevel()).isEqualTo(MemoryAccessLevel.EXPLICIT);
        for (var level : List.of(MemoryAccessLevel.IMPLICIT, MemoryAccessLevel.RESIDUAL, MemoryAccessLevel.BLOCKED)) {
            change(evidence, level);
            if (level == MemoryAccessLevel.BLOCKED) {
                assertThat(recalled(pattern)).isNull();
                assertThat(recalled(schema)).isNull();
            } else {
                assertThat(recalled(pattern).accessLevel()).isEqualTo(level);
                assertThat(recalled(schema).accessLevel()).isEqualTo(level);
                assertThat(recalled(schema).effectiveStrength()).isEqualTo(level == MemoryAccessLevel.IMPLICIT ? 60 : 30);
            }
        }
        change(schema, MemoryAccessLevel.IMPLICIT);
        change(evidence, MemoryAccessLevel.EXPLICIT);
        assertThat(recalled(pattern).accessLevel()).isEqualTo(MemoryAccessLevel.EXPLICIT);
        assertThat(recalled(schema).accessLevel()).isEqualTo(MemoryAccessLevel.IMPLICIT);
        change(schema, MemoryAccessLevel.BLOCKED);
        assertThat(recalled(schema)).isNull();
        var stored = memories.findById(schema.getId()).orElseThrow();
        assertThat(stored.getSummary()).isEqualTo(schema.getSummary());
        assertThat(stored.getStrength()).isEqualTo(100);
        assertThat(stored.getImportance()).isEqualTo(9);
        assertThat(stored.getNarrativeAnchor()).isEqualTo(2);
        assertThat(access.findByMemoryId(pattern.getId())).isEmpty();
    }

    @Test void historicalStageAndLegacyWithoutProvenanceRemainCompatible() {
        var evidence = memory(MemoryDetailLevel.DETAILED);
        var derived = memory(MemoryDetailLevel.SCHEMA);
        edge(derived, evidence, MemoryDetailLevel.PATTERN); // 이전 압축 단계의 근거
        var legacy = memory(MemoryDetailLevel.SCHEMA);
        var summary = memory(MemoryDetailLevel.SUMMARY);
        change(evidence, MemoryAccessLevel.BLOCKED);
        assertThat(recalled(derived)).isNull();
        assertThat(recalled(legacy).accessLevel()).isEqualTo(MemoryAccessLevel.EXPLICIT);
        assertThat(recalled(summary).accessLevel()).isEqualTo(MemoryAccessLevel.EXPLICIT);
    }

    @Test void corruptCrossConversationAndCharacterEdgesFailClosed() {
        var source = memory(MemoryDetailLevel.PATTERN);
        var evidence = memory(MemoryDetailLevel.DETAILED);
        var a = Conversation.fresh(character, now);
        var b = Conversation.fresh(character, now);
        em.persist(a); em.persist(b);
        ReflectionTestUtils.setField(source, "conversation", a);
        ReflectionTestUtils.setField(evidence, "conversation", b);
        edge(source, evidence, MemoryDetailLevel.PATTERN);
        em.flush(); em.clear();
        assertThat(recall.recallForConversation(character.getId(), a.getId(), now, 20, "기밀"))
                .noneMatch(item -> item.memoryId().equals(source.getId()));
        var world = memory(MemoryDetailLevel.PATTERN);
        edge(world, evidence, MemoryDetailLevel.PATTERN);
        assertThat(recalled(world)).isNull();
        var otherCharacter = new Character("Other"); em.persist(otherCharacter);
        var foreign = new Memory(otherCharacter, null, "foreign", 9, 100, now); em.persist(foreign);
        var corrupted = memory(MemoryDetailLevel.PATTERN);
        edge(corrupted, foreign, MemoryDetailLevel.PATTERN);
        assertThat(recalled(corrupted)).isNull();
    }

    @Test void contextMasksRestrictedDerivedAndNeverReceivesBlockedDerived() {
        var source = memory(MemoryDetailLevel.PATTERN);
        var evidence = memory(MemoryDetailLevel.DETAILED);
        edge(source, evidence, MemoryDetailLevel.PATTERN);
        var builder = new AiContextBuilder(recall, mock(RelationshipRepository.class));
        var state = new CharacterState(character, 70, 70, now);
        var candidate = new EventCandidate(now, "DAILY", "scene", 1, true, StateDelta.none());
        for (var level : List.of(MemoryAccessLevel.IMPLICIT, MemoryAccessLevel.RESIDUAL, MemoryAccessLevel.BLOCKED)) {
            change(evidence, level);
            var context = builder.build(state, candidate);
            assertThat(context.memories()).noneMatch(item -> item.summary().contains("기밀"));
            if (level == MemoryAccessLevel.BLOCKED) assertThat(context.memories()).isEmpty();
            else assertThat(context.memories()).hasSize(2);
        }
    }

    @Test void derivedRestrictionBlocksSourceCandidateAndFinalCompressionPrompt() {
        var source = memory(MemoryDetailLevel.PATTERN);
        var evidence = memory(MemoryDetailLevel.DETAILED);
        var safe = memory(MemoryDetailLevel.SUMMARY);
        edge(source, evidence, MemoryDetailLevel.PATTERN);
        change(evidence, MemoryAccessLevel.BLOCKED);
        var finder = new MemoryPatternCandidateFinder(memories, access, policy);
        assertThat(finder.findRelated(safe)).extracting(Memory::getId).doesNotContain(source.getId(), evidence.getId());
        var compressor = mock(MemoryCompressor.class);
        var service = new MemoryCompressionService(new MemoryDecayPolicy(), compressor, finder,
                mock(MemoryCompressionPersistenceService.class), access, policy);
        assertThat(service.compressIfNeeded(source, now.plusYears(1)).compressed()).isFalse();
        verifyNoInteractions(compressor);
        var prompts = new MemoryCompressionPromptBuilder(access, policy);
        assertThat(prompts.prepare(source, MemoryDetailLevel.SCHEMA, List.of(safe, evidence))).isEmpty();
        assertThat(prompts.prepare(safe, MemoryDetailLevel.PATTERN, List.of(source))).isEmpty();
    }

    @Test void legacyCycleTerminatesAndPropagatesRestrictedLeaf() {
        var a = memory(MemoryDetailLevel.PATTERN);
        var b = memory(MemoryDetailLevel.PATTERN);
        var leaf = memory(MemoryDetailLevel.DETAILED);
        edge(a, b, MemoryDetailLevel.PATTERN);
        edge(b, a, MemoryDetailLevel.PATTERN);
        edge(b, leaf, MemoryDetailLevel.PATTERN);
        change(leaf, MemoryAccessLevel.RESIDUAL);
        assertThat(recalled(a).accessLevel()).isEqualTo(MemoryAccessLevel.RESIDUAL);
        assertThat(recalled(b).accessLevel()).isEqualTo(MemoryAccessLevel.RESIDUAL);
    }

    @Test void sameConversationRecoversThroughRealRecallAndBatchQueriesStayBounded() {
        var room = Conversation.fresh(character, now); em.persist(room);
        var source = memory(MemoryDetailLevel.PATTERN);
        var evidence = memory(MemoryDetailLevel.DETAILED);
        ReflectionTestUtils.setField(source, "conversation", room);
        ReflectionTestUtils.setField(evidence, "conversation", room);
        edge(source, evidence, MemoryDetailLevel.PATTERN);
        change(evidence, MemoryAccessLevel.BLOCKED);
        assertThat(recall.recallForConversation(character.getId(), room.getId(), now, 10, "기밀")).isEmpty();
        change(evidence, MemoryAccessLevel.EXPLICIT);
        assertThat(recall.recallForConversation(character.getId(), room.getId(), now, 10, "기밀"))
                .extracting(EffectiveMemory::memoryId).contains(source.getId());
        var edgeRepo = mock(MemoryCompressionEvidenceRepository.class);
        var accessRepo = mock(MemoryAccessStateRepository.class);
        var batchPolicy = new MemoryProvenanceAccessPolicy(edgeRepo, accessRepo);
        assertThat(batchPolicy.restrictions(java.util.stream.LongStream.rangeClosed(1, 300).boxed().toList())).isEmpty();
        verify(edgeRepo, times(2)).findAccessEdges(anyCollection());
        verifyNoInteractions(accessRepo);
    }
}
