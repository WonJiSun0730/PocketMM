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
    public record Result(String sceneKey,String status,List<String> suggestions) {}
    public Result getOrCreate(ReplySuggestionScene.Snapshot scene,Supplier<List<String>> generate) throws IOException {
        if(scene.sessionId()==null || !scene.key().matches("[a-f0-9]{64}"))throw new IllegalArgumentException("Invalid suggestion scene");
        var directory=root.resolve(scene.sessionId().toString());Files.createDirectories(directory);
        var file=directory.resolve(scene.key()+".json");
        var pending=new Result(scene.key(),"PENDING",List.of());
        try {Files.writeString(file,JSON.writeValueAsString(pending),StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE);}
        catch(FileAlreadyExistsException cached) {
            // An in-flight/crashed reservation must never trigger a second paid request.
            try {return JSON.readValue(Files.readString(file),Result.class);}
            catch(IOException|RuntimeException incompleteReservation){return pending;}
        }
        Result result;
        try {result=new Result(scene.key(),"READY",List.copyOf(generate.get()));}
        catch(RuntimeException failure) {result=new Result(scene.key(),"FAILED",List.of());}
        Path temporary=Files.createTempFile(directory,"suggestion-",".tmp");
        try {
            Files.writeString(temporary,JSON.writeValueAsString(result));
            try {Files.move(temporary,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
            catch(AtomicMoveNotSupportedException unsupported){Files.move(temporary,file,StandardCopyOption.REPLACE_EXISTING);}
        } finally {Files.deleteIfExists(temporary);}
        return result;
    }
}
