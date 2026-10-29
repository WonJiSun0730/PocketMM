package com.example.projectmm.application.chat;

import com.example.projectmm.domain.chat.*;
import com.example.projectmm.domain.member.*;
import com.example.projectmm.infrastructure.security.PrivateDataAudit;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class BugReportService {
    public record Submission(String requestId, Long messageId, Integer variantNo, BugReport.Category category,
                             String problem, String expected, boolean shareContext) {}
    public record Receipt(Long id, BugReport.Status status, LocalDateTime createdAt) {
        static Receipt from(BugReport report) { return new Receipt(report.getId(),report.getStatus(),report.getCreatedAt()); }
    }
    public record Detail(Long id, Long ownerMemberId, Long sessionId, Long messageId, Integer variantNo,
                         BugReport.Category category, BugReport.Status status, String problem, String expected,
                         String evidenceJson, boolean sharedContext, LocalDateTime createdAt, LocalDateTime updatedAt) {
        static Detail from(BugReport r) { return new Detail(r.getId(),r.getOwnerMemberId(),r.getSessionId(),r.getMessageId(),
                r.getVariantNo(),r.getCategory(),r.getStatus(),r.getProblem(),r.getExpected(),r.getEvidenceJson(),
                r.isSharedContext(),r.getCreatedAt(),r.getUpdatedAt()); }
    }
    public record Evidence(int schemaVersion, String userInputAtReportTime, String selectedReply, boolean truncated) {}
    private final WorldRoomSessionService sessions;
    private final WorldRoomMessageVariantRepository variants;
    private final WorldRoomMessageRepository messages;
    private final BugReportRepository reports;
    private final MemberRepository members;
    private final PrivateDataAudit audit;
    private final ObjectMapper json = new ObjectMapper();

    public BugReportService(WorldRoomSessionService sessions, WorldRoomMessageVariantRepository variants,
                            WorldRoomMessageRepository messages, BugReportRepository reports,
                            MemberRepository members, PrivateDataAudit audit) {
        this.sessions=sessions; this.variants=variants; this.messages=messages; this.reports=reports;
        this.members=members; this.audit=audit;
    }

    @Transactional
    public Receipt submit(Long sessionId, Submission request, Authentication auth) {
        String login=login(auth);
        validate(request);
        var session=sessions.pocket(sessionId,login); // never accept a client-supplied owner
        var member=members.lockForBugReport(login).orElseThrow(()->new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        String requestId=UUID.fromString(request.requestId()).toString();
        var existing=reports.findByOwnerMemberIdAndRequestId(member.getId(),requestId);
        if(existing.isPresent()) {
            if(!existing.get().getSessionId().equals(sessionId)) throw new ResponseStatusException(HttpStatus.CONFLICT);
            return Receipt.from(existing.get()); // retries cannot create duplicate reports
        }
        if(reports.countByOwnerMemberIdAndCreatedAtAfter(member.getId(),LocalDateTime.now().minusHours(1))>=20)
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,"한 시간에 최대 20건까지 신고할 수 있어요.");
        String evidence=null;
        if(request.messageId()!=null) {
            var variant=variants.findByMessageIdAndVariantNo(request.messageId(),request.variantNo())
                    .filter(v->v.getMessage().getSession().getId().equals(sessionId) && v.getMessage().getRole()==ChatRole.CHARACTER)
                    .orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND));
            if(request.shareContext()) {
                String input=messages.findTopBySessionIdAndIdLessThanOrderByIdDesc(sessionId,request.messageId())
                        .filter(m->m.getRole()==ChatRole.USER).map(WorldRoomMessage::getContent).orElse("");
                String reply=variant.getContent();
                evidence=json.writeValueAsString(new Evidence(1,clip(input,2000),clip(reply,12000),
                        input.length()>2000 || reply.length()>12000));
            }
        }
        var saved=reports.saveAndFlush(new BugReport(session.getMember().getId(),requestId,sessionId,
                request.messageId(),request.variantNo(),request.category(),request.problem().strip(),
                request.expected()==null?"":request.expected().strip(),evidence,LocalDateTime.now()));
        return Receipt.from(saved);
    }

    @Transactional(readOnly=true)
    public List<BugReportRepository.Metadata> inbox(Authentication auth, Long before, BugReport.Status status) {
        operator(auth);
        if(before!=null && before<=0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        String id=UUID.randomUUID().toString();
        log(id,auth,"REPORT_LIST",0,0,"","REQUESTED",0);
        try {
            var result=reports.inbox(before==null?Long.MAX_VALUE:before,status,PageRequest.of(0,50));
            log(id,auth,"REPORT_LIST",0,0,"","SUCCESS",result.size());
            return result;
        } catch(RuntimeException ex) {
            log(id,auth,"REPORT_LIST",0,0,"","FAILED",0);
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"신고함을 불러오지 못했어요.");
        }
    }

    @Transactional(readOnly=true)
    public Detail detail(Authentication auth, Long reportId) {
        operator(auth);
        String id=UUID.randomUUID().toString();
        log(id,auth,"REPORT_READ",0,0,reportId.toString(),"REQUESTED",0);
        try {
            var report=reports.findById(reportId).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND));
            log(id,auth,"REPORT_READ",report.getOwnerMemberId(),report.getSessionId(),reportId.toString(),"SUCCESS",1);
            return Detail.from(report);
        } catch(RuntimeException ex) {
            log(id,auth,"REPORT_READ",0,0,reportId.toString(),"FAILED",0);
            if(ex instanceof ResponseStatusException status) throw status;
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"신고 내용을 불러오지 못했어요.");
        }
    }

    @Transactional
    public void updateStatus(Authentication auth, Long reportId, BugReport.Status status) {
        var operator=operator(auth);
        if(status==null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        String id=UUID.randomUUID().toString();
        log(id,auth,"REPORT_STATUS",0,0,reportId.toString(),"REQUESTED",0);
        try {
            var report=reports.lockById(reportId).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND));
            report.updateStatus(status,operator.getId(),LocalDateTime.now());
            reports.flush();
            log(id,auth,"REPORT_STATUS",report.getOwnerMemberId(),report.getSessionId(),reportId.toString(),"SUCCESS",1);
        } catch(RuntimeException ex) {
            log(id,auth,"REPORT_STATUS",0,0,reportId.toString(),"FAILED",0);
            if(ex instanceof ResponseStatusException value) throw value;
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"신고 상태를 바꾸지 못했어요.");
        }
    }

    private Member operator(Authentication auth) {
        String name=login(auth);
        var member=members.findByLoginId(name).orElseThrow(()->new ResponseStatusException(HttpStatus.FORBIDDEN));
        boolean role=auth.getAuthorities().stream().anyMatch(a->Set.of("ROLE_ADMIN","ROLE_DEVELOPER").contains(a.getAuthority()));
        if(!role || !Set.of(Role.ADMIN,Role.DEVELOPER).contains(member.getRole())) {
            try {log(UUID.randomUUID().toString(),auth,"REPORT_ACCESS",0,0,"","DENIED",0);}
            catch(RuntimeException ignored) { /* Remain forbidden when audit storage is unavailable. */ }
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        return member;
    }
    private String login(Authentication auth) {
        if(auth==null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken)
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        return auth.getName();
    }
    private void log(String id,Authentication auth,String action,long owner,long scope,String ids,String outcome,int count) {
        audit.record(id,auth.getName(),action,"BUG_REPORT",owner,scope,ids,"User-submitted bug report support",outcome,count);
    }
    private void validate(Submission r) {
        if(r==null || r.requestId()==null || !r.requestId().matches("[0-9a-fA-F]{8}(-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}")
                || r.category()==null || r.problem()==null || r.problem().isBlank() || r.problem().length()>5000
                || (r.expected()!=null && r.expected().length()>5000)
                || ((r.messageId()==null)!=(r.variantNo()==null))
                || (r.messageId()!=null && (r.messageId()<=0 || r.variantNo()<=0))
                || (r.shareContext() && r.messageId()==null))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
    }
    private String clip(String value,int max) { return value.length()<=max?value:value.substring(0,max); }
}
