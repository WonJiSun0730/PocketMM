package com.example.projectmm.api.world;

import com.example.projectmm.application.world.WorldPublicationService;
import com.example.projectmm.application.world.WorldCoverService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;
import org.springframework.http.*;
import org.springframework.core.io.FileSystemResource;
import org.springframework.web.multipart.MultipartFile;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.io.IOException;
import java.util.List;

@RestController
@RequiredArgsConstructor
public class WorldPublicationController {
    private final WorldPublicationService publication;
    private final WorldCoverService covers;
    public record Metadata(@Size(max = 10) List<@NotNull @Size(max = 31) String> hashtags) {}
    public record Start(@NotNull Long introId, Long playerProfileId) {}

    @GetMapping("/api/published-worlds")
    public WorldPublicationService.Listing list(@RequestParam(defaultValue = "0") int page) { return publication.list(page); }
    @GetMapping("/api/published-worlds/{id}")
    public WorldPublicationService.Detail detail(@PathVariable Long id) { return publication.detail(id); }
    @PostMapping("/api/published-worlds/{id}/start")
    @ResponseStatus(HttpStatus.CREATED)
    public WorldPublicationService.Started start(@PathVariable Long id, @Valid @RequestBody Start request, Authentication auth) {
        return publication.start(id, auth.getName(), request.introId(), request.playerProfileId());
    }
    @GetMapping("/api/worlds/{id}/publication")
    public WorldPublicationService.Settings settings(@PathVariable Long id, Authentication auth) { return publication.settings(id, auth.getName()); }
    @PutMapping("/api/worlds/{id}/publication")
    public WorldPublicationService.Settings metadata(@PathVariable Long id, @Valid @RequestBody Metadata request, Authentication auth) { return publication.update(id, auth.getName(), request.hashtags()); }
    @PostMapping("/api/worlds/{id}/publication")
    public WorldPublicationService.Settings publish(@PathVariable Long id, Authentication auth) { return publication.publish(id, auth.getName()); }
    @DeleteMapping("/api/worlds/{id}/publication")
    public WorldPublicationService.Settings unpublish(@PathVariable Long id, Authentication auth) { return publication.unpublish(id, auth.getName()); }
    @PostMapping(value = "/api/worlds/{id}/cover", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public java.util.Map<String, String> upload(@PathVariable Long id, @RequestParam MultipartFile cover, Authentication auth) throws IOException {
        return java.util.Map.of("coverImageUrl", covers.upload(id, auth.getName(), cover));
    }
    @GetMapping("/api/published-worlds/{id}/cover/{filename}")
    public ResponseEntity<FileSystemResource> image(@PathVariable Long id, @PathVariable String filename, Authentication auth) {
        var path = covers.image(id, auth.getName(), filename);
        return ResponseEntity.ok().contentType(filename.endsWith(".png") ? MediaType.IMAGE_PNG : MediaType.IMAGE_JPEG)
                .cacheControl(CacheControl.noStore()).header("X-Content-Type-Options", "nosniff").body(new FileSystemResource(path));
    }
}
