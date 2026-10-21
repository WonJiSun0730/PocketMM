package com.example.projectmm.api.world;

import com.example.projectmm.application.chat.WorldRoomSessionService;
import com.example.projectmm.application.qa.ExperienceQaUiService;
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
@RequestMapping("/api/worlds/pockets/{sessionId}/experience-qa")
public class PocketExperienceQaController {
    private final ExperienceQaUiService qa;
    private final WorldRoomSessionService sessions;
    private String developer(Long sessionId,Authentication auth) {
        if(auth==null||!auth.isAuthenticated()||auth.getAuthorities().stream().noneMatch(a->"ROLE_DEVELOPER".equals(a.getAuthority())))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,"개발자만 자동 테스트를 사용할 수 있어요.");
        sessions.pocket(sessionId,auth.getName());return auth.getName();
    }
    @GetMapping("/capabilities") public ExperienceQaUiService.Capabilities capabilities(@PathVariable Long sessionId,Authentication auth) {
        developer(sessionId,auth);return qa.capabilities(sessionId,auth.getName());
    }
    @GetMapping("/runs") public List<ExperienceQaUiService.View> list(@PathVariable Long sessionId,Authentication auth) throws IOException {
        return qa.list(sessionId,developer(sessionId,auth));
    }
    @PostMapping("/runs") public ExperienceQaUiService.View start(@PathVariable Long sessionId,@RequestBody ExperienceQaUiService.Start request,Authentication auth) throws IOException {
        return qa.start(sessionId,developer(sessionId,auth),request);
    }
    @GetMapping("/runs/{id}") public ExperienceQaUiService.View current(@PathVariable Long sessionId,@PathVariable String id,Authentication auth) throws IOException {
        return qa.current(sessionId,developer(sessionId,auth),id);
    }
    @PostMapping("/runs/{id}/{command}") public ExperienceQaUiService.View control(@PathVariable Long sessionId,@PathVariable String id,@PathVariable String command,Authentication auth) throws IOException {
        return qa.control(sessionId,developer(sessionId,auth),id,command.toUpperCase(java.util.Locale.ROOT));
    }
    @GetMapping("/runs/{id}/report") public ResponseEntity<byte[]> report(@PathVariable Long sessionId,@PathVariable String id,
            @RequestParam(defaultValue="json") String format,Authentication auth) throws IOException {
        String owner=developer(sessionId,auth);
        String suffix=switch(format){case "json"->"json";case "markdown"->"md";case "turns","calls","metrics"->"csv";case "prompts"->"zip";default->throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"지원하지 않는 보고서 형식이에요.");};
        byte[] data=qa.download(sessionId,owner,id,format);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(format.equals("json")?"application/json":format.equals("markdown")?"text/markdown;charset=UTF-8":format.equals("prompts")?"application/zip":"text/csv;charset=UTF-8"))
                .header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=\""+id+"-"+format+"."+suffix+"\"")
                .header(HttpHeaders.CACHE_CONTROL,"no-store").body(data);
    }
    @GetMapping("/runs/{id}/analysis") public tools.jackson.databind.JsonNode analysis(@PathVariable Long sessionId,@PathVariable String id,Authentication auth) throws IOException {
        return qa.analysis(sessionId,developer(sessionId,auth),id);
    }
    @GetMapping("/runs/{id}/conversation") public tools.jackson.databind.JsonNode conversation(@PathVariable Long sessionId,@PathVariable String id,@RequestParam(defaultValue="0") int offset,@RequestParam(defaultValue="20") int limit,Authentication auth) throws IOException {
        return qa.conversation(sessionId,developer(sessionId,auth),id,offset,limit);
    }
    @PostMapping("/runs/{id}/reviews") public tools.jackson.databind.JsonNode review(@PathVariable Long sessionId,@PathVariable String id,@RequestBody ExperienceQaUiService.Review request,Authentication auth) throws IOException {
        return qa.review(sessionId,developer(sessionId,auth),id,request);
    }
}
