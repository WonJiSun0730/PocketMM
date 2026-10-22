package com.example.projectmm.qa;

import com.example.projectmm.application.chat.WorldRoomDialogueService;
import com.example.projectmm.application.dialogue.DialogueGateway;
import com.example.projectmm.application.memory.PocketMemoryInspectorService;
import com.example.projectmm.domain.chat.*;
import com.example.projectmm.domain.dialogue.*;
import com.example.projectmm.infrastructure.dialogue.AiUsageTracker;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.*;

/** Adapter to the actual transactional chat service and AFTER_COMMIT memory listener. */
public final class QaPipeline implements QaRunner.Target {
    private final QaSpec spec;
    private final long sessionId;
    private final WorldRoomDialogueService dialogue;
    private final WorldRoomMessageRepository messages;
    private final WorldRoomMessageVariantRepository variants;
    private final PocketMemoryInspectorService inspector;
    private final AiUsageTracker usage;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final ObjectMapper mapper=new ObjectMapper();
    public QaPipeline(QaSpec spec,long sessionId,WorldRoomDialogueService dialogue,
                      WorldRoomMessageRepository messages,WorldRoomMessageVariantRepository variants,
                      PocketMemoryInspectorService inspector,AiUsageTracker usage,JdbcTemplate jdbc,TransactionTemplate transactions) {
        this.spec=spec;this.sessionId=sessionId;this.dialogue=dialogue;this.messages=messages;this.variants=variants;
        this.inspector=inspector;this.usage=usage;this.jdbc=jdbc;this.transactions=transactions;
        String url=jdbc.execute((java.sql.Connection c)->c.getMetaData().getURL());
        if(url==null||!url.startsWith("jdbc:h2:")||!url.contains("pocket-qa"))throw new IllegalStateException("Only isolated pocket-qa H2 database permitted");
        jdbc.execute("CREATE TABLE IF NOT EXISTS qa_turn_receipts(request_id VARCHAR(120) PRIMARY KEY, session_id BIGINT NOT NULL, input_hash VARCHAR(64) NOT NULL, reply_message_id BIGINT, result_json LONGTEXT)");
    }
    @Override public QaRunner.Outcome send(String requestId,String input) {
        var hashes=jdbc.queryForList("SELECT input_hash FROM qa_turn_receipts WHERE request_id=?",requestId);
        if(!hashes.isEmpty()&&!hash(input).equals(hashes.getFirst().get("INPUT_HASH")))
            throw new IllegalArgumentException("Idempotency key reused with different input");
        var previous=recover(requestId);
        if(previous.isPresent())return previous.get();
        var before=usage.calls(sessionId);
        try {
            Long replyId=transactions.execute(status->{
                jdbc.update("INSERT INTO qa_turn_receipts(request_id,session_id,input_hash) VALUES(?,?,?)",requestId,sessionId,hash(input));
                dialogue.chat(sessionId,input);
                long reply=messages.findTop50BySessionIdOrderByIdDesc(sessionId).stream().filter(m->m.getRole()==ChatRole.CHARACTER).findFirst().orElseThrow().getId();
                jdbc.update("UPDATE qa_turn_receipts SET reply_message_id=? WHERE request_id=?",reply,requestId);
                return reply;
            });
            // AFTER_COMMIT extraction has completed before execute returns; reuse durable variant evidence.
            var result=transactions.execute(status->snapshot(callsSince(before,usage.calls(sessionId),spec,"TARGET"),replyId));
            jdbc.update("UPDATE qa_turn_receipts SET result_json=? WHERE request_id=? AND session_id=?",mapper.writeValueAsString(result),requestId,sessionId);
            return result;
        } catch(RuntimeException ex) {
            var partial=new ArrayList<>(callsSince(before,usage.calls(sessionId),spec,"TARGET"));
            partial.add(new QaRunner.Usage("TARGET",spec.targetModel(),"UNCERTAIN_FAILURE",0,0,null,0,"UNKNOWN",null));
            String details=mapper.writeValueAsString(failureDetails(ex));
            return new QaRunner.Outcome("",List.of(),null,details,null,null,partial,
                    "Target failed; inspect failedOutcome.diagnosticsJson. Usage may be incomplete. No automatic retry.");
        }
    }
    // Do not persist arbitrary provider exception messages: they may contain credentials or prompts.
    static Map<String,Object> failureDetails(Throwable error) {
        var chain=new ArrayList<Map<String,Object>>();
        var seen=Collections.newSetFromMap(new IdentityHashMap<Throwable,Boolean>());
        for(Throwable e=error;e!=null&&chain.size()<16&&seen.add(e);e=e.getCause()) {
            var item=new LinkedHashMap<String,Object>();
            item.put("exceptionType",e.getClass().getName());
            if(e instanceof com.example.projectmm.application.dialogue.SceneContinuityService.ReviewRejectedException rejected)
                item.put("continuityReview",rejected.review());
            item.put("frames",Arrays.stream(e.getStackTrace())
                    .filter(f->f.getClassName().startsWith("com.example.projectmm."))
                    .limit(8).map(StackTraceElement::toString).toList());
            String message=e.getMessage();
            boolean localValidation=Arrays.stream(e.getStackTrace()).findFirst()
                    .map(f->f.getClassName().equals("com.example.projectmm.domain.dialogue.SceneFacts")
                            ||f.getClassName().equals("com.example.projectmm.application.dialogue.SceneContinuityService"))
                    .orElse(false);
            if(localValidation&&message!=null&&message.length()<=200)
                item.put("validationReason",message);
            chain.add(item);
        }
        return Map.of("phase","TARGET_LOCAL_FAILURE","causes",chain);
    }
    @Override public Optional<QaRunner.Outcome> recover(String requestId) {
        var rows=jdbc.queryForList("SELECT session_id,reply_message_id,result_json FROM qa_turn_receipts WHERE request_id=?",requestId);
        if(rows.isEmpty())return Optional.empty();
        var row=rows.getFirst();
        if(((Number)row.get("SESSION_ID")).longValue()!=sessionId)throw new IllegalStateException("Receipt belongs to another QA session");
        var json=row.get("RESULT_JSON");
        if(json!=null)return Optional.of(mapper.readValue(json.toString(),QaRunner.Outcome.class));
        // A committed chat must never be resent just because file finalization was interrupted.
        return Optional.of(transactions.execute(status->snapshot(List.of(new QaRunner.Usage("TARGET",spec.targetModel(),
                "RECOVERED_COMMIT",0,0,null,0,"USAGE_RECONCILIATION_REQUIRED",null)),((Number)row.get("REPLY_MESSAGE_ID")).longValue())));
    }
    private QaRunner.Outcome snapshot(List<QaRunner.Usage> calls,Long replyId) {
        var reply=messages.findById(replyId).filter(m->m.getSession().getId().equals(sessionId)).orElseThrow();
        var variant=variants.findByMessageIdAndVariantNo(reply.getId(),reply.getSelectedVariantNo()==null?1:reply.getSelectedVariantNo()).orElseThrow();
        return new QaRunner.Outcome(reply.getContent(),inspector.snapshotAll(sessionId).stream().flatMap(c->c.memories().stream())
                .map(m->m.summary()+" "+m.evidenceQuote()).toList(),variant.getEvaluationContextJson(),variant.getTestDiagnosticsJson(),
                variant.getTestMemoryBeforeJson(),variant.getTestMemoryAfterJson(),calls,null);
    }
    static List<QaRunner.Usage> callsSince(List<AiUsageTracker.Call> before,List<AiUsageTracker.Call> after,QaSpec spec,String role) {
        int first=0;
        if(!before.isEmpty()) {
            var last=before.getLast();
            int found=-1;for(int i=0;i<after.size();i++)if(after.get(i)==last)found=i;
            if(found<0)throw new IllegalStateException("Usage buffer overflow; do not report incomplete total");
            first=found+1;
        }
        var result=new ArrayList<QaRunner.Usage>();
        for(var c:after.subList(first,after.size())) {
            var u=c.usage();String actualRole=role.equals("PERSONA")?role:c.purpose().equals("ROLEPLAY")?"TARGET":"AUXILIARY";
            if(u==null) {result.add(new QaRunner.Usage(actualRole,"unknown",c.purpose(),0,0,null,0,c.status(),null));continue;}
            var rate=spec.prices().get(u.model());
            var cost=spec.paid()?(rate==null?null:rate.cost(u.inputTokens(),u.cachedInputTokens(),u.cacheWriteTokens(),u.outputTokens())):java.math.BigDecimal.ZERO;
            result.add(new QaRunner.Usage(actualRole,u.model(),c.purpose(),u.inputTokens(),u.cachedInputTokens(),u.cacheWriteTokens(),u.outputTokens(),c.status(),cost));
        }
        return result;
    }
    static String hash(String input) {
        try{return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8)));}
        catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}
    }
}
