package com.example.projectmm.application.qa;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;

/** Development-only bridge to the existing isolated test-classpath worker. */
public final class QaUiService {
    public static final String PERSONA_MODEL="gpt-5.6-luna";
    public static final List<String> TARGET_MODELS=List.of("gpt-5.6-luna","gpt-5.6-terra","gpt-5.6-sol");
    public record Start(String requestId,String targetModel,int turns,boolean paid,BigDecimal budgetUsd,boolean acknowledgeCost) {}
    public record Estimate(BigDecimal lowUsd,BigDecimal highUsd) {}
    public record Capabilities(String personaModel,List<String> targetModels,boolean paidAvailable,String paidUnavailableReason,
                               int maxTurns,Map<String,Estimate> perTurnEstimates) {}
    public record Meta(String id,String ownerHash,long pocketId,String fingerprint,String targetModel,int turns,
                       boolean paid,BigDecimal budgetUsd,long pid,String createdAt) {
        Meta withPid(long value){return new Meta(id,ownerHash,pocketId,fingerprint,targetModel,turns,paid,budgetUsd,value,Instant.now().toString());}
    }
    public record TurnView(int number,String input,String reply,long latencyMillis,List<String> memories,JsonNode recalledMemories) {}
    public record View(String id,String status,String personaModel,String targetModel,int completed,int total,boolean paid,
                       BigDecimal budgetUsd,BigDecimal knownUsd,int unknownCalls,boolean workerAlive,String reason,List<TurnView> recentTurns) {}
    public interface Worker {
        long launch(List<String> command,Path directory,Map<String,String> environment,Path log) throws IOException;
        boolean alive(long pid,String createdAt);
    }
    private final Path workspace,root,pricesFile;
    private final String maven,cache,apiKey;
    private final Worker worker;
    private final ObjectMapper mapper;
    public QaUiService(Path workspace,Path pricesFile,String maven,String cache,String apiKey,Worker worker,ObjectMapper mapper) throws IOException {
        this.workspace=workspace.toRealPath();this.root=this.workspace.resolve("target/pocket-qa");this.pricesFile=pricesFile;
        this.maven=maven;this.cache=cache;this.apiKey=apiKey;this.worker=worker;this.mapper=mapper;
        if(!Files.isRegularFile(this.workspace.resolve("tools/persona-qa.ps1"))
                ||!Files.isRegularFile(this.workspace.resolve("src/test/java/com/example/projectmm/qa/PersonaQaExecutionTest.java")))
            throw new IllegalArgumentException("QA UI requires a source checkout containing the isolated QA worker");
        Files.createDirectories(root);
        if(!root.toRealPath().startsWith(this.workspace))throw new IllegalArgumentException("QA output escaped source checkout");
    }
    public Capabilities capabilities() {
        var estimates=new LinkedHashMap<String,Estimate>();
        for(var model:TARGET_MODELS)try {estimates.put(model,estimate(model));}catch(RuntimeException ignored) { /* Missing verified pricing disables paid mode. */ }
        boolean key=apiKey!=null&&!apiKey.isBlank();
        boolean ready=key&&estimates.size()==TARGET_MODELS.size();
        return new Capabilities(PERSONA_MODEL,TARGET_MODELS,ready,ready?null:!key?"서버에 테스트용 API 키가 설정되지 않았어요.":"서버의 검증된 모델 가격표 설정이 필요해요.",1000,estimates);
    }
    public synchronized View start(long pocketId,String owner,Start request) throws IOException {
        if(request==null||request.requestId()==null||!request.requestId().matches("[0-9a-fA-F-]{36}")
                ||!TARGET_MODELS.contains(request.targetModel())||request.turns()<1||request.turns()>1000)
            throw bad("대상 모델과 1~1,000턴을 지정해 주세요.");
        try {UUID.fromString(request.requestId());}catch(IllegalArgumentException e){throw bad("올바른 실행 요청 ID가 필요해요.");}
        if(request.paid()&&(!request.acknowledgeCost()||request.budgetUsd()==null||request.budgetUsd().signum()<=0))
            throw bad("실제 API 호출에는 양수인 최대 비용과 비용 발생 동의가 필요해요.");
        if(request.paid()&&!capabilities().paidAvailable())throw bad(capabilities().paidUnavailableReason());
        String ownerHash=hash(owner),id="ui-"+hash(ownerHash+":"+pocketId+":"+request.requestId()).substring(0,24);
        String fingerprint=hash(mapper.writeValueAsString(request));Path path=path(id);
        if(Files.exists(path.resolve("ui-owner.json"))) {
            var old=owned(pocketId,owner,id);
            if(!old.fingerprint().equals(fingerprint))throw new ResponseStatusException(HttpStatus.CONFLICT,"같은 요청 ID의 설정이 달라요.");
            return view(old);
        }
        ensureWorkerAvailable();
        Files.createDirectories(path);
        var meta=new Meta(id,ownerHash,pocketId,fingerprint,request.targetModel(),request.turns(),request.paid(),
                request.paid()?request.budgetUsd():BigDecimal.ZERO,0,Instant.now().toString());
        atomic(path.resolve("config.json"),mapper.writerWithDefaultPrettyPrinter().writeValueAsString(configuration(meta)));
        writeMeta(meta);
        try {meta=launch(meta);}catch(IOException|RuntimeException ex){throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"QA 실행기를 시작하지 못했어요. 서버 실행 설정을 확인해 주세요.");}
        return view(meta);
    }
    public List<View> list(long pocketId,String owner) throws IOException {
        var result=new ArrayList<View>();
        for(var meta:allMeta().stream().sorted(Comparator.comparing(Meta::createdAt).reversed()).toList())
            if(meta.pocketId()==pocketId&&meta.ownerHash().equals(hash(owner)))result.add(view(meta));
        return result;
    }
    public View current(long pocketId,String owner,String id) throws IOException {return view(owned(pocketId,owner,id));}
    public synchronized View control(long pocketId,String owner,String id,String command) throws IOException {
        if(!Set.of("PAUSE","RESUME","STOP").contains(command))throw bad("지원하지 않는 테스트 동작이에요.");
        var meta=owned(pocketId,owner,id);
        View current;
        try {current=view(meta);}catch(IOException|RuntimeException failure) {
            if(!command.equals("STOP"))throw failure;
            atomic(path(id).resolve("control"),"STOP");
            boolean alive=meta.pid()>0&&worker.alive(meta.pid(),meta.createdAt());
            return new View(meta.id(),alive?"STOPPING":"STOPPED",PERSONA_MODEL,meta.targetModel(),0,meta.turns(),meta.paid(),meta.budgetUsd(),null,1,alive,
                    "진행 기록을 읽지 못했지만 중단 요청은 전달했어요. 비용은 원본 기록을 확인해 주세요.",List.of());
        }
        if(Set.of("COMPLETED","STOPPED").contains(current.status()))throw new ResponseStatusException(HttpStatus.CONFLICT,"이미 종료된 테스트예요.");
        if(command.equals("RESUME")&&Set.of("REVIEW_REQUIRED","BUDGET_STOP","FAILED").contains(current.status()))
            throw new ResponseStatusException(HttpStatus.CONFLICT,"비용 또는 실패 원인을 확인한 뒤 실행기로 복구해야 해요.");
        atomic(path(id).resolve("control"),command);
        if(command.equals("RESUME")&&!current.workerAlive()) {ensureWorkerAvailable();meta=launch(meta);}
        return view(meta);
    }
    public byte[] download(long pocketId,String owner,String id,String format) throws IOException {
        owned(pocketId,owner,id);
        String file=switch(format) {case "json"->"run.json";case "markdown"->"report.md";case "turns"->"turns.csv";case "calls"->"calls.csv";default->throw bad("지원하지 않는 보고서 형식이에요.");};
        Path p=path(id).resolve(file);
        if(!Files.isRegularFile(p))throw new ResponseStatusException(HttpStatus.NOT_FOUND,"아직 보고서가 생성되지 않았어요.");
        checkFile(p);return Files.readAllBytes(p);
    }
    private Map<String,Object> configuration(Meta meta) {
        var config=new LinkedHashMap<String,Object>();
        config.put("runId",meta.id());config.put("turns",meta.turns());config.put("paid",meta.paid());config.put("approval",meta.paid()?meta.id():null);
        config.put("persona",Map.of("kind","MIXED","personality","차분하고 호기심 많음","speech","자연스러운 한국어","relationship","새로 알게 된 동료",
                "tendencies","상대 반응을 읽고 일상과 감정과 사건을 섞음","goal","함께 시간을 보내며 신뢰 쌓기"));
        config.put("personaModel",PERSONA_MODEL);config.put("targetModel",meta.targetModel());config.put("extractionModel",PERSONA_MODEL);
        config.put("budgetUsd",meta.budgetUsd());
        config.put("turnReservationUsd",meta.paid()?estimate(meta.targetModel()).highUsd():BigDecimal.ZERO);
        config.put("prices",meta.paid()?mapper.convertValue(prices(),Map.class):Map.of());
        config.put("recentTurns",3);config.put("memoryEnabled",true);return config;
    }
    private Meta launch(Meta meta) throws IOException {
        Path installed=Path.of(maven);if(!installed.isAbsolute())installed=workspace.resolve(installed).normalize();
        Path home=Files.isDirectory(installed)?installed:installed.getParent().getParent();
        Path boot;
        try(var jars=Files.list(home.resolve("boot"))) {
            boot=jars.filter(p->p.getFileName().toString().startsWith("plexus-classworlds-")&&p.toString().endsWith(".jar")).findFirst()
                    .orElseThrow(()->new IOException("Configured Maven installation has no classworlds launcher"));
        }
        Path java=Path.of(System.getProperty("java.home"),"bin",System.getProperty("os.name","").toLowerCase(Locale.ROOT).contains("win")?"java.exe":"java");
        var command=new ArrayList<String>(List.of(java.toString(),"--enable-native-access=ALL-UNNAMED","-classpath",boot.toString(),
                "-Dclassworlds.conf="+home.resolve("bin/m2.conf"),"-Dmaven.home="+home,"-Dmaven.multiModuleProjectDirectory="+workspace,
                "org.codehaus.plexus.classworlds.launcher.Launcher","-o","-Dpocket.qa.execute=true","-Dpocket.qa.run-id="+meta.id(),
                "-Dpocket.qa.config="+path(meta.id()).resolve("config.json"),"-Dtest=PersonaQaExecutionTest"));
        if(cache!=null&&!cache.isBlank())command.add("-Dmaven.repo.local="+cache);
        if(meta.paid())command.add("-Dpocket.qa.approve="+meta.id());
        command.add("test");
        long pid=worker.launch(List.copyOf(command),workspace,Map.of("POCKET_QA_OPENAI_API_KEY",meta.paid()?apiKey:""),path(meta.id()).resolve("worker.log"));
        var updated=meta.withPid(pid);writeMeta(updated);return updated;
    }
    private View view(Meta meta) throws IOException {
        Path file=path(meta.id()).resolve("run.json");boolean alive=meta.pid()>0&&worker.alive(meta.pid(),meta.createdAt());
        if(!Files.isRegularFile(file))return new View(meta.id(),alive?"STARTING":"FAILED",PERSONA_MODEL,meta.targetModel(),0,meta.turns(),meta.paid(),meta.budgetUsd(),BigDecimal.ZERO,0,alive,
                alive?"격리된 테스트 환경을 준비하고 있어요.":"실행기가 시작되지 않았어요. 서버의 worker.log를 확인해 주세요.",List.of());
        checkFile(file);var state=mapper.readTree(Files.readString(file));
        String status=state.path("status").asText("FAILED");
        String control=Files.exists(path(meta.id()).resolve("control"))?Files.readString(path(meta.id()).resolve("control")):"";
        if(!alive&&(status.equals("RUNNING")||status.equals("READY")))status="INTERRUPTED";
        if(!alive&&control.equals("STOP")&&!status.equals("COMPLETED"))status="STOPPED";
        if(alive&&control.equals("STOP")&&!Set.of("COMPLETED","STOPPED").contains(status))status="STOPPING";
        else if(alive&&control.equals("PAUSE")&&status.equals("RUNNING"))status="PAUSING";
        else if(alive&&control.equals("RESUME")&&status.equals("PAUSED"))status="STARTING";
        BigDecimal known=BigDecimal.ZERO;int unknown=0;
        for(var c:state.path("calls")) {
            var amount=c.path("usd");if(amount.isNull()||amount.isMissingNode())amount=state.path("reconciledCosts").path(c.path("callId").asText());
            if(amount.isNull()||amount.isMissingNode())unknown++;else known=known.add(new BigDecimal(amount.asText()));
        }
        var turns=state.path("turns");var recent=new ArrayList<TurnView>();
        for(int i=Math.max(0,turns.size()-10);i<turns.size();i++) {
            var t=turns.path(i);var o=t.path("outcome");var memories=new ArrayList<String>();for(var m:o.path("memories"))memories.add(m.asText());
            JsonNode recalled=mapper.createArrayNode();
            try {recalled=mapper.readTree(o.path("evaluationJson").asText("{}")).path("context").path("recalledMemories");}catch(RuntimeException ignored) { /* Missing evidence stays missing. */ }
            recent.add(new TurnView(t.path("number").asInt(),t.path("input").asText(),o.path("reply").asText(),t.path("latencyMillis").asLong(),memories,recalled));
        }
        return new View(meta.id(),status,PERSONA_MODEL,meta.targetModel(),turns.size(),meta.turns(),meta.paid(),meta.budgetUsd(),known,unknown,alive,
                state.path("reason").isNull()?null:state.path("reason").asText(null),recent);
    }
    private List<Meta> allMeta() throws IOException {
        var result=new ArrayList<Meta>();
        try(var files=Files.list(root)) {
            for(var p:files.filter(Files::isDirectory).filter(p->p.getFileName().toString().matches("ui-[0-9a-f]{24}")).toList()) {
                var file=p.resolve("ui-owner.json");if(Files.isRegularFile(file)){checkFile(file);result.add(mapper.readValue(Files.readString(file),Meta.class));}
            }
        }
        return result;
    }
    private void ensureWorkerAvailable() throws IOException {
        for(var m:allMeta())if(m.pid()>0&&worker.alive(m.pid(),m.createdAt()))
            throw new ResponseStatusException(HttpStatus.CONFLICT,"다른 자동 테스트가 실행 중이에요. 완료 후 시작해 주세요.");
    }
    private Meta owned(long pocketId,String owner,String id) throws IOException {
        var file=path(id).resolve("ui-owner.json");
        if(!Files.isRegularFile(file))throw new ResponseStatusException(HttpStatus.NOT_FOUND,"테스트를 찾을 수 없어요.");
        checkFile(file);var meta=mapper.readValue(Files.readString(file),Meta.class);
        if(meta.pocketId()!=pocketId||!meta.ownerHash().equals(hash(owner)))throw new ResponseStatusException(HttpStatus.NOT_FOUND,"테스트를 찾을 수 없어요.");
        return meta;
    }
    private Path path(String id) throws IOException {
        if(id==null||!id.matches("ui-[0-9a-f]{24}"))throw bad("올바른 테스트 ID가 필요해요.");
        var path=root.resolve(id).normalize();
        if(!path.startsWith(root)||Files.exists(path)&&!path.toRealPath().startsWith(root.toRealPath()))throw bad("허용되지 않는 테스트 경로예요.");
        return path;
    }
    private void checkFile(Path path) throws IOException {
        if(!path.toRealPath().startsWith(root.toRealPath())||Files.size(path)>64L*1024*1024)throw bad("허용되지 않는 테스트 파일이에요.");
    }
    private void writeMeta(Meta meta) throws IOException {atomic(path(meta.id()).resolve("ui-owner.json"),mapper.writeValueAsString(meta));}
    private void atomic(Path path,String text) throws IOException {
        var temp=Files.createTempFile(path.getParent(),"qa-ui-",".tmp");
        try {Files.writeString(temp,text);Files.move(temp,path,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}finally{Files.deleteIfExists(temp);}
    }
    private JsonNode prices() {
        try {if(pricesFile==null)throw bad("가격표가 없어요.");var value=mapper.readTree(Files.readString(pricesFile));
            if(!value.isObject())throw bad("가격표 형식이 올바르지 않아요.");return value;
        } catch(IOException e){throw bad("가격표를 읽을 수 없어요.");}
    }
    private Estimate estimate(String target) {
        var prices=prices();var p=prices.path(PERSONA_MODEL);var t=prices.path(target);
        return new Estimate(estimateRate(p,4000,350,false).add(estimateRate(t,10000,600,false)),
                estimateRate(p,4000,350,true).add(estimateRate(t,10000,600,true)));
    }
    private BigDecimal estimateRate(JsonNode price,long input,long output,boolean written) {
        for(var key:List.of("input","cached","written","output"))if(!price.path(key).isNumber()||price.path(key).decimalValue().signum()<0)throw bad("검증된 모델별 가격표가 필요해요.");
        if(price.path("source").asText().isBlank()||price.path("checkedAt").asText().isBlank())throw bad("가격표 출처와 확인 날짜가 필요해요.");
        return price.path(written?"written":"input").decimalValue().multiply(BigDecimal.valueOf(input))
                .add(price.path("output").decimalValue().multiply(BigDecimal.valueOf(output))).movePointLeft(6);
    }
    private static String hash(String value) {
        try{return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)));}
        catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}
    }
    private static ResponseStatusException bad(String message){return new ResponseStatusException(HttpStatus.BAD_REQUEST,message);}
}
