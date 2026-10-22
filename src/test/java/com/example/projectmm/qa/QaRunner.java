package com.example.projectmm.qa;

import tools.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import static java.nio.file.StandardOpenOption.*;

/** A single step holds an OS file lock. No automatic retry after an uncertain network request. */
public final class QaRunner {
    public record Exchange(String user, String reply) {}
    public record PersonaInput(QaSpec.Persona persona, int turn, List<Exchange> recent) {}
    public record Usage(String role, String model, String purpose, long input, long cached, Long written,
                        long output, String status, BigDecimal usd, String callId, int turn) {
        public Usage(String role,String model,String purpose,long input,long cached,Long written,long output,String status,BigDecimal usd) {
            this(role,model,purpose,input,cached,written,output,status,usd,UUID.randomUUID().toString(),0);
        }
        public Usage atTurn(int value) {return new Usage(role,model,purpose,input,cached,written,output,status,usd,callId,value);}
    }
    public record Diagnostic(String at, String phase, String exceptionType) {}
    static Diagnostic diagnostic(String phase, Throwable error) {
        return new Diagnostic(Instant.now().toString(),phase,error==null?null:error.getClass().getName());
    }
    public record Generation(String text, List<Usage> calls, String error, List<Diagnostic> diagnostics) {
        public Generation(String text,List<Usage> calls,String error) {this(text,calls,error,List.of());}
    }
    public record Outcome(String reply, List<String> memories, String evaluationJson, String diagnosticsJson,
                          String memoryBeforeJson, String memoryAfterJson, List<Usage> calls, String error) {}
    public interface Persona { Generation next(PersonaInput input); }
    public interface Target {
        Outcome send(String requestId, String input);
        Optional<Outcome> recover(String requestId);
    }
    public record Turn(int number, String requestId, String input, Outcome outcome, long latencyMillis,
                       QaScenarios.Verdict verdict) {}
    public static final class State {
        public QaSpec spec;
        public List<QaScenarios.Event> scenario;
        public String startedAt=Instant.now().toString(), updatedAt=startedAt;
        public String status="READY", stage="READY", pendingInput, reason;
        public List<Turn> turns=new ArrayList<>();
        public List<Usage> calls=new ArrayList<>();
        public Map<String,BigDecimal> reconciledCosts=new HashMap<>();
        public List<String> audit=new ArrayList<>();
        public List<Diagnostic> diagnostics=new ArrayList<>();
        public Outcome failedOutcome;
        public BigDecimal heldUsd=BigDecimal.ZERO;
        public State() {}
        public BigDecimal cost(Usage c) {return c.usd()==null?reconciledCosts.get(c.callId()):c.usd();}
        public BigDecimal knownUsd() { return calls.stream().map(this::cost).filter(Objects::nonNull).reduce(BigDecimal.ZERO,BigDecimal::add); }
        public boolean unknownCost() { return calls.stream().anyMatch(c->cost(c)==null); }
        void addCalls(List<Usage> values,int turn) {
            for(var call:values)if(calls.stream().noneMatch(c->c.callId().equals(call.callId())))calls.add(call.atTurn(turn));
        }
    }
    private final Path directory;
    private final ObjectMapper mapper=new ObjectMapper();
    private final Persona persona;
    private final Target target;
    private final int maximumPersonaCharacters;
    public QaRunner(Path directory, Persona persona, Target target) throws IOException {
        this(directory,persona,target,4000);
    }
    /** New simulation modes may preserve the service's unrestricted input; legacy QA keeps 4000. */
    public QaRunner(Path directory, Persona persona, Target target, int maximumPersonaCharacters) throws IOException {
        if(maximumPersonaCharacters<0)throw new IllegalArgumentException("Invalid maximum persona characters");
        this.directory=directory;this.persona=persona;this.target=target;this.maximumPersonaCharacters=maximumPersonaCharacters;
        Files.createDirectories(directory);
    }
    public State create(QaSpec spec, QaScenarios scenarios) throws IOException {
        try(var channel=FileChannel.open(directory.resolve("run.lock"),CREATE,WRITE); var lock=channel.lock()) {
            if(Files.exists(directory.resolve("run.json")))throw new IllegalStateException("Run already exists; resume it");
            var s=new State();s.spec=spec;s.scenario=scenarios.events(); save(s);return s;
        }
    }
    public State read() throws IOException { return mapper.readValue(Files.readString(directory.resolve("run.json")),State.class); }
    public void command(String command) throws IOException {
        if(!Set.of("PAUSE","STOP","RESUME").contains(command))throw new IllegalArgumentException("Unknown command");
        atomic(directory.resolve("control"),command);
    }
    public State step() throws IOException {
        try(var channel=FileChannel.open(directory.resolve("run.lock"),CREATE,WRITE); var lock=channel.lock()) {
            var s=read(); var scenario=new QaScenarios(s.scenario);
            String control=Files.exists(directory.resolve("control"))?Files.readString(directory.resolve("control")):"";
            if(control.equals("STOP")) {s.status="STOPPED";save(s);return s;}
            if(control.equals("PAUSE")) {s.status="PAUSED";save(s);return s;}
            if(Set.of("COMPLETED","STOPPED","REVIEW_REQUIRED","BUDGET_STOP").contains(s.status))return s;
            if(s.status.equals("PAUSED")&&!control.equals("RESUME"))return s;
            if(s.turns.size()>=s.spec.turns()) {s.status="COMPLETED";save(s);return s;}
            int number=s.turns.size()+1;
            String id=s.spec.runId()+":"+number;
            if(s.stage.equals("TARGET_IN_FLIGHT")) {
                var recovered=target.recover(id);
                if(recovered.isPresent()) {finish(s,scenario,id,number,recovered.get(),0);return s;}
                uncertain(s,"TARGET",s.spec.targetModel(),"Target request may have been charged; no durable receipt. Automatic retry prohibited.");return s;
            }
            if(s.stage.equals("PERSONA_IN_FLIGHT")) {
                uncertain(s,"PERSONA",s.spec.personaModel(),"Persona request may have been charged. Automatic retry prohibited.");return s;
            }
            if(s.unknownCost()) {review(s,"A provider call has unknown cost; reconcile before further spending.");return s;}
            if(s.stage.equals("INPUT_READY")&&s.heldUsd.signum()==0) {
                if(s.knownUsd().add(s.spec.turnReservationUsd()).compareTo(s.spec.budgetUsd())>0) {
                    s.status="BUDGET_STOP";s.reason="Insufficient retry reservation";save(s);return s;
                }
                s.heldUsd=s.spec.turnReservationUsd();
            }
            if(s.stage.equals("READY")) {
                if(s.knownUsd().add(s.spec.turnReservationUsd()).compareTo(s.spec.budgetUsd())>0) {
                    s.status="BUDGET_STOP";s.reason="Insufficient budget for next turn reservation";save(s);return s;
                }
                s.heldUsd=s.spec.turnReservationUsd();
                s.pendingInput=scenario.messageFor(number);
                if(s.pendingInput==null) {
                    s.stage="PERSONA_IN_FLIGHT";s.status="RUNNING";s.diagnostics.add(diagnostic("PERSONA_REQUEST_STARTED",null));save(s);
                    var history=s.turns.subList(Math.max(0,s.turns.size()-s.spec.recentTurns()),s.turns.size()).stream()
                            .map(t->new Exchange(t.input(),t.outcome().reply())).toList();
                    Generation generated;
                    try {generated=persona.next(new PersonaInput(s.spec.persona(),number,history));}
                    catch(RuntimeException e) {s.diagnostics.add(diagnostic("PERSONA_FAILED",e));uncertain(s,"PERSONA",s.spec.personaModel(),"Persona failed with uncertain usage; inspect provider before retry");return s;}
                    s.diagnostics.addAll(generated.diagnostics());
                    s.diagnostics.add(diagnostic("PERSONA_RETURNED",null));
                    s.addCalls(generated.calls(),number);
                    s.diagnostics.add(diagnostic("PERSONA_USAGE_CHECKPOINT",null));save(s);
                    if(s.spec.paid()&&generated.calls().isEmpty()) {uncertain(s,"PERSONA",s.spec.personaModel(),"Paid persona returned no usage record");return s;}
                    if(generated.error()!=null||generated.text()==null||generated.text().isBlank()||(maximumPersonaCharacters>0&&generated.text().length()>maximumPersonaCharacters)) {
                        review(s,"Persona output failed validation; charged calls retained");return s;
                    }
                    s.pendingInput=generated.text();
                }
                s.stage="INPUT_READY";save(s);
            }
            if(s.unknownCost()) {review(s,"Persona usage unknown; target not called");return s;}
            if(s.knownUsd().compareTo(s.spec.budgetUsd())>0) {
                s.status="BUDGET_STOP";s.reason="Persona exceeded budget; target not called";save(s);return s;
            }
            s.stage="TARGET_IN_FLIGHT";s.status="RUNNING";s.diagnostics.add(diagnostic("TARGET_REQUEST_STARTED",null));save(s);
            long start=System.nanoTime();
            Outcome outcome;
            try {outcome=target.send(id,s.pendingInput);}
            catch(RuntimeException e) {s.diagnostics.add(diagnostic("TARGET_FAILED",e));uncertain(s,"TARGET",s.spec.targetModel(),"Target failed with uncertain usage; inspect durable receipt and provider");return s;}
            finish(s,scenario,id,number,outcome,(System.nanoTime()-start)/1_000_000);
            return s;
        }
    }
    private void finish(State s,QaScenarios scenario,String id,int number,Outcome outcome,long latency) throws IOException {
        s.diagnostics.add(diagnostic("TARGET_RESPONSE_RECEIVED",null));
        if(outcome.error()!=null)s.failedOutcome=outcome;
        s.addCalls(outcome.calls(),number);
        s.diagnostics.add(diagnostic("TARGET_USAGE_CHECKPOINT",null));save(s);
        boolean blockedBeforeSend=outcome.calls().stream().anyMatch(c->c.status().equals("BUDGET_BLOCKED"));
        if(blockedBeforeSend&&outcome.error()!=null) {
            if(s.unknownCost()) {review(s,"A request was blocked before sending, but an earlier charge still needs reconciliation");return;}
            s.status="BUDGET_STOP";s.reason=outcome.error()==null?"Request blocked before sending":outcome.error();
            s.heldUsd=BigDecimal.ZERO;save(s);return;
        }
        if(s.spec.paid()&&outcome.calls().stream().noneMatch(c->c.role().equals("TARGET"))) {
            uncertain(s,"TARGET",s.spec.targetModel(),"Paid target returned no usage record");return;
        }
        if(outcome.error()!=null) {review(s,outcome.error());return;}
        s.turns.add(new Turn(number,id,s.pendingInput,outcome,latency,scenario.evaluate(number,outcome.reply(),outcome.memories())));
        s.pendingInput=null;s.stage="READY";s.heldUsd=BigDecimal.ZERO;
        if(s.unknownCost()) {review(s,"Usage incomplete; known subtotal is not total cost");return;}
        if(blockedBeforeSend) {s.status="BUDGET_STOP";s.reason="A subsequent auxiliary request was blocked before sending; the committed turn is preserved";}
        else if(s.knownUsd().compareTo(s.spec.budgetUsd())>0) {s.status="BUDGET_STOP";s.reason="Actual call exceeded configured reservation/budget";}
        else s.status=s.turns.size()==s.spec.turns()?"COMPLETED":"RUNNING";
        save(s);
    }
    private void review(State s,String reason) throws IOException {s.status="REVIEW_REQUIRED";s.reason=reason;save(s);}
    private void uncertain(State s,String role,String model,String reason) throws IOException {
        s.addCalls(List.of(new Usage(role,model,"UNCERTAIN_REQUEST",0,0,null,0,"UNKNOWN",null)),s.turns.size()+1);
        review(s,reason);
    }
    private void save(State s) throws IOException {
        s.updatedAt=Instant.now().toString();
        try {atomic(directory.resolve("run.json"),mapper.writerWithDefaultPrettyPrinter().writeValueAsString(s));}
        catch(IOException e) {
            // Checkpoint may be unavailable: emit only safe metadata, never exception messages.
            System.err.printf("QA checkpoint failure at=%s stage=%s exceptionType=%s%n",Instant.now(),s.stage,e.getClass().getName());
            throw e;
        }
    }
    private void atomic(Path path,String content) throws IOException {
        var temp=Files.createTempFile(directory,"checkpoint-",".tmp");
        try {Files.writeString(temp,content);try(var f=FileChannel.open(temp,WRITE)){f.force(true);}
            // Windows readers can briefly hold the destination. Retry only this local move;
            // never repeat a provider request or downgrade atomic durability.
            for(int attempt=0;;attempt++) {
                try {Files.move(temp,path,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);break;}
                catch(AccessDeniedException e) {
                    if(attempt>=4)throw e;
                    try {Thread.sleep(25L << attempt);}
                    catch(InterruptedException interrupted) {Thread.currentThread().interrupt();throw new IOException("Checkpoint move interrupted",interrupted);}
                }
            }
        } finally {Files.deleteIfExists(temp);}
    }
    /** Explicit retry is safe only when a durable successful receipt already exists. */
    public State recoverCommitted() throws IOException {
        try(var channel=FileChannel.open(directory.resolve("run.lock"),CREATE,WRITE); var lock=channel.lock()) {
            var s=read();
            if(!s.stage.equals("TARGET_IN_FLIGHT"))throw new IllegalStateException("No uncertain target request");
            var id=s.spec.runId()+":"+(s.turns.size()+1);
            var result=target.recover(id).orElseThrow(()->new IllegalStateException("No committed receipt; paid retry requires reconciliation"));
            finish(s,new QaScenarios(s.scenario),id,s.turns.size()+1,result,0);return s;
        }
    }
    /** Human-supplied provider evidence, never an automatic zero-cost assumption. */
    public State authorizeRetry(Map<String,BigDecimal> reconciled,String evidence) throws IOException {
        try(var channel=FileChannel.open(directory.resolve("run.lock"),CREATE,WRITE);var lock=channel.lock()) {
            var s=read();
            if(!s.status.equals("REVIEW_REQUIRED")||evidence==null||evidence.isBlank())throw new IllegalStateException("Retry needs review state and reconciliation evidence");
            for(var entry:reconciled.entrySet()) {
                if(entry.getValue()==null||entry.getValue().signum()<0||s.calls.stream().noneMatch(c->c.callId().equals(entry.getKey())&&c.usd()==null))
                    throw new IllegalArgumentException("Invalid reconciliation call ID or amount");
                s.reconciledCosts.put(entry.getKey(),entry.getValue());
            }
            if(s.unknownCost())throw new IllegalStateException("All unknown calls must be reconciled before retry");
            String id=s.spec.runId()+":"+(s.turns.size()+1);
            if(s.stage.equals("TARGET_IN_FLIGHT")&&target.recover(id).isPresent())
                throw new IllegalStateException("Committed receipt exists; recover it instead of retrying");
            if(s.stage.equals("TARGET_IN_FLIGHT"))s.stage="INPUT_READY";
            else if(s.stage.equals("PERSONA_IN_FLIGHT")) {s.stage="READY";s.pendingInput=null;}
            else if(!s.stage.equals("READY"))throw new IllegalStateException("No failed in-flight request to retry");
            s.audit.add(Instant.now()+" retry authorized: "+evidence+" costs="+reconciled);
            s.heldUsd=BigDecimal.ZERO;s.reason=null;s.status=s.turns.size()==s.spec.turns()?"COMPLETED":"RUNNING";save(s);return s;
        }
    }
}
