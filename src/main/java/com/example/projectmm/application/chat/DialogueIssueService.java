package com.example.projectmm.application.chat;

import com.example.projectmm.domain.chat.*;
import com.example.projectmm.domain.member.MemberRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.data.domain.PageRequest;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.JsonNode;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class DialogueIssueService {
    private final WorldRoomSessionService sessions;
    private final WorldRoomMessageVariantRepository variants;
    private final DialogueIssueRepository issues;
    private final MemberRepository members;
    private final WorldRoomMessageRepository messages;
    private final WorldRoomVariantCastSnapshotRepository castSnapshots;
    private final WorldRoomVariantSystemSnapshotRepository systemSnapshots;
    private final ObjectMapper mapper = new ObjectMapper();
    public DialogueIssueService(WorldRoomSessionService sessions, WorldRoomMessageVariantRepository variants,
                                DialogueIssueRepository issues, MemberRepository members, WorldRoomMessageRepository messages,
                                WorldRoomVariantCastSnapshotRepository castSnapshots, WorldRoomVariantSystemSnapshotRepository systemSnapshots) {
        this.sessions = sessions; this.variants = variants; this.issues = issues; this.members = members; this.messages = messages;
        this.castSnapshots = castSnapshots; this.systemSnapshots = systemSnapshots;
    }

    public record Evidence(int schemaVersion, boolean generationContextCaptured, JsonNode generation,
            String reply, String beforeTime, String afterTime, JsonNode beforePlayerState, JsonNode afterPlayerState,
            JsonNode sceneInstruction, List<CastEvidence> cast, List<SystemEvidence> systems) {}
    public record CastEvidence(Long characterId, boolean beforeActive, boolean afterActive,
            String beforeEnteredAt, String beforeLeftAt, String afterEnteredAt, String afterLeftAt) {}
    public record SystemEvidence(Long systemId, int beforeValue, int afterValue) {}

    @Transactional
    public DialogueIssue save(Long sessionId, Long messageId, int variantNo, String loginId,
                              String category, String problem, String expected) {
        var session = sessions.pocket(sessionId, loginId);
        var variant = variants.findByMessageIdAndVariantNo(messageId, variantNo)
                .filter(v -> v.getMessage().getSession().getId().equals(sessionId)
                        && v.getMessage().getRole() == ChatRole.CHARACTER)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        var evidence = new Evidence(1, variant.getEvaluationContextJson() != null,
                variant.getEvaluationContextJson() == null ? legacyReference(sessionId, messageId) : json(variant.getEvaluationContextJson()), variant.getContent(),
                text(variant.getBeforeSimulatedAt()), text(variant.getAfterSimulatedAt()),
                json(variant.getBeforePlayerStateJson()), json(variant.getAfterPlayerStateJson()),
                json(variant.getUserSceneSetupJson()),
                castSnapshots.findByVariantIdOrderByCharacterIdAsc(variant.getId()).stream()
                        .map(s -> new CastEvidence(s.getCharacterId(), s.isBeforeActive(), s.isAfterActive(),
                                text(s.getBeforeEnteredAt()), text(s.getBeforeLeftAt()), text(s.getAfterEnteredAt()), text(s.getAfterLeftAt()))).toList(),
                systemSnapshots.findByVariantIdOrderBySystemIdAsc(variant.getId()).stream()
                        .map(s -> new SystemEvidence(s.getSystemId(), s.getBeforeValue(), s.getAfterValue())).toList());
        return issues.save(new DialogueIssue(session.getMember().getId(), sessionId, messageId, variantNo,
                category, problem.trim(), expected.trim(), mapper.writeValueAsString(evidence), LocalDateTime.now()));
    }
    @Transactional(readOnly = true)
    public List<DialogueIssue> list(String loginId, Long before) {
        var owner = members.findByLoginId(loginId).orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        return issues.findByOwnerMemberIdAndIdLessThanOrderByIdDesc(owner.getId(),
                before == null ? Long.MAX_VALUE : before, PageRequest.of(0, 100));
    }
    @Transactional
    public void delete(Long id, String loginId) {
        var owner = members.findByLoginId(loginId).orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        var issue = issues.findById(id).filter(item -> item.getOwnerMemberId().equals(owner.getId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        issues.delete(issue);
    }
    private JsonNode json(String value) { return value == null ? null : mapper.readTree(value); }
    private JsonNode legacyReference(Long sessionId, Long messageId) {
        var previous = messages.findTopBySessionIdAndIdLessThanOrderByIdDesc(sessionId, messageId);
        String input = previous.filter(message -> message.getRole() == ChatRole.USER)
                .map(WorldRoomMessage::getContent).orElse("[이전 자동 전개 또는 입력 미저장]");
        var history = messages.findTop15BySessionIdAndIdLessThanOrderByIdDesc(sessionId, messageId).stream()
                .limit(6).sorted(java.util.Comparator.comparing(WorldRoomMessage::getId))
                .map(message -> new com.example.projectmm.domain.dialogue.DialogueMessageContext(
                        message.getRole(), message.getContent(), message.getCreatedAt())).toList();
        return mapper.valueToTree(java.util.Map.of("operation", "LEGACY_REFERENCE", "userInput", input,
                "inputIsReference", true, "historyAtReportTime", history));
    }
    private String text(LocalDateTime value) { return value == null ? null : value.toString(); }
}
