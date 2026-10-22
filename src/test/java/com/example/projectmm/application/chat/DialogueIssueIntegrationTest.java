package com.example.projectmm.application.chat;

import com.example.projectmm.application.dialogue.*;
import com.example.projectmm.application.memory.MemoryRecallService;
import com.example.projectmm.application.time.TimeInterpreter;
import com.example.projectmm.domain.chat.*;
import com.example.projectmm.domain.dialogue.*;
import com.example.projectmm.domain.member.*;
import com.example.projectmm.domain.world.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@DataJpaTest
@Import({WorldRoomSessionService.class, WorldRoomContextBuilder.class, WorldRoomDialogueService.class, DialogueIssueService.class})
class DialogueIssueIntegrationTest {
    @org.junit.jupiter.api.BeforeEach
    void approveContinuityForExistingScenario() {
        com.example.projectmm.application.dialogue.SceneContinuityTestSupport.approveUnchanged(gateway);
    }

    @Autowired WorldRoomSessionService sessions;
    @Autowired WorldRoomDialogueService dialogue;
    @Autowired DialogueIssueService reports;
    @Autowired MemberRepository members;
    @Autowired WorldRepository worlds;
    @Autowired WorldConversationRepository rooms;
    @Autowired WorldRoomIntroRepository intros;
    @Autowired WorldRoomMessageRepository messages;
    @Autowired WorldRoomMessageVariantRepository variants;
    @Autowired DialogueIssueRepository issues;
    @Autowired EntityManager entityManager;
    @MockitoBean DialogueGateway gateway;
    @MockitoBean MemoryRecallService memories;
    @MockitoBean TimeInterpreter time;
    ObjectMapper mapper = new ObjectMapper();

    WorldRoomSession fixture() {
        var encoder = mock(PasswordEncoder.class); when(encoder.encode("password")).thenReturn("encoded");
        var member = members.save(Member.createLocal("tester", "password", "Tester", "tester@example.test", encoder));
        var world = worlds.save(new World(member, "World", "World description"));
        var now = LocalDateTime.of(2026, 10, 8, 15, 7);
        var room = rooms.save(new WorldConversation(world, member, "Room", WorldConversation.Visibility.PRIVATE, now));
        var intro = intros.save(new WorldRoomIntro(room, "Free", "Start", 0, now, "상담실", List.of("정상"), List.of("휴대폰")));
        return sessions.start(room.getId(), "tester", null, intro.getId());
    }
    WorldRoomMessage reply(Long sessionId) {
        return messages.findTop50BySessionIdOrderByIdDesc(sessionId).stream()
                .filter(message -> message.getRole() == ChatRole.CHARACTER).findFirst().orElseThrow();
    }

    @Test void savesExactVariantAndOriginalContextEvenAfterSettingsAndConversationAreDeleted() {
        var session = fixture(); Long sessionId = session.getId();
        when(gateway.generateWorldRoomTurn(any(), anyString())).thenReturn(new WorldRoomTurn("Original reply", WorldRoomCastChange.none(), List.of(), 60));
        dialogue.chat(sessionId, "*5분 기다렸다.*");
        var reply = reply(sessionId);
        when(gateway.generateWorldRoomTurn(any(), anyString())).thenReturn(new WorldRoomTurn("Rerolled reply", WorldRoomCastChange.none(), List.of(), 120));
        dialogue.reroll(sessionId, reply.getId());
        sessions.updatePocketSettings(sessionId, "tester", "later note", "later guide", true, "OPENAI", "later-model");
        var report = reports.save(sessionId, reply.getId(), 1, "tester", "PROSE", " 지문이 너무 짧음 ", " 행동과 대화가 연결된 장면 ");
        String saved = report.getEvidenceJson();
        var evidence = mapper.readTree(saved);
        assertThat(evidence.get("reply").asText()).isEqualTo("Original reply");
        assertThat(evidence.get("generationContextCaptured").asBoolean()).isTrue();
        assertThat(evidence.at("/generation/userInput").asText()).isEqualTo("*5분 기다렸다.*");
        assertThat(evidence.at("/generation/context/noControlPlayer").asBoolean()).isFalse();
        assertThat(evidence.at("/generation/context/aiProvider").asText()).isEqualTo("GEMINI");
        assertThat(evidence.at("/generation/effectiveModel").asText()).isNotBlank().isNotEqualTo("later-model");
        assertThat(evidence.get("beforeTime").asText()).isEqualTo("2026-10-08T15:07");
        assertThat(report.getProblem()).isEqualTo("지문이 너무 짧음");
        entityManager.flush(); entityManager.clear();
        sessions.deletePocket(sessionId, "tester"); entityManager.flush(); entityManager.clear();
        assertThat(reports.list("tester", null)).hasSize(1);
        assertThat(reports.list("tester", null).getFirst().getEvidenceJson()).isEqualTo(saved);
    }

    @Test void onlyOwnerCanSaveReadAndDeleteAndMessageMustBelongToPocket() {
        var session = fixture();
        var encoder = mock(PasswordEncoder.class); when(encoder.encode("password")).thenReturn("encoded");
        members.save(Member.createLocal("other", "password", "Other", "other@example.test", encoder));
        when(gateway.generateWorldRoomTurn(any(), anyString())).thenReturn(new WorldRoomTurn("Reply", WorldRoomCastChange.none(), List.of(), 0));
        dialogue.chat(session.getId(), "Hello"); var reply = reply(session.getId());
        assertThatThrownBy(() -> reports.save(session.getId(), reply.getId(), 1, "other", "OTHER", "problem", "expected"))
                .isInstanceOf(ResponseStatusException.class);
        var report = reports.save(session.getId(), reply.getId(), 1, "tester", "OTHER", "problem", "expected");
        assertThat(reports.list("other", null)).isEmpty();
        assertThatThrownBy(() -> reports.delete(report.getId(), "other")).isInstanceOf(ResponseStatusException.class);
        var another = sessions.start(session.getRoom().getId(), "tester", null, session.getIntro().getId());
        assertThatThrownBy(() -> reports.save(another.getId(), reply.getId(), 1, "tester", "OTHER", "problem", "expected"))
                .isInstanceOf(ResponseStatusException.class);
        reports.delete(report.getId(), "tester"); entityManager.flush();
        assertThat(reports.list("tester", null)).isEmpty();
        assertThat(messages.findById(reply.getId())).isPresent();
    }

    @Test void legacyAnswerUsesClearlyMarkedReferenceInsteadOfInventingOldSettings() {
        var session = fixture();
        when(gateway.generateWorldRoomTurn(any(), anyString())).thenReturn(new WorldRoomTurn("Old reply", WorldRoomCastChange.none(), List.of(), 0));
        dialogue.chat(session.getId(), "Original input"); var reply = reply(session.getId());
        variants.findByMessageIdAndVariantNo(reply.getId(), 1).orElseThrow().captureEvaluationContext(null);
        var report = reports.save(session.getId(), reply.getId(), 1, "tester", "OTHER", "problem", "expected");
        var evidence = mapper.readTree(report.getEvidenceJson());
        assertThat(evidence.get("generationContextCaptured").asBoolean()).isFalse();
        assertThat(evidence.at("/generation/inputIsReference").asBoolean()).isTrue();
        assertThat(evidence.at("/generation/userInput").asText()).isEqualTo("Original input");
        assertThat(evidence.at("/generation/context").isMissingNode()).isTrue();
    }
}
