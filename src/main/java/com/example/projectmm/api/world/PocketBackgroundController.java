package com.example.projectmm.api.world;

import com.example.projectmm.application.chat.PocketBackgroundService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/worlds/pockets/{sessionId}/backgrounds")
public class PocketBackgroundController {
    private final PocketBackgroundService backgrounds;
    public record Request(String location, String simulatedAt) {}
    public record Configuration(boolean automatic, String location, String simulatedAt) {}
    @GetMapping
    public PocketBackgroundService.Result resolve(@PathVariable long sessionId, @RequestParam String location, @RequestParam(required=false) String simulatedAt, Authentication auth) {
        return backgrounds.resolve(backgrounds.context(sessionId, auth.getName()).at(simulatedAt), location);
    }
    @PostMapping
    public ResponseEntity<PocketBackgroundService.Result> generate(@PathVariable long sessionId, @RequestBody Request request, Authentication auth) {
        var result = backgrounds.request(backgrounds.context(sessionId, auth.getName()).at(request.simulatedAt()), request.location());
        return ResponseEntity.status(result.status().equals("GENERATING") ? HttpStatus.ACCEPTED : HttpStatus.OK).body(result);
    }
    @PutMapping("/configuration")
    public PocketBackgroundService.Result configure(@PathVariable long sessionId, @RequestBody Configuration config, Authentication auth) {
        return backgrounds.configure(backgrounds.context(sessionId, auth.getName()).at(config.simulatedAt()), config.automatic(), config.location());
    }
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public PocketBackgroundService.Result upload(@PathVariable long sessionId, @RequestParam String location,
            @RequestParam(defaultValue = "") String keywords, @RequestParam MultipartFile background,
            @RequestParam MultipartFile scenery, @RequestParam(required=false) String simulatedAt, Authentication auth) throws IOException {
        var context = backgrounds.context(sessionId, auth.getName()).at(simulatedAt);
        return backgrounds.upload(context, location, keywords, background.getBytes(), scenery.getBytes());
    }
    @GetMapping("/images/{id}/{layer}")
    public ResponseEntity<FileSystemResource> image(@PathVariable long sessionId, @PathVariable String id,
            @PathVariable String layer, Authentication auth) {
        var file = backgrounds.image(backgrounds.context(sessionId, auth.getName()), id, layer);
        return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG)
                .cacheControl(CacheControl.maxAge(java.time.Duration.ofDays(7)).cachePrivate())
                .body(new FileSystemResource(file));
    }
}
