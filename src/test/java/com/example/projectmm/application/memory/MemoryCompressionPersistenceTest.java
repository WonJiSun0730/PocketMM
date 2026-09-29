package com.example.projectmm.application.memory;

import com.example.projectmm.domain.character.Character;
import com.example.projectmm.domain.memory.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DataJpaTest(properties = {"spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop", "spring.jpa.show-sql=false"}, showSql = false)
@Import({MemoryCompressionService.class, MemoryCompressionPersistenceService.class, MemoryDecayPolicy.class, MemoryProvenanceAccessPolicy.class,ProtectedMemoryDetailPolicy.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class MemoryCompressionPersistenceTest {
    @Autowired MemoryCompressionService service;
    @Autowired MemoryCompressionPersistenceService persistence;
    @Autowired MemoryRepository memories;
    @Autowired MemoryAccessStateRepository access;
    @Autowired EntityManager em;
    @Autowired ProtectedMemoryDetailRepository protectedDetails;
    @Autowired PlatformTransactionManager transactions;
    @MockitoSpyBean MemoryCompressionEvidenceRepository provenance;
    @MockitoBean MemoryCompressor compressor;
    @MockitoBean MemoryPatternCandidateFinder candidates;
    private final LocalDateTime at = LocalDateTime.of(2026, 1, 1, 12, 0);

    @Test
    void accessChangedDuringCompressionRejectsPersistence() {
        var memory = seed(MemoryDetailLevel.SUMMARY);
        var evidence = seed(MemoryDetailLevel.DETAILED);
        when(candidates.findRelated(any())).thenReturn(List.of(evidence));
        when(compressor.compress(any(), any(), any())).thenAnswer(invocation -> {
            access.saveAndFlush(new MemoryAccessState(evidence, MemoryAccessLevel.BLOCKED, at.plusYears(2)));
            return new MemoryCompressorOutput("must not persist", List.of(evidence.getId()));
        });
        assertThat(service.compressIfNeeded(memory, at.plusYears(2)).compressed()).isFalse();
        assertUnchanged(memory, MemoryDetailLevel.SUMMARY);
    }

    @Test
    void protectedDetailAddedDuringCompressionRejectsPersistence() {

        var memory =
                seed(MemoryDetailLevel.DETAILED);

        when(
                compressor.compress(
                        any(),
                        any(),
                        any()
                )
        ).thenAnswer(invocation -> {

            new TransactionTemplate(transactions)
                    .executeWithoutResult(status ->
                            protectedDetails.saveAndFlush(
                                    new ProtectedMemoryDetail(
                                            memories.findById(
                                                    memory.getId()
                                            ).orElseThrow(),
                                            "왼손에 반지를 끼워줬다",
                                            MemoryDetailSalience.HIGH,
                                            false
                                    )
                            )
                    );

            return new MemoryCompressorOutput(
                    "중요한 사건이었다.",
                    List.of()
            );
        });

        assertThat(
                service.compressIfNeeded(
                        memory,
                        at.plusYears(2)
                ).compressed()
        ).isFalse();

        assertUnchanged(
                memory,
                MemoryDetailLevel.DETAILED
        );
    }

    @Test
    void persistenceAcceptsSummaryThatPreservesProtectedDetail() {

        var memory =
                seed(MemoryDetailLevel.DETAILED);

        new TransactionTemplate(transactions)
                .executeWithoutResult(status ->
                        protectedDetails.saveAndFlush(
                                new ProtectedMemoryDetail(
                                        memories.findById(
                                                memory.getId()
                                        ).orElseThrow(),
                                        "왼손에 반지를 끼워줬다",
                                        MemoryDetailSalience.HIGH,
                                        false
                                )
                        )
                );

        when(
                compressor.compress(
                        any(),
                        any(),
                        any()
                )
        ).thenReturn(
                new MemoryCompressorOutput(
                        "그는 왼손에 반지를 끼워줬다.",
                        List.of()
                )
        );

        assertThat(
                service.compressIfNeeded(
                        memory,
                        at.plusYears(2)
                ).compressed()
        ).isTrue();

        var stored =
                memories.findById(
                        memory.getId()
                ).orElseThrow();

        assertThat(stored.getSummary())
                .contains(
                        "왼손에 반지를 끼워줬다"
                );

        assertThat(stored.getDetailLevel())
                .isEqualTo(
                        MemoryDetailLevel.SUMMARY
                );
    }

    @Test
    void summaryPersistsFromReadOnlyCallerWithoutProvenance() {
        var memory = seed(MemoryDetailLevel.DETAILED);
        var read = new TransactionTemplate(transactions);
        read.setReadOnly(true);
        when(compressor.compress(any(), any(), any())).thenReturn(new MemoryCompressorOutput("summary", List.of()));
        Boolean compressed = read.execute(status -> service.compressIfNeeded(memory, at.plusYears(2)).compressed());
        assertThat(compressed).isTrue();
        assertThat(memories.findById(memory.getId()).orElseThrow().getDetailLevel()).isEqualTo(MemoryDetailLevel.SUMMARY);
        assertThat(rows(memory, MemoryDetailLevel.SUMMARY)).isEmpty();
    }

    @Test
    void patternStoresOnlyOutputEvidenceAndPreservesIndependentFields() {
        var memory = seed(MemoryDetailLevel.SUMMARY);
        var used = seed(MemoryDetailLevel.DETAILED);
        var excluded = seed(MemoryDetailLevel.DETAILED);
        stub(List.of(used, excluded), new MemoryCompressorOutput("pattern", List.of(used.getId())));
        assertThat(service.compressIfNeeded(memory, at.plusYears(2)).compressed()).isTrue();
        assertThat(rows(memory, MemoryDetailLevel.PATTERN)).containsExactly(used.getId());
        var stored = memories.findById(memory.getId()).orElseThrow();
        assertThat(stored.getSummary()).isEqualTo("pattern");
        assertThat(stored.getImportance()).isEqualTo(5);
        assertThat(stored.getNarrativeAnchor()).isEqualTo(10);
        assertThat(stored.getStrength()).isEqualTo(50);
        assertThat(stored.getRememberedAt()).isEqualTo(at);
    }

    @Test
    void schemaStoresMultipleDistinctEvidenceAndReapplyingDoesNotDuplicate() {
        var memory = seed(MemoryDetailLevel.PATTERN);
        var a = seed(MemoryDetailLevel.SUMMARY);
        var b = seed(MemoryDetailLevel.DETAILED);
        var output = new MemoryCompressorOutput("schema", List.of(a.getId(), a.getId(), b.getId()));
        stub(List.of(a, b), output);
        assertThat(service.compressIfNeeded(memory, at.plusYears(2)).compressed()).isTrue();
        assertThat(persistence.apply(memory.getId(), MemoryDetailLevel.PATTERN, "original", output)).isFalse();
        assertThat(rows(memory, MemoryDetailLevel.SCHEMA)).containsExactlyInAnyOrder(a.getId(), b.getId());
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            assertThat(provenance.findByMemoryIdAndTargetLevel(memory.getId(), MemoryDetailLevel.SCHEMA))
                    .extracting(MemoryCompressionEvidence::getEvidenceLevel)
                    .containsExactlyInAnyOrder(MemoryDetailLevel.SUMMARY, MemoryDetailLevel.DETAILED);
        });
    }

    @Test
    void existingRowIsReusedInsteadOfInsertingDuplicate() {
        var memory = seed(MemoryDetailLevel.SUMMARY);
        var evidence = seed(MemoryDetailLevel.DETAILED);
        provenance.saveAndFlush(new MemoryCompressionEvidence(memory, MemoryDetailLevel.PATTERN, evidence));
        stub(List.of(evidence), new MemoryCompressorOutput("pattern", List.of(evidence.getId())));
        assertThat(service.compressIfNeeded(memory, at.plusYears(2)).compressed()).isTrue();
        assertThat(rows(memory, MemoryDetailLevel.PATTERN)).containsExactly(evidence.getId());
    }

    @Test
    void missingAndSelfEvidenceRejectWholeResult() {
        var memory = seed(MemoryDetailLevel.SUMMARY);
        var evidence = seed(MemoryDetailLevel.DETAILED);
        for (Long invalid : List.of(Long.MAX_VALUE, memory.getId())) {
            stub(List.of(evidence), new MemoryCompressorOutput("pattern", List.of(evidence.getId(), invalid)));
            assertThat(service.compressIfNeeded(memory, at.plusYears(2)).compressed()).isFalse();
        }
        assertUnchanged(memory, MemoryDetailLevel.SUMMARY);
    }

    @Test
    void directEvidenceCycleRejectsWholeCompression() {
        var memory = seed(MemoryDetailLevel.SUMMARY);
        var evidence = seed(MemoryDetailLevel.SUMMARY);

        provenance.saveAndFlush(
                new MemoryCompressionEvidence(
                        evidence,
                        MemoryDetailLevel.PATTERN,
                        memory
                )
        );

        stub(
                List.of(evidence),
                new MemoryCompressorOutput(
                        "pattern",
                        List.of(evidence.getId())
                )
        );

        assertThat(
                service.compressIfNeeded(
                        memory,
                        at.plusYears(2)
                ).compressed()
        ).isFalse();

        assertUnchanged(memory, MemoryDetailLevel.SUMMARY);

        assertThat(
                rows(memory, MemoryDetailLevel.PATTERN)
        ).isEmpty();
    }

    @Test
    void outputEvidenceGateStopsFallbackAndInsufficientSchema() {
        var evidence = seed(MemoryDetailLevel.DETAILED);
        var second = seed(MemoryDetailLevel.DETAILED);
        for (var level : List.of(MemoryDetailLevel.SUMMARY, MemoryDetailLevel.PATTERN)) {
            var memory = seed(level);
            stub(List.of(evidence, second), new RuleBasedMemoryCompressor().compress(memory, level.next(), List.of(evidence)));
            assertThat(service.compressIfNeeded(memory, at.plusYears(2)).compressed()).isFalse();
            if (level == MemoryDetailLevel.PATTERN) {
                stub(List.of(evidence, second), new MemoryCompressorOutput("schema", List.of(evidence.getId())));
                assertThat(service.compressIfNeeded(memory, at.plusYears(2)).compressed()).isFalse();
            }
            assertUnchanged(memory, level);
        }
    }

    @Test
    void invalidOutputDoesNotWriteAnything() {
        var memory = seed(MemoryDetailLevel.SUMMARY);
        var evidence = seed(MemoryDetailLevel.DETAILED);
        for (String text : new String[]{null, "", "x".repeat(501)}) {
            stub(List.of(evidence), text == null ? null : new MemoryCompressorOutput(text, List.of(evidence.getId())));
            assertThat(service.compressIfNeeded(memory, at.plusYears(2)).compressed()).isFalse();
        }
        assertUnchanged(memory, MemoryDetailLevel.SUMMARY);
    }

    @Test
    void failureAfterProvenanceInsertRollsBackMemoryAndRows() {
        var memory = seed(MemoryDetailLevel.SUMMARY);
        var evidence = seed(MemoryDetailLevel.DETAILED);
        stub(List.of(evidence), new MemoryCompressorOutput("pattern", List.of(evidence.getId())));
        doAnswer(call -> {
            Iterable<MemoryCompressionEvidence> rows = call.getArgument(0);
            rows.forEach(em::persist);
            em.flush();
            throw new IllegalStateException("simulated storage failure after flush");
        }).when(provenance).saveAllAndFlush(any());
        assertThatThrownBy(() -> service.compressIfNeeded(memory, at.plusYears(2))).isInstanceOf(IllegalStateException.class);
        assertUnchanged(memory, MemoryDetailLevel.SUMMARY);
    }

    private Character fixtureCharacter;
    private Memory seed(MemoryDetailLevel level) {
        return new TransactionTemplate(transactions).execute(status -> {
            if (fixtureCharacter == null) {
                fixtureCharacter = new Character("NPC");
                em.persist(fixtureCharacter);
            }
            var character = em.getReference(Character.class, fixtureCharacter.getId());
            var memory = new Memory(character, null, "original", 5, 50, 10, at);
            while (memory.getDetailLevel() != level) memory.compress("original");
            em.persist(memory);
            return memory;
        });
    }

    private void stub(List<Memory> related, MemoryCompressorOutput output) {
        when(candidates.findRelated(any())).thenReturn(related);
        when(compressor.compress(any(), any(), any())).thenReturn(output);
    }

    private List<Long> rows(Memory memory, MemoryDetailLevel target) {
        return new TransactionTemplate(transactions).execute(status -> provenance
                .findByMemoryIdAndTargetLevel(memory.getId(), target).stream()
                .map(row -> row.getEvidenceMemory().getId()).toList());
    }

    private void assertUnchanged(Memory memory, MemoryDetailLevel level) {
        assertThat(memory.getSummary()).isEqualTo("original");
        var stored = memories.findById(memory.getId()).orElseThrow();
        assertThat(stored.getSummary()).isEqualTo("original");
        assertThat(stored.getDetailLevel()).isEqualTo(level);
        assertThat(rows(memory, level.next())).isEmpty();
    }
}
