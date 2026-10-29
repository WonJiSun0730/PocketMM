package com.example.projectmm.application.chat;

import com.example.projectmm.domain.chat.*;
import com.example.projectmm.domain.member.*;
import com.example.projectmm.domain.world.*;
import com.example.projectmm.infrastructure.security.PrivateDataAudit;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@DataJpaTest(properties={"spring.datasource.driver-class-name=org.h2.Driver","spring.jpa.hibernate.ddl-auto=create-drop","spring.jpa.show-sql=false"},showSql=false)
@Import(BugReportService.class)
class BugReportIntegrationTest {
    @DynamicPropertySource static void encryption(DynamicPropertyRegistry properties) {
        byte[] key=new byte[32];new java.security.SecureRandom().nextBytes(key);
        String encoded=Base64.getEncoder().encodeToString(key);
        properties.add("POCKETMM_DATA_ENCRYPTION_KEY",()->encoded);
        properties.add("pocketmm.encryption.required",()->true);
    }
    @Autowired BugReportService service;
    @Autowired BugReportRepository reports;
    @Autowired MemberRepository members;
    @Autowired EntityManager em;
    @MockitoBean WorldRoomSessionService sessions;
    @MockitoBean PrivateDataAudit audit;
    WorldRoomSession session;
    WorldRoomMessage reply;
    Member owner;
    Member admin;
    LocalDateTime now=LocalDateTime.of(2026,10,29,12,0);
    static UsernamePasswordAuthenticationToken auth(String name,String role) {
        return new UsernamePasswordAuthenticationToken(name,"",List.of(new SimpleGrantedAuthority("ROLE_"+role)));
    }
    Member member(String name,Role role) {
        var password=mock(PasswordEncoder.class);when(password.encode(anyString())).thenReturn("encoded");
        var member=Member.createLocal(name,"test-only",name,name+"@example.invalid",password);member.changeRole(role);
        return members.saveAndFlush(member);
    }
    WorldRoomSession pocket(Member member,String name) {
        var world=new World(member,name,"description");em.persist(world);
        var room=new WorldConversation(world,member,name,WorldConversation.Visibility.PRIVATE,now);em.persist(room);
        var intro=new WorldRoomIntro(room,"intro","start",0);em.persist(intro);
        var result=new WorldRoomSession(room,member,null,intro,now);em.persist(result);em.flush();return result;
    }
    @BeforeEach void setup() {
        owner=member("reporter",Role.USER);admin=member("operator",Role.ADMIN);member("other",Role.USER);
        session=pocket(owner,"private pocket");
        var input=new WorldRoomMessage(session,ChatRole.USER,"바로 앞 사용자 입력",now);em.persist(input);
        reply=new WorldRoomMessage(session,ChatRole.CHARACTER,"신고할 답변",now.plusSeconds(1));em.persist(reply);
        var variant=new WorldRoomMessageVariant(reply,1,"신고할 답변",now.plusSeconds(1));
        variant.captureEvaluationContext("{\"privateMemory\":\"DO_NOT_COPY_FULL_GENERATION\"}");em.persist(variant);em.flush();
        when(sessions.pocket(anyLong(),anyString())).thenAnswer(invocation->{
            if(session.getId().equals(invocation.getArgument(0)) && "reporter".equals(invocation.getArgument(1))) return session;
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        });
    }
    BugReportService.Submission submission(String id,boolean shared) {
        return new BugReportService.Submission(id,reply.getId(),1,BugReport.Category.OTHER,"버그 설명", "기대 결과",shared);
    }
    BugReportService.Receipt submit(boolean shared) {
        return service.submit(session.getId(),submission(UUID.randomUUID().toString(),shared),auth("reporter","USER"));
    }
    @Test void userSubmissionAppearsInOperatorInboxAndAllPrivateReportFieldsStayEncrypted() {
        var receipt=submit(true);em.flush();em.clear();
        for(String column:List.of("problem","expected","evidence_json"))
            assertThat((String)em.createNativeQuery("select "+column+" from bug_reports").getSingleResult()).startsWith("enc:v2:")
                    .doesNotContain("버그 설명","신고할 답변");
        assertThat((String)em.createNativeQuery("select content from world_room_messages order by id limit 1").getSingleResult()).startsWith("enc:v2:");
        var inbox=service.inbox(auth("operator","ADMIN"),null,null);
        assertThat(inbox).hasSize(1);assertThat(inbox.getFirst().getId()).isEqualTo(receipt.id());
        assertThat(new ObjectMapper().writeValueAsString(inbox)).doesNotContain("버그 설명","기대 결과","신고할 답변","evidenceJson");
        var detail=service.detail(auth("operator","ADMIN"),receipt.id());
        assertThat(detail.problem()).isEqualTo("버그 설명");
        assertThat(detail.evidenceJson()).contains("바로 앞 사용자 입력","신고할 답변").doesNotContain("DO_NOT_COPY_FULL_GENERATION");
        verify(audit).record(anyString(),eq("operator"),eq("REPORT_READ"),eq("BUG_REPORT"),eq(owner.getId()),eq(session.getId()),
                eq(receipt.id().toString()),anyString(),eq("SUCCESS"),eq(1));
    }
    @Test void contextIsNotCopiedWithoutConsentAndGeneralReportsNeedNoMessage() {
        var first=submit(false);
        assertThat(reports.findById(first.id()).orElseThrow().getEvidenceJson()).isNull();
        var general=new BugReportService.Submission(UUID.randomUUID().toString(),null,null,BugReport.Category.OTHER,"설정 버튼 오류",null,false);
        var receipt=service.submit(session.getId(),general,auth("reporter","USER"));
        var detail=service.detail(auth("operator","ADMIN"),receipt.id());
        assertThat(detail.messageId()).isNull();assertThat(detail.expected()).isEmpty();assertThat(detail.sharedContext()).isFalse();
    }
    @Test void otherUsersAnonymousAndStaleOperatorRolesCannotReadReportsOrSubmitForAnotherPocket() {
        var receipt=submit(false);
        assertThatThrownBy(()->service.submit(session.getId(),submission(UUID.randomUUID().toString(),false),auth("other","USER")))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(()->service.inbox(null,null,null)).hasMessageContaining("401");
        assertThatThrownBy(()->service.inbox(auth("other","USER"),null,null)).hasMessageContaining("403");
        assertThatThrownBy(()->service.detail(auth("reporter","USER"),receipt.id())).hasMessageContaining("403");
        assertThatThrownBy(()->service.updateStatus(auth("other","USER"),receipt.id(),BugReport.Status.RESOLVED)).hasMessageContaining("403");
        admin.changeRole(Role.USER);members.flush();
        assertThatThrownBy(()->service.detail(auth("operator","ADMIN"),receipt.id())).hasMessageContaining("403");
    }
    @Test void exactPocketVariantIsRequiredAndConsentCannotBeForgedForAGeneralReport() {
        var other=pocket(owner,"other pocket");
        var foreign=new WorldRoomMessage(other,ChatRole.CHARACTER,"다른 포켓 답변",now);em.persist(foreign);
        em.persist(new WorldRoomMessageVariant(foreign,1,"다른 포켓 답변",now));em.flush();
        var bad=new BugReportService.Submission(UUID.randomUUID().toString(),foreign.getId(),1,BugReport.Category.OTHER,"문제",null,true);
        assertThatThrownBy(()->service.submit(session.getId(),bad,auth("reporter","USER"))).hasMessageContaining("404");
        var general=new BugReportService.Submission(UUID.randomUUID().toString(),null,null,BugReport.Category.OTHER,"문제",null,true);
        assertThatThrownBy(()->service.submit(session.getId(),general,auth("reporter","USER"))).hasMessageContaining("400");
    }
    @Test void retryIsIdempotentAndHourlyQuotaIsEnforcedAfterExistingReceiptLookup() {
        String id=UUID.randomUUID().toString();var first=service.submit(session.getId(),submission(id,false),auth("reporter","USER"));
        assertThat(service.submit(session.getId(),submission(id.toUpperCase(Locale.ROOT),false),auth("reporter","USER")).id()).isEqualTo(first.id());
        assertThat(reports.count()).isEqualTo(1);
        for(int i=0;i<19;i++)reports.save(new BugReport(owner.getId(),UUID.randomUUID().toString(),session.getId(),null,null,
                BugReport.Category.OTHER,"synthetic quota entry","",null,LocalDateTime.now()));
        reports.flush();
        assertThatThrownBy(()->submit(false)).hasMessageContaining("429");
        assertThat(service.submit(session.getId(),submission(id,false),auth("reporter","USER")).id()).isEqualTo(first.id());
    }
    @Test void statusAndKeysetFiltersWorkWithoutExposingTextInList() {
        var first=submit(false);var second=submit(false);
        service.updateStatus(auth("operator","ADMIN"),second.id(),BugReport.Status.REVIEWING);
        assertThat(service.inbox(auth("operator","ADMIN"),null,BugReport.Status.NEW)).extracting(BugReportRepository.Metadata::getId).containsExactly(first.id());
        assertThat(service.inbox(auth("operator","ADMIN"),second.id(),null)).extracting(BugReportRepository.Metadata::getId).containsExactly(first.id());
        assertThat(service.detail(auth("operator","ADMIN"),second.id()).status()).isEqualTo(BugReport.Status.REVIEWING);
    }
    @Test void auditFailurePreventsDisclosureOfSubmittedContent() {
        var receipt=submit(true);
        doThrow(new IllegalStateException("audit unavailable")).when(audit).record(anyString(),anyString(),anyString(),anyString(),anyLong(),anyLong(),anyString(),anyString(),eq("SUCCESS"),anyInt());
        assertThatThrownBy(()->service.detail(auth("operator","ADMIN"),receipt.id())).hasMessageContaining("503");
    }
}
