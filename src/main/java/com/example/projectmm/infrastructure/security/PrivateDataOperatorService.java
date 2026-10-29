package com.example.projectmm.infrastructure.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;

/** Exact owner AND room scope only; no global/full-text search or unrestricted dump endpoint. */
@Service
public class PrivateDataOperatorService {
    public enum Target {
        CHAT("chat_messages t join characters c on c.id=t.character_id", "c.owner_member_id", "t.conversation_id", "t.content"),
        POCKET("world_room_messages t join world_room_sessions s on s.id=t.session_id", "s.member_id", "t.session_id", "t.content"),
        VARIANT("world_room_message_variants t join world_room_messages m on m.id=t.message_id join world_room_sessions s on s.id=m.session_id", "s.member_id", "s.id", "t.content"),
        POCKET_MEMORY("memories t join world_room_sessions s on s.id=t.world_room_session_id", "s.member_id", "t.world_room_session_id", "t.summary"),
        CHAT_MEMORY("memories t join characters c on c.id=t.character_id",
                "case when t.world_room_session_id is null then c.owner_member_id else null end", "t.conversation_id", "t.summary");
        final String from, owner, scope, text;
        Target(String from, String owner, String scope, String text) { this.from=from;this.owner=owner;this.scope=scope;this.text=text; }
    }
    public record Request(Target target, long ownerId, long scopeId, Long before, List<Long> ids, String reason) {}
    public record Metadata(long id) {}
    public record Disclosure(long id, String text) {}
    private final JdbcTemplate jdbc;
    private final StoredTextCipher cipher;
    private final PrivateDataAudit audit;
    private final Set<String> operators;
    public PrivateDataOperatorService(JdbcTemplate jdbc, StoredTextCipher cipher, PrivateDataAudit audit,
            @Value("${POCKETMM_PRIVATE_DATA_OPERATORS:}") String operators) {
        this.jdbc=jdbc;this.cipher=cipher;this.audit=audit;
        this.operators=new HashSet<>(Arrays.asList(operators.split(",")));
        this.operators.removeIf(String::isBlank);
    }
    private void authorize(Authentication auth) {
        if (auth == null || auth instanceof AnonymousAuthenticationToken || !auth.isAuthenticated())
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        if (!operators.contains(auth.getName()) || auth.getAuthorities().stream()
                .noneMatch(a -> Set.of("ROLE_ADMIN","ROLE_DEVELOPER").contains(a.getAuthority()))) {
            try {
                audit.record(UUID.randomUUID().toString(),auth.getName(),"ACCESS","UNKNOWN",0,0,"",
                        "Operator authorization denied","DENIED",0);
            } catch (RuntimeException ignored) { /* Disclosure remains forbidden if auditing is unavailable. */ }
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
    }
    private void validate(Request r, boolean decrypt) {
        if (r == null || r.target()==null || r.ownerId()<=0 || r.scopeId()<=0 || (r.before()!=null && r.before()<=0)
                || r.reason()==null || r.reason().strip().length()<10 || r.reason().length()>500
                || (decrypt && (r.ids()==null || r.ids().isEmpty() || r.ids().size()>20
                    || r.ids().stream().anyMatch(id->id==null || id<=0)
                    || new HashSet<>(r.ids()).size()!=r.ids().size())))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Exact scope and a 10-500 character reason are required; maximum 20 ids");
    }
    public List<Metadata> search(Authentication auth, Request r) {
        authorize(auth); validate(r,false);
        String requestId=UUID.randomUUID().toString();
        record(requestId,auth,r,"SEARCH","","REQUESTED",0);
        try {
            Target t=r.target();
            var result=jdbc.query("select t.id from "+t.from+" where "+t.owner+"=? and "+t.scope+"=? and t.id<? order by t.id desc limit 20",
                    (rs,n)->new Metadata(rs.getLong(1)),r.ownerId(),r.scopeId(),r.before()==null?Long.MAX_VALUE:r.before());
            record(requestId,auth,r,"SEARCH","","SUCCESS",result.size());
            return result;
        } catch (RuntimeException ex) {
            record(requestId,auth,r,"SEARCH","","FAILED",0);
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Private data operation failed");
        }
    }
    public List<Disclosure> decrypt(Authentication auth, Request r) {
        authorize(auth); validate(r,true);
        String requestId=UUID.randomUUID().toString(), ids=r.ids().toString();
        record(requestId,auth,r,"DECRYPT",ids,"REQUESTED",0);
        try {
            Target t=r.target();
            var args=new ArrayList<Object>();args.add(r.ownerId());args.add(r.scopeId());args.addAll(r.ids());
            var result=jdbc.query("select t.id,"+t.text+" from "+t.from+" where "+t.owner+"=? and "+t.scope+"=? and t.id in ("+
                    String.join(",",Collections.nCopies(r.ids().size(),"?"))+") order by t.id",
                    (rs,n)->new Disclosure(rs.getLong(1),cipher.decrypt(rs.getString(2))),args.toArray());
            if(result.size()!=r.ids().size()) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
            record(requestId,auth,r,"DECRYPT",ids,"SUCCESS",result.size());
            return result;
        } catch (RuntimeException ex) {
            record(requestId,auth,r,"DECRYPT",ids,"FAILED",0);
            if (ex instanceof ResponseStatusException status) throw status;
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Private data operation failed");
        }
    }
    private void record(String id,Authentication auth,Request r,String action,String ids,String outcome,int count) {
        audit.record(id,auth.getName(),action,r.target().name(),r.ownerId(),r.scopeId(),ids,r.reason().strip(),outcome,count);
    }
}
