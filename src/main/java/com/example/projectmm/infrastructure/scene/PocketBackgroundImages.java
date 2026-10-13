package com.example.projectmm.infrastructure.scene;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;

/** Raster normalization for runtime assets, not a model-dependent alpha promise. */
public final class PocketBackgroundImages {
    public static final int WIDTH = 240, HEIGHT = 160;
    private PocketBackgroundImages() {}
    public static byte[] prepare(byte[] bytes, boolean scenery, boolean chromaKey) {
        if (bytes == null || bytes.length == 0 || bytes.length > 10 * 1024 * 1024) throw invalid();
        try (var input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw invalid();
            var reader = readers.next();
            BufferedImage source;
            try {
                if (!java.util.Set.of("png", "jpeg", "jpg").contains(reader.getFormatName().toLowerCase(java.util.Locale.ROOT))) throw invalid();
                reader.setInput(input);
                int w = reader.getWidth(0), h = reader.getHeight(0);
                if (w < 24 || h < 24 || w > 4096 || h > 4096 || (long) w * h > 8_000_000) throw invalid();
                if (Math.abs((double) w / h - 1.5) > .12) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "배경과 원경은 가로:세로 3:2 비율로 준비해 주세요.");
                source = reader.read(0);
            } finally { reader.dispose(); }
            var image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB);
            int transparent = 0, occupied = 0;
            for (int y = 0; y < HEIGHT; y++) for (int x = 0; x < WIDTH; x++) {
                // Nearest-neighbour sampling avoids blurred pixel edges and preserves alpha holes.
                int rgba = source.getRGB(x * source.getWidth() / WIDTH, y * source.getHeight() / HEIGHT);
                int red = (rgba >>> 16) & 255, green = (rgba >>> 8) & 255, blue = rgba & 255;
                // Generated props may contain dark key-colour spill, not just flat bright magenta.
                // Purple is forbidden in AI overlay objects; uploaded alpha art is left untouched.
                if (scenery && chromaKey && red > 35 && blue > 35
                        && Math.min(red, blue) - green > 20 && green < Math.min(red, blue) * .65) rgba = 0;
                if (!scenery) rgba |= 0xff000000;
                if ((rgba >>> 24) < 32) transparent++; else occupied++;
                image.setRGB(x, y, rgba);
            }
            if (scenery && (transparent < WIDTH * HEIGHT / 10 || occupied < WIDTH * HEIGHT / 100))
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "원경의 빈 공간은 투명해야 하고, 보여줄 그림이 일부 있어야 해요.");
            var output = new ByteArrayOutputStream();
            ImageIO.write(image, "png", output);
            return output.toByteArray();
        } catch (IOException e) { throw invalid(); }
    }
    private static ResponseStatusException invalid() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "10MB 이하의 PNG 또는 JPEG 이미지를 확인해 주세요.");
    }
}
