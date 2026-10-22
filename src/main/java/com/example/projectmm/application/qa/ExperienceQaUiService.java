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
public final class ExperienceQaUiService {
    public static final String PERSONA_MODEL="gpt-5.6-luna";
    public static final List<String> TARGET_MODELS=List.of("gpt-5.6-luna","gpt-5.6-terra","gpt-5.6-sol");
    public record Start(String requestId,String targetModel,int turns,boolean paid,BigDecimal budgetUsd,boolean acknowledgeCost,String snapshotDigest) {}
    public record Estimate(BigDecimal lowUsd,BigDecimal highUsd,BigDecimal serviceLowUsd,BigDecimal serviceHighUsd) {}
    public record Capabilities(String personaModel,List<String> targetModels,boolean paidAvailable,String paidUnavailableReason,
                               int maxTurns,Map<String,Estimate> perTurnEstimates,SnapshotInfo snapshot,Map<String,Object> budgetPolicy) {
        public Capabilities(String personaModel,List<String> targetModels,boolean paidAvailable,String reason,int maxTurns,Map<String,Estimate> estimates,SnapshotInfo snapshot) {
            this(personaModel,targetModels,paidAvailable,reason,maxTurns,estimates,snapshot,ExperienceBudgetPolicy.create(List.of()));
        }
    }
    public record SnapshotInfo(String digest,String world,List<String> characters,String player,int messages,int memories,long estimatedInputTokens,long observedOutputTokens) {}
    public record Meta(String id,String ownerHash,long pocketId,String fingerprint,String targetModel,int turns,
                       boolean paid,BigDecimal budgetUsd,long pid,String createdAt) {
        Meta withPid(long value){return new Meta(id,ownerHash,pocketId,fingerprint,targetModel,turns,paid,budgetUsd,value,Instant.now().toString());}
    }
    public record TurnView(int number,String input,String reply,long latencyMillis,List<String> memories,JsonNode recalledMemories) {}
    public record View(String id,String status,String personaModel,String targetModel,int completed,int total,boolean paid,
                       BigDecimal budgetUsd,BigDecimal knownUsd,int unknownCalls,boolean workerAlive,String reason,List<TurnView> recentTurns,JsonNode budget) {}
    public interface Worker {
        long launch(List<String> command,Path directory,Map<String,String> environment,Path log) throws IOException;
        boolean alive(long pid,String createdAt);
    }
    private final Path workspace,root,pricesFile,limitsFile;
    private final PocketExperienceSnapshotService snapshots;
    private final String geminiKey;
    private final String maven,cache,apiKey;
    private final Worker worker;
    private final ObjectMapper mapper;
    public ExperienceQaUiService(Path workspace,Path pricesFile,String maven,String cache,String apiKey,Worker worker,ObjectMapper mapper,PocketExperienceSnapshotService snapshots,Path limitsFile,String geminiKey) throws IOException {
        this.workspace=workspace.toRealPath();this.root=this.workspace.resolve("target/pocket-qa-experience");this.pricesFile=pricesFile;
        this.snapshots=snapshots;this.limitsFile=limitsFile;this.geminiKey=geminiKey;this.maven=maven;this.cache=cache;this.apiKey=apiKey;this.worker=worker;this.mapper=mapper;
        if(!Files.isRegularFile(this.workspace.resolve("tools/persona-qa.ps1"))
                ||!Files.isRegularFile(this.workspace.resolve("src/test/java/com/example/projectmm/qa/ExperienceExecutionTest.java")))
            throw new IllegalArgumentException("QA UI requires a source checkout containing the isolated QA worker");
        Files.createDirectories(root);
        if(!root.toRealPath().startsWith(this.workspace))throw new IllegalArgumentException("QA output escaped source checkout");
    }
    public Capabilities capabilities(long pocketId,String owner) {
        var snapshot=snapshots.capture(pocketId,owner);var info=info(snapshot);var estimates=new LinkedHashMap<String,Estimate>();String unavailable=null;
        for(String model:TARGET_MODELS)try {validatePricing(snapshot,model);estimates.put(model,estimate(model,snapshot));}catch(RuntimeException e){unavailable=e.getMessage();}
        boolean ready=apiKey!=null&&!apiKey.isBlank()&&estimates.size()==TARGET_MODELS.size();
        if(apiKey==null||apiKey.isBlank())unavailable="서버에 테스트용 OpenAI 키가 필요해요.";
        return new Capabilities(PERSONA_MODEL,TARGET_MODELS,ready,ready?null:unavailable,1000,estimates,info,budgetPolicy(snapshot));
    }
    private void validatePricing(PocketExperienceSnapshot snapshot,String target) {
        var models=new LinkedHashSet<String>(List.of(PERSONA_MODEL,target));
        if(snapshot.memoryEnabled()&&!snapshot.extractorProvider().equals("NONE"))models.add(snapshot.extractorModel());
        if(snapshot.compressorModel()!=null)models.add(snapshot.compressorModel());
        if(snapshot.timeModel()!=null)models.add(snapshot.timeModel());
        if(snapshot.embeddingModel()!=null)models.add(snapshot.embeddingModel());
        var rates=prices();var maxima=limits();
        for(String model:models) {
            if(!rates.path(model).isObject())throw bad("실서비스 보조 모델까지 검증된 가격표가 필요해요: "+model);
            estimateRate(rates.path(model),1,1,false);
            if((!maxima.path(model).path("maximumOutputTokens").isIntegralNumber()||maxima.path(model).path("maximumOutputTokens").asLong(-1)<(model.equals(snapshot.embeddingModel())?0:1))||maxima.path(model).path("source").asText().isBlank()||maxima.path(model).path("checkedAt").asText().isBlank())
                throw bad("요청을 변경하지 않고 지출을 예약하려면 검증된 모델 최대 출력 정보가 필요해요: "+model);
        }
        if(snapshot.extractorProvider().equals("GEMINI")||java.util.stream.Stream.of(snapshot.extractorModel(),snapshot.compressorModel(),snapshot.timeModel(),snapshot.embeddingModel()).filter(java.util.Objects::nonNull).anyMatch(model->model.startsWith("gemini")))
            throw bad("이전 Gemini 보조 호출 설정은 차단되었어요. OpenAI 설정으로 새 테스트를 만들어 주세요.");
    }
    private JsonNode limits() {
        try {return limitsFile==null?mapper.createObjectNode():mapper.readTree(Files.readString(limitsFile));}
        catch(IOException e){throw bad("모델 최대 출력 설정 파일을 읽지 못했어요.");}
    }
    private SnapshotInfo info(PocketExperienceSnapshot snapshot) {
        var rootWorlds=new HashSet<String>();
        for(var table:snapshot.tables())if(table.name().equals("world_conversations"))for(var row:table.rows())rootWorlds.add(row.get("world_id").value());
        var registered=new HashSet<String>();boolean captured=false;
        for(var table:snapshot.tables())if(table.name().equals("world_room_sessions"))for(var row:table.rows()) {var value=row.get("cast_roster_captured");captured=value!=null&&Set.of("true","1").contains(String.valueOf(value.value()));}
        for(var table:snapshot.tables())if(table.name().equals(captured?"world_room_session_cast":"world_conversation_cast"))for(var row:table.rows())registered.add(row.get("character_id").value());
        String world="",player="";var characters=new ArrayList<String>();int messages=0,memories=0;long output=0,count=0;
        for(var table:snapshot.tables())for(var row:table.rows()) {
            if(table.name().equals("worlds")&&rootWorlds.contains(row.get("id").value()))world=row.get("name").value();
            if(table.name().equals("characters")&&registered.contains(row.get("id").value()))characters.add(row.get("name").value());
            if(table.name().equals("player_profiles"))player=row.get("name").value();
            if(table.name().equals("memories"))memories++;
            if(table.name().equals("world_room_messages")) {messages++;if(row.get("role").value().equals("CHARACTER")){output+=tokens(row.get("content").value());count++;}}
        }
        // Estimate settings + bounded service history. No source recall is executed to prepare an estimate.
        long input=0;
        var usedFields=Set.of("name","play_guide","description","raw_description","example_dialogue","personality","speech_style","background","appearance","category","title","content","change_condition","label","user_note","pocket_play_guide","intro_title_snapshot","intro_content_snapshot","location","condition_text","item_text");
        long recentOutput=0,replies=0;
        for(var table:snapshot.tables()) {
            if(table.name().equals("world_room_messages")) {
                var recent=table.rows().stream().sorted(Comparator.comparingLong((Map<String,PocketExperienceSnapshot.Cell> row)->Long.parseLong(row.get("id").value())).reversed()).limit(12).toList();
                for(var row:recent) {long n=tokens(row.get("content").value());input+=n;if(row.get("role").value().equals("CHARACTER")){recentOutput+=n;replies++;}}
            } else if(!List.of("memories","protected_memory_detail","world_room_message_variants","pocket_activity_reports","relationship_facts").contains(table.name())) {
                for(var row:table.rows())for(var field:row.entrySet())if(usedFields.contains(field.getKey()))input+=tokens(field.getValue().value());
            }
        }
        // Include an explicit allowance for schema/instructions/recall; these are assumptions, not a bill or maximum.
        return new SnapshotInfo(snapshot.previewDigest(),world,characters,player,messages,memories,input+2000,((Number)budgetPolicy(snapshot).get("expectedTargetOutputTokens")).longValue());
    }
    private static final com.knuddels.jtokkit.api.Encoding ENCODING=com.knuddels.jtokkit.Encodings.newDefaultEncodingRegistry().getEncoding(com.knuddels.jtokkit.api.EncodingType.O200K_BASE);
    private long tokens(String text) {return ENCODING.countTokensOrdinary(text==null?"":text);}
    public synchronized View start(long pocketId,String owner,Start request) throws IOException {
        if(request==null||request.requestId()==null||!request.requestId().matches("[0-9a-fA-F-]{36}")
                ||!TARGET_MODELS.contains(request.targetModel())||request.turns()<1||request.turns()>1000)
            throw bad("대상 모델과 1~1,000턴을 지정해 주세요.");
        try {UUID.fromString(request.requestId());}catch(IllegalArgumentException e){throw bad("올바른 실행 요청 ID가 필요해요.");}
        if(request.paid()&&(!request.acknowledgeCost()||request.budgetUsd()==null||request.budgetUsd().signum()<=0))
            throw bad("실제 API 호출에는 양수인 최대 비용과 비용 발생 동의가 필요해요.");
        String ownerHash=hash(owner),id="exp-"+hash(ownerHash+":"+pocketId+":"+request.requestId()).substring(0,24);
        String fingerprint=hash(mapper.writeValueAsString(request));Path path=path(id);
        if(Files.exists(path.resolve("ui-owner.json"))) {
            var old=owned(pocketId,owner,id);
            if(!old.fingerprint().equals(fingerprint))throw new ResponseStatusException(HttpStatus.CONFLICT,"같은 요청 ID의 설정이 달라요.");
            return view(old);
        }
        var snapshot=snapshots.capture(pocketId,owner);
        if(!snapshot.previewDigest().equals(request.snapshotDigest()))throw new ResponseStatusException(HttpStatus.CONFLICT,"원본 포켓이 변경됐어요. 새로고침 후 설정을 다시 확인해 주세요.");
        if(request.paid())validatePricing(snapshot,request.targetModel());
        if(request.paid()&&(apiKey==null||apiKey.isBlank()))throw bad("테스트용 OpenAI 키가 필요해요.");
        ensureWorkerAvailable();
        Files.createDirectories(path);
        var meta=new Meta(id,ownerHash,pocketId,fingerprint,request.targetModel(),request.turns(),request.paid(),
                request.paid()?request.budgetUsd():BigDecimal.ZERO,0,Instant.now().toString());
        atomic(path.resolve("config.json"),mapper.writerWithDefaultPrettyPrinter().writeValueAsString(configuration(meta,snapshot)));
        atomic(path.resolve("snapshot.json"),mapper.writerWithDefaultPrettyPrinter().writeValueAsString(snapshot));
        var maxima=new LinkedHashMap<String,Long>();limits().properties().forEach(e->maxima.put(e.getKey(),e.getValue().path("maximumOutputTokens").asLong()));
        atomic(path.resolve("manifest.json"),mapper.writerWithDefaultPrettyPrinter().writeValueAsString(Map.of("budgetPolicy",budgetPolicy(snapshot),"maximumOutputTokens",maxima,"codeHashes",PocketExperienceCode.hashes(),"playerPromptVersion","experience-player-v2-narrative","snapshotDigest",snapshot.digest(),"evaluationMode","MANUAL_NO_MODEL_CALLS","estimateBasis",info(snapshot),"estimatedPerTurn",request.paid()?estimate(meta.targetModel(),snapshot):Map.of(),"differences",java.util.stream.Stream.concat(snapshot.differences().stream(),java.util.stream.Stream.of(String.valueOf(budgetPolicy(snapshot).get("differences")))).toList())));
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
                    "진행 기록을 읽지 못했지만 중단 요청은 전달했어요. 비용은 원본 기록을 확인해 주세요.",List.of(),mapper.createObjectNode());
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
        String file=switch(format) {case "json"->"run.json";case "markdown"->"report.md";case "turns"->"turns.csv";case "calls"->"calls.csv";case "metrics"->"run.json";case "prompts"->"manifest.json";default->throw bad("지원하지 않는 보고서 형식이에요.");};
        Path p=path(id).resolve(file);
        if(!Files.isRegularFile(p))throw new ResponseStatusException(HttpStatus.NOT_FOUND,"아직 보고서가 생성되지 않았어요.");
        checkFile(p);
        if(format.equals("prompts")) {
            var bytes=new java.io.ByteArrayOutputStream();long total=0;
            try(var zip=new java.util.zip.ZipOutputStream(bytes)) {
                var files=new ArrayList<Path>();files.add(path(id).resolve("manifest.json"));files.add(path(id).resolve("snapshot.json"));
                for(String name:List.of("budget.json","budget-events.jsonl"))if(Files.exists(path(id).resolve(name)))files.add(path(id).resolve(name));
                for(var receipt:ledger(path(id))) {
                    String call=receipt.path("callId").asText();if(!call.matches("[0-9a-f-]{36}"))throw new IOException("Invalid request identifier");
                    files.add(path(id).resolve("requests").resolve(call+".json"));
                }
                for(Path entry:files) {
                    checkFile(entry);if(!entry.toRealPath().startsWith(path(id).toRealPath()))throw bad("요청 기록 경로가 올바르지 않아요.");
                    total+=Files.size(entry);if(total>64L*1024*1024)throw bad("프롬프트 묶음이 64MB를 넘어요. 서버 실행 폴더에서 원본을 확인해 주세요.");
                    zip.putNextEntry(new java.util.zip.ZipEntry(path(id).relativize(entry).toString().replace('\\','/')));Files.copy(entry,zip);zip.closeEntry();
                }
            }
            return bytes.toByteArray();
        }
        if(format.equals("metrics")) {
            var csv=new StringBuilder("turn,input_tokens,output_tokens,cached_tokens,reply_characters,dialogue_characters_approx,narration_characters_approx,model_request_ms,total_roundtrip_ms,usage_known,semantic_review\n");
            for(var turn:analysis(pocketId,owner,id).path("turns"))csv.append(turn.path("turn").asInt()).append(',').append(turn.path("inputTokens").asLong()).append(',').append(turn.path("outputTokens").asLong()).append(',').append(turn.path("cachedTokens").asLong()).append(',').append(turn.path("replyCharacters").asInt()).append(',').append(turn.path("dialogueCharacters").asInt()).append(',').append(turn.path("narrationCharacters").asInt()).append(',').append(turn.path("generationMillis").asLong(-1)).append(',').append(turn.path("latencyMillis").asLong()).append(',').append(turn.path("usageKnown").asBoolean()).append(',').append(turn.path("semanticReview").asText()).append('\n');
            return csv.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
        }
        if(format.equals("json")) {
            var bundle=new LinkedHashMap<String,Object>();bundle.put("execution",stateWithLedger(path(id)));bundle.put("currentAnalysis",analysis(pocketId,owner,id));
            for(String name:List.of("snapshot","manifest","snapshot-verification","analysis","reviews","budget")) {
                Path f=path(id).resolve(name+".json");if(Files.exists(f)){checkFile(f);bundle.put(name,mapper.readTree(Files.readString(f)));}
            }
            for(String name:List.of("ledger","budget-events")) {
                Path ledger=path(id).resolve(name+".jsonl");if(Files.exists(ledger)){checkFile(ledger);bundle.put(name,Files.readAllLines(ledger).stream().filter(line->!line.isBlank()).map(mapper::readTree).toList());}
            }
            return mapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(bundle);
        }
        if(format.equals("markdown")) {Path f=path(id).resolve("experience-report.md");checkFile(f);String text=Files.readString(f)+"\n## 실행 당시 예상과 실측 비교 및 검토 기록\n\n```json\n"+mapper.writerWithDefaultPrettyPrinter().writeValueAsString(analysis(pocketId,owner,id))+"\n```\n";return text.getBytes(java.nio.charset.StandardCharsets.UTF_8);}
        return Files.readAllBytes(p);
    }
    private List<JsonNode> ledger(Path directory) throws IOException {
        Path file=directory.resolve("ledger.jsonl");if(!Files.exists(file))return List.of();checkFile(file);
        var receipts=new LinkedHashMap<String,JsonNode>();
        // An interrupted final line remains unknown; never discard it as a free call.
        for(String line:Files.readAllLines(file))if(!line.isBlank()) {
            try {var entry=mapper.readTree(line);receipts.put(entry.path("callId").asText(),entry);}
            catch(RuntimeException e){throw new IOException("Ledger is incomplete; reconciliation required");}
        }
        return new ArrayList<>(receipts.values());
    }
    private List<JsonNode> ledgerEvents(Path directory) throws IOException {
        Path file=directory.resolve("ledger.jsonl");if(!Files.exists(file))return List.of();checkFile(file);
        return Files.readAllLines(file).stream().filter(line->!line.isBlank()).map(mapper::readTree).toList();
    }
    private JsonNode stateWithLedger(Path directory) throws IOException {
        Path file=directory.resolve("run.json");checkFile(file);var state=mapper.readTree(Files.readString(file));
        var ids=new HashSet<String>();for(var call:state.path("calls"))ids.add(call.path("callId").asText());
        var calls=(tools.jackson.databind.node.ArrayNode)state.path("calls");
        for(var entry:ledger(directory))if(!ids.contains(entry.path("callId").asText())) {
            var call=(tools.jackson.databind.node.ObjectNode)entry.deepCopy();call.put("turn",state.path("turns").size()+1);calls.add(call);
        }
        return state;
    }
    public JsonNode analysis(long pocketId,String owner,String id) throws IOException {
        owned(pocketId,owner,id);Path file=path(id).resolve("run.json");if(!Files.exists(file))return mapper.createObjectNode();checkFile(file);
        var result=(tools.jackson.databind.node.ObjectNode)PocketExperienceAnalysis.fullAnalysis(stateWithLedger(path(id)),mapper.readTree(Files.readString(path(id).resolve("manifest.json"))),ledgerEvents(path(id)));
        result.set("budget",budgetView(owned(pocketId,owner,id)));
        Path reviews=path(id).resolve("reviews.json");
        if(Files.exists(reviews)){checkFile(reviews);result.set("reviews",mapper.readTree(Files.readString(reviews)));}
        else result.set("reviews",mapper.createArrayNode());
        return result;
    }
    public record Review(int turn,String type,String note) {}
    public synchronized JsonNode review(long pocketId,String owner,String id,Review request) throws IOException {
        owned(pocketId,owner,id);
        if(request==null||request.turn()<1||!Set.of("SETTING_DRIFT","CHARACTER_DRIFT","MEMORY_ERROR","APPROPRIATE_SHORT","OTHER").contains(request.type())||request.note()==null||request.note().isBlank()||request.note().length()>1000)throw bad("턴과 검토 내용(1~1000자)을 입력해 주세요.");
        Path run=path(id).resolve("run.json");checkFile(run);var state=mapper.readTree(Files.readString(run));
        if(request.turn()>state.path("turns").size())throw bad("아직 생성되지 않은 턴이에요.");
        Path file=path(id).resolve("reviews.json");if(Files.exists(file))checkFile(file);
        var rows=Files.exists(file)?(tools.jackson.databind.node.ArrayNode)mapper.readTree(Files.readString(file)):mapper.createArrayNode();
        rows.add(mapper.valueToTree(Map.of("turn",request.turn(),"type",request.type(),"note",request.note(),"at",Instant.now().toString(),"assessment","HUMAN_REVIEW")));
        atomic(file,mapper.writerWithDefaultPrettyPrinter().writeValueAsString(rows));return rows;
    }
    public JsonNode conversation(long pocketId,String owner,String id,int offset,int limit) throws IOException {
        owned(pocketId,owner,id);if(offset<0||limit<1||limit>100)throw bad("대화 범위가 올바르지 않아요.");
        Path file=path(id).resolve("run.json");checkFile(file);var state=stateWithLedger(path(id));var rows=mapper.createArrayNode();
        var turns=state.path("turns");for(int i=offset;i<Math.min(turns.size(),offset+limit);i++)rows.add(turns.path(i));
        return mapper.valueToTree(Map.of("offset",offset,"total",turns.size(),"turns",rows));
    }
    private Map<String,Object> configuration(Meta meta,PocketExperienceSnapshot snapshot) {
        var config=new LinkedHashMap<String,Object>();
        config.put("runId",meta.id());config.put("turns",meta.turns());config.put("paid",meta.paid());config.put("approval",meta.paid()?meta.id():null);
        config.put("persona",Map.of("kind","MIXED","personality","원본 플레이어 프로필과 현재 상황을 사용","speech","원본 플레이어 말투와 대화 기록을 사용","relationship","기존 서사와 관계 상태를 사용",
                "tendencies","실제 이용자처럼 상황에 맞게 대사와 행동을 혼합","goal","원본 포켓의 이야기를 자연스럽게 이어가기"));
        config.put("personaModel",PERSONA_MODEL);config.put("targetModel",meta.targetModel());config.put("extractionModel",snapshot.extractorModel());
        config.put("budgetUsd",meta.budgetUsd());
        config.put("turnReservationUsd",meta.paid()?new BigDecimal("0.000001"):BigDecimal.ZERO);
        config.put("prices",meta.paid()?mapper.convertValue(prices(),Map.class):Map.of());
        config.put("recentTurns",6);config.put("memoryEnabled",snapshot.memoryEnabled());return config;
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
        var command=new ArrayList<String>(List.of(java.toString(),"-Djava.io.tmpdir="+workspace,"--enable-native-access=ALL-UNNAMED","-classpath",boot.toString(),
                "-Dclassworlds.conf="+home.resolve("bin/m2.conf"),"-Dmaven.home="+home,"-Dmaven.multiModuleProjectDirectory="+workspace,
                "org.codehaus.plexus.classworlds.launcher.Launcher","-o","-Dpocket.qa.execute=true","-Dpocket.experience.execute=true","-Dpocket.qa.run-id="+meta.id(),
                "-Dpocket.qa.config="+path(meta.id()).resolve("config.json"),"-Dtest=ExperienceExecutionTest"));
        if(cache!=null&&!cache.isBlank())command.add("-Dmaven.repo.local="+cache);
        if(meta.paid())command.add("-Dpocket.qa.approve="+meta.id());
        command.add("test");
        long pid=worker.launch(List.copyOf(command),workspace,Map.of("POCKET_QA_OPENAI_API_KEY",meta.paid()?apiKey:"","POCKET_EXPERIENCE_GEMINI_API_KEY",""),path(meta.id()).resolve("worker.log"));
        var updated=meta.withPid(pid);writeMeta(updated);return updated;
    }
    private View view(Meta meta) throws IOException {
        Path file=path(meta.id()).resolve("run.json");boolean alive=meta.pid()>0&&worker.alive(meta.pid(),meta.createdAt());
        if(!Files.isRegularFile(file))return new View(meta.id(),alive?"STARTING":"FAILED",PERSONA_MODEL,meta.targetModel(),0,meta.turns(),meta.paid(),meta.budgetUsd(),BigDecimal.ZERO,0,alive,
                alive?"격리된 테스트 환경을 준비하고 있어요.":"실행기가 시작되지 않았어요. 서버의 worker.log를 확인해 주세요.",List.of(),budgetView(meta));
        checkFile(file);var state=stateWithLedger(path(meta.id()));
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
                state.path("reason").isNull()?null:state.path("reason").asText(null),recent,budgetView(meta));
    }
    private JsonNode budgetView(Meta meta) throws IOException {
        Path file=path(meta.id()).resolve("budget.json");
        if(Files.exists(file)){checkFile(file);return mapper.readTree(Files.readString(file));}
        var node=mapper.createObjectNode();node.put("budgetUsd",meta.budgetUsd());node.put("actualUsd",BigDecimal.ZERO);
        node.put("reservedUsd",BigDecimal.ZERO);node.put("remainingUsd",meta.budgetUsd());node.put("phase","NOT_STARTED");
        Path stateFile=path(meta.id()).resolve("run.json");
        if(Files.exists(stateFile)) {
            var state=stateWithLedger(path(meta.id()));BigDecimal known=BigDecimal.ZERO;boolean unknown=false;
            for(var call:state.path("calls")){if(call.path("usd").isNumber())known=known.add(call.path("usd").decimalValue());else unknown=true;}
            node.put("actualUsd",known);node.put("remainingUsd",meta.budgetUsd().subtract(known));node.put("uncertain",unknown);
            if(unknown)node.putNull("remainingUsd");
            node.put("phase","LEGACY_NO_RESERVATION");node.put("reason","이전 실행에는 하드캡 예약 기록이 없습니다. 기존 비용 기록을 표시하며, 새 정책 적용에는 새 실행이 필요합니다.");
        }
        return node;
    }
    private Map<String,Object> budgetPolicy(PocketExperienceSnapshot snapshot) {
        var output=new ArrayList<Long>();
        for(var table:snapshot.tables())if(table.name().equals("world_room_messages"))table.rows().stream()
                .filter(row->row.get("role").value().equals("CHARACTER"))
                .sorted(Comparator.comparingLong((Map<String,PocketExperienceSnapshot.Cell> row)->Long.parseLong(row.get("id").value())).reversed())
                .limit(12).forEach(row->output.add(tokens(row.get("content").value())));
        return ExperienceBudgetPolicy.create(output);
    }
    private List<Meta> allMeta() throws IOException {
        var result=new ArrayList<Meta>();
        try(var files=Files.list(root)) {
            for(var p:files.filter(Files::isDirectory).filter(p->p.getFileName().toString().matches("exp-[0-9a-f]{24}")).toList()) {
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
        if(id==null||!id.matches("exp-[0-9a-f]{24}"))throw bad("올바른 테스트 ID가 필요해요.");
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
    private Estimate estimate(String target,PocketExperienceSnapshot snapshot) {
        var rates=prices();var info=info(snapshot);long input=info.estimatedInputTokens();var policy=budgetPolicy(snapshot);long output=((Number)policy.get("expectedTargetOutputTokens")).longValue();long safe=((Number)policy.get("safeExpectedTargetOutputTokens")).longValue();
        var serviceLow=estimateRate(rates.path(target),input,Math.max(100,output/2),false);
        var serviceHigh=estimateRate(rates.path(target),input*2,safe,true);
        // Every turn has one review; repaired prose has one verification. Legacy backfill is extra.
        serviceLow=serviceLow.add(estimateRate(rates.path(target),input+output+4000,output+1500,false));
        serviceHigh=serviceHigh.add(estimateRate(rates.path(target),input*2+safe+8000,safe+6000,true).multiply(BigDecimal.valueOf(2)));

        if(snapshot.memoryEnabled()&&!snapshot.extractorProvider().equals("NONE")) {
            serviceLow=serviceLow.add(estimateRate(rates.path(snapshot.extractorModel()),1500,200,false));
            serviceHigh=serviceHigh.add(estimateRate(rates.path(snapshot.extractorModel()),4000,2048,true));
        }
        if(snapshot.embeddingModel()!=null)serviceHigh=serviceHigh.add(estimateRate(rates.path(snapshot.embeddingModel()),8000,0,false).multiply(BigDecimal.valueOf(10)));
        if(snapshot.timeModel()!=null)serviceHigh=serviceHigh.add(estimateRate(rates.path(snapshot.timeModel()),2000,500,true));
        if(snapshot.compressorModel()!=null)serviceHigh=serviceHigh.add(estimateRate(rates.path(snapshot.compressorModel()),8000,2000,true).multiply(BigDecimal.valueOf(4)));
        long playerInput=input;
        for(var table:snapshot.tables())if(table.name().equals("world_room_messages"))for(var row:table.rows())playerInput+=tokens(row.get("content").value());
        var playerLow=estimateRate(rates.path(PERSONA_MODEL),playerInput,200,false);var playerHigh=estimateRate(rates.path(PERSONA_MODEL),playerInput*2,2000,true);
        return new Estimate(serviceLow.add(playerLow),serviceHigh.add(playerHigh),serviceLow,serviceHigh);
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
