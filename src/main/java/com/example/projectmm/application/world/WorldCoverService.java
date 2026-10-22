package com.example.projectmm.application.world;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.nio.file.*;
import java.io.*;
import java.util.UUID;
import javax.imageio.ImageIO;

@Service
@RequiredArgsConstructor
public class WorldCoverService {
    private final WorldPublicationService publication;
    @Value("${world.cover.directory:./data/world-covers}") private String directory;

    @Transactional(rollbackFor = IOException.class)
    public String upload(Long id, String login, MultipartFile file) throws IOException {
        var world = publication.owned(id, login);
        if (file.isEmpty() || file.getSize() > 3 * 1024 * 1024) throw bad("표지는 PNG/JPEG 3MB 이하로 선택해 주세요.");
        String extension;
        try (var input = ImageIO.createImageInputStream(file.getInputStream())) {
            if (input == null) throw bad("이미지 파일을 확인해 주세요.");
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw bad("PNG/JPEG 이미지를 선택해 주세요.");
            var reader = readers.next();
            try {
                reader.setInput(input);
                String format = reader.getFormatName();
                extension = format.equalsIgnoreCase("png") ? "png" : format.equalsIgnoreCase("jpeg") ? "jpg" : null;
                if (extension == null || reader.getWidth(0) > 4096 || reader.getHeight(0) > 4096) throw bad("PNG/JPEG 이미지를 가로·세로 4096px 이하로 선택해 주세요.");
            } finally { reader.dispose(); }
        }
        var folder = Path.of(directory).toAbsolutePath().normalize().resolve(id.toString());
        Files.createDirectories(folder);
        String filename = UUID.randomUUID() + "." + extension;
        try (var stream = file.getInputStream()) { Files.copy(stream, folder.resolve(filename)); }
        String url = "/api/published-worlds/" + id + "/cover/" + filename;
        world.updateCoverImageUrl(url);
        return url;
    }

    @Transactional(readOnly = true)
    public Path image(Long id, String login, String filename) {
        try { publication.owned(id, login); }
        catch (ResponseStatusException ex) { publication.published(id); }
        if (!filename.matches("[a-f0-9-]{36}\\.(png|jpg)")) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        var path = Path.of(directory).toAbsolutePath().normalize().resolve(id.toString()).resolve(filename);
        if (!Files.isRegularFile(path)) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        return path;
    }
    private ResponseStatusException bad(String text) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, text); }
}
