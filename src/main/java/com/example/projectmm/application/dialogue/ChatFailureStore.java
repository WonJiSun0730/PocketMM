package com.example.projectmm.application.dialogue;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;
import java.nio.file.*;
import java.io.IOException;
import java.time.Instant;
import java.util.*;

/** Private, bounded evidence separate from the rolled-back conversation. No provider envelope. */
@Service
public class ChatFailureStore {
    private final Path root;
    private static final ObjectMapper JSON=new ObjectMapper();
    public ChatFailureStore(@Value("${pocket.chat-failure-directory:data/pocket-chat-failures}") String directory) {
        root=Path.of(directory).toAbsolutePath().normalize();
    }
    public record Evidence(String requestId,Long sessionId,String createdAt,String phase,String mode,
                           Boolean approved,List<String> reasons,List<String> conflicts,String candidate,boolean truncated) {}
    public static Evidence evidence(Throwable failure,String requestId,Long sessionId,String phase,String candidate) {
        var seen=Collections.newSetFromMap(new IdentityHashMap<Throwable,Boolean>());
        for(Throwable cause=failure;cause!=null && seen.add(cause) && seen.size()<=8;cause=cause.getCause()) {
            if(!(cause instanceof SceneContinuityService.ReviewRejectedException rejected))continue;
            if(rejected.candidate()!=null)candidate=rejected.candidate().reply();
            var review=rejected.review();var reasons=new ArrayList<String>();
            if(review==null)reasons.add("검사 결과가 없습니다.");
            else {
                if(!review.approved())reasons.add("검사기가 승인하지 않았습니다.");
                if(review.changes()==null)reasons.add("필수 사실 변경 목록이 누락됐습니다.");
                if(review.conflicts()==null)reasons.add("필수 충돌 목록이 누락됐습니다.");
                else if(!review.conflicts().isEmpty())reasons.add("검사기가 충돌을 보고했습니다.");
            }
            var raw=review==null||review.conflicts()==null?List.<String>of():review.conflicts();
            boolean truncated=raw.size()>20 || raw.stream().filter(Objects::nonNull).anyMatch(s->s.length()>2000)
                    || (candidate!=null && candidate.length()>32000);
            var conflicts=raw.stream().filter(Objects::nonNull).limit(20).map(s->clip(s,2000)).toList();
            return new Evidence(requestId,sessionId,Instant.now().toString(),phase,rejected.mode(),
                    review==null?null:review.approved(),List.copyOf(reasons),conflicts,clip(candidate,32000),truncated);
        }
        return null;
    }
    private static String clip(String text,int max) {
        if(text==null)return "";
        int end=Math.min(text.length(),max);
        if(end<text.length() && end>0 && Character.isHighSurrogate(text.charAt(end-1)))end--;
        return text.substring(0,end);
    }
    public void save(Evidence evidence) throws IOException {
        UUID.fromString(evidence.requestId());
        Path directory=root.resolve(evidence.sessionId().toString());Files.createDirectories(directory);
        Path temporary=Files.createTempFile(directory,"pending-",".tmp");
        try {
            Files.writeString(temporary,JSON.writeValueAsString(evidence));
            Path destination=directory.resolve(System.currentTimeMillis()+"-"+evidence.requestId()+".json");
            try {Files.move(temporary,destination,StandardCopyOption.ATOMIC_MOVE);}
            catch(AtomicMoveNotSupportedException unsupported){Files.move(temporary,destination);}
        } finally {Files.deleteIfExists(temporary);}
    }
    /** The controller must validate ownership and world/room scope before calling this. */
    public List<Evidence> latest(Long sessionId) throws IOException {
        Path directory=root.resolve(sessionId.toString());if(!Files.isDirectory(directory))return List.of();
        var result=new ArrayList<Evidence>();
        try(var paths=Files.list(directory)) {
            for(Path file:paths.filter(p->p.getFileName().toString().matches("[0-9]+-[0-9a-f-]{36}\\.json"))
                    .sorted(Comparator.comparing((Path p)->p.getFileName().toString()).reversed()).limit(10).toList()) {
                if(Files.size(file)>600_000)continue;
                var evidence=JSON.readValue(Files.readString(file),Evidence.class);
                if(sessionId.equals(evidence.sessionId()))result.add(evidence);
            }
        }
        return List.copyOf(result);
    }
}
