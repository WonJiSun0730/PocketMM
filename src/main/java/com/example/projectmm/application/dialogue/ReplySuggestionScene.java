package com.example.projectmm.application.dialogue;

import com.example.projectmm.application.chat.WorldRoomSessionService;
import com.example.projectmm.domain.chat.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** Read-only snapshot, with no memory recall, model calls or scene advancement. */
@Component
@RequiredArgsConstructor
public class ReplySuggestionScene {
    private final WorldRoomSessionService sessions;
    private final WorldRoomMessageRepository messages;
    private final WorldRoomPlayerStateRepository players;
    public record Source(Long messageId,Integer variantNo,String content) {}
    public record Snapshot(Long sessionId,String key,String evidence) {}

    @Transactional(readOnly=true)
    public Snapshot prepare(Long worldId,Long roomId,Long sessionId,String loginId,Source source) {
        var session=sessions.ownedSession(roomId,sessionId,loginId);
        if(!Objects.equals(worldId,session.getRoom().getWorld().getId()))throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        var recent=messages.findTop15BySessionIdOrderByIdDesc(sessionId);
        var latest=recent.isEmpty()?null:recent.getFirst();
        if(latest!=null && (latest.getRole()!=ChatRole.CHARACTER
                || !Objects.equals(latest.getId(),source.messageId())
                || !Objects.equals(latest.getSelectedVariantNo(),source.variantNo())
                || !Objects.equals(latest.getContent(),source.content())))
            throw new ResponseStatusException(HttpStatus.CONFLICT,"장면이 바뀌었어요. 최신 대화를 확인해 주세요.");
        if(latest==null && source.messageId()!=null)throw new ResponseStatusException(HttpStatus.CONFLICT,"장면이 바뀌었어요.");
        String passage=latest==null?session.getIntroContentSnapshot():latest.getContent();
        String identity=sessionId+":"+(latest==null?"intro":latest.getId()+":"+latest.getSelectedVariantNo())+":"+passage;
        var data=new LinkedHashMap<String,Object>();
        var profile=session.getPlayerProfile();
        data.put("player",profile==null?Map.of():Map.of("name",profile.getName(),"description",clip(profile.getDescription(),500)));
        data.put("intro",clip(session.getIntroContentSnapshot(),1200));
        data.put("playerNote",clip(session.getUserNote(),800));
        data.put("currentPassage",clip(passage,6000));
        data.put("recentDialogue",recent.stream().skip(1).limit(4).toList().reversed().stream()
                .map(m->Map.of("role",m.getRole().name(),"content",clip(m.getContent(),1200))).toList());
        players.findBySessionId(sessionId).ifPresent(state->data.put("playerState",Map.of(
                "location",clip(state.getLocation(),200),"inventory",state.getInventory().stream().limit(20).map(v->clip(v,80)).toList(),
                "conditions",state.getConditions().stream().limit(10).map(v->clip(v,80)).toList())));
        return new Snapshot(sessionId,hash(identity),new ObjectMapper().writeValueAsString(data));
    }
    private static String clip(String value,int max) {return value==null?"":value.substring(0,Math.min(max,value.length()));}
    static String hash(String value) {
        try {return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}
        catch(java.security.NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}
    }
}
