package com.example.projectmm.api.world;

import com.example.projectmm.application.chat.WorldRoomSessionService;
import com.example.projectmm.application.qa.QaUiService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.io.IOException;
import java.util.List;

@RestController
@RequiredArgsConstructor
@Profile("(!prod & !production) | qa-ui")
@ConditionalOnProperty(name="app.qa-ui.enabled",havingValue="true")
@RequestMapping("/api/worlds/pockets/{sessionId}/qa")
public class PocketQaController {
    private final QaUiService qa;
    private final WorldRoomSessionService sessions;
    private String developer(Long sessionId,Authentication auth) {
        if(auth==null||!auth.isAuthenticated()||auth.getAuthorities().stream().noneMatch(a->"ROLE_DEVELOPER".equals(a.getAuthority())))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,"개발자만 자동 테스트를 사용할 수 있어요.");
        sessions.pocket(sessionId,auth.getName());return auth.getName();
    }
    @GetMapping("/capabilities") public QaUiService.Capabilities capabilities(@PathVariable Long sessionId,Authentication auth) {
        developer(sessionId,auth);return qa.capabilities();
    }
    @GetMapping("/runs") public List<QaUiService.View> list(@PathVariable Long sessionId,Authentication auth) throws IOException {
        return qa.list(sessionId,developer(sessionId,auth));
    }
    @PostMapping("/runs") public QaUiService.View start(@PathVariable Long sessionId,@RequestBody QaUiService.Start request,Authentication auth) throws IOException {
        return qa.start(sessionId,developer(sessionId,auth),request);
    }
    @GetMapping("/runs/{id}") public QaUiService.View current(@PathVariable Long sessionId,@PathVariable String id,Authentication auth) throws IOException {
        return qa.current(sessionId,developer(sessionId,auth),id);
    }
    @PostMapping("/runs/{id}/{command}") public QaUiService.View control(@PathVariable Long sessionId,@PathVariable String id,@PathVariable String command,Authentication auth) throws IOException {
        return qa.control(sessionId,developer(sessionId,auth),id,command.toUpperCase(java.util.Locale.ROOT));
    }
    @GetMapping("/runs/{id}/report") public ResponseEntity<byte[]> report(@PathVariable Long sessionId,@PathVariable String id,
            @RequestParam(defaultValue="json") String format,Authentication auth) throws IOException {
        String owner=developer(sessionId,auth);
        String suffix=switch(format){case "json"->"json";case "markdown"->"md";case "turns","calls"->"csv";default->throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"지원하지 않는 보고서 형식이에요.");};
        byte[] data=qa.download(sessionId,owner,id,format);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(format.equals("json")?"application/json":format.equals("markdown")?"text/markdown;charset=UTF-8":"text/csv;charset=UTF-8"))
                .header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=\""+id+"-"+format+"."+suffix+"\"")
                .header(HttpHeaders.CACHE_CONTROL,"no-store").body(data);
    }
}
