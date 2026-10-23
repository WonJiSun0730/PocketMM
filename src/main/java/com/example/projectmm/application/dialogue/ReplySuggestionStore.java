package com.example.projectmm.application.dialogue;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.*;
import java.util.List;
import java.util.function.Supplier;

/** Durable per-passage reservation prevents duplicate paid calls, including after restart. */
@Service
public class ReplySuggestionStore {
    private final Path root;
    private static final ObjectMapper JSON=new ObjectMapper();
    public ReplySuggestionStore(@Value("${pocket.reply-suggestion-directory:data/pocket-reply-suggestions}") String directory) {
        root=Path.of(directory).toAbsolutePath().normalize();
    }
    public record Result(String sceneKey,String status,List<String> suggestions,String errorCode,String message) {
        public Result(String sceneKey,String status,List<String> suggestions){this(sceneKey,status,suggestions,null,null);}
    }
    public record Diagnostic(String code,String exceptionType,String draftPayload) {}
    private static final org.slf4j.Logger LOG=org.slf4j.LoggerFactory.getLogger(ReplySuggestionStore.class);
    public Result getOrCreate(ReplySuggestionScene.Snapshot scene,Supplier<List<String>> generate) throws IOException {
        if(scene.sessionId()==null || !scene.key().matches("[a-f0-9]{64}"))throw new IllegalArgumentException("Invalid suggestion scene");
        var directory=root.resolve(scene.sessionId().toString());Files.createDirectories(directory);
        var file=directory.resolve(scene.key()+".json");
        var pending=new Result(scene.key(),"PENDING",List.of());
        try {Files.writeString(file,JSON.writeValueAsString(pending),StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE);}
        catch(FileAlreadyExistsException cached) {
            // An in-flight/crashed reservation must never trigger a second paid request.
            try {
                var result=JSON.readValue(Files.readString(file),Result.class);
                if("FAILED".equals(result.status()) && result.errorCode()==null)
                    return new Result(scene.key(),"FAILED",List.of(),"LEGACY_FAILURE",
                            "이전 실패의 원인이 기록되지 않았어요. 같은 지문은 재호출하지 않습니다.");
                return result;
            }
            catch(IOException|RuntimeException incompleteReservation){return pending;}
        }
        Result result;
        try {
            var drafts=List.copyOf(generate.get());
            result=new Result(scene.key(),drafts.size()==3?"READY":"PARTIAL",drafts,null,
                    drafts.size()==3?null:"사용 가능한 추천만 표시했어요. 추가 호출하지 않습니다.");
        } catch(RuntimeException failure) {
            var detail=failure instanceof ReplySuggestions.Failure known?known:null;
            String code=detail==null?"REQUEST_FAILED":detail.code();
            String message=detail==null?"추천 요청을 완료하지 못했어요.":detail.getMessage();
            result=new Result(scene.key(),"FAILED",List.of(),code,message);
            LOG.warn("Reply suggestion failed session={} scene={} code={} exceptionType={}",
                    scene.sessionId(),scene.key(),code,failure.getClass().getName());
            String draft=detail==null?null:detail.payload();
            if(draft!=null && draft.length()>16000)draft=draft.substring(0,16000);
            // Generated drafts only; never request prompts, credentials or provider envelopes.
            try {Files.writeString(directory.resolve(scene.key()+".failure.json"),
                    JSON.writeValueAsString(new Diagnostic(code,failure.getClass().getName(),draft)));}
            catch(IOException diagnosticFailure){LOG.warn("Reply suggestion diagnostic save failed session={}",scene.sessionId());}
        }
        Path temporary=Files.createTempFile(directory,"suggestion-",".tmp");
        try {
            Files.writeString(temporary,JSON.writeValueAsString(result));
            try {Files.move(temporary,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
            catch(AtomicMoveNotSupportedException unsupported){Files.move(temporary,file,StandardCopyOption.REPLACE_EXISTING);}
        } finally {Files.deleteIfExists(temporary);}
        return result;
    }
}
