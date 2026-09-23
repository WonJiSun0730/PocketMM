package com.example.projectmm.application.player;

import com.example.projectmm.domain.player.*;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.util.Base64;
import java.util.List;
import java.util.stream.IntStream;

@Service
@RequiredArgsConstructor
public class PlayerProfileService {
    private final PlayerProfileRepository profiles;

    @Transactional(readOnly = true)
    public List<PlayerProfileResponse> list() {
        return profiles.findAllByOrderBySlotAsc().stream().map(PlayerProfileResponse::from).toList();
    }

    @Transactional
    public PlayerProfileResponse save(Long id, PlayerProfileRequest request) {
        validate(request);
        PlayerProfile profile;
        if (id == null) {
            var used = profiles.findAllByOrderBySlotAsc().stream().map(PlayerProfile::getSlot).toList();
            int slot = IntStream.rangeClosed(1, 10).filter(n -> !used.contains(n)).findFirst()
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "프로필은 최대 10개까지 만들 수 있습니다."));
            profile = new PlayerProfile(slot, request.name().strip(), request.description().strip(), request.avatar());
        } else {
            profile = profiles.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
            profile.update(request.name().strip(), request.description().strip(), request.avatar());
        }
        try {
            return PlayerProfileResponse.from(profiles.saveAndFlush(profile));
        } catch (DataIntegrityViolationException ex) {
            // 동시 생성으로 같은 슬롯을 골랐을 때 10개 제한을 넘기지 않고 재시도를 안내한다.
            throw new ResponseStatusException(HttpStatus.CONFLICT, "목록이 변경되었습니다. 다시 불러온 뒤 저장해 주세요.");
        }
    }

    private void validate(PlayerProfileRequest request) {
        if (request == null || request.name() == null || request.name().isBlank() || request.name().length() > 40
                || request.description() == null || request.description().isBlank()
                || request.name().length() + request.description().length() > 500) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "이름은 40자 이내, 이름과 설정은 합쳐 500자 이내로 입력해 주세요.");
        }
        String avatar = request.avatar();
        if (avatar == null || avatar.isBlank()) return;
        // 외부 이미지 URL과 SVG는 받지 않고 크기가 제한된 이미지 데이터만 저장한다.
        if (avatar.length() > 400000 || !avatar.matches("data:image/(png|jpeg|webp);base64,[A-Za-z0-9+/=]+")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "PNG/JPEG/WebP 이미지만 사용할 수 있습니다.");
        }
        try {
            byte[] bytes = Base64.getDecoder().decode(avatar.substring(avatar.indexOf(',') + 1));
            if (bytes.length > 280000 || bytes.length < 12) throw new IllegalArgumentException();
            boolean png = bytes[0] == (byte) 0x89 && bytes[1] == 'P' && bytes[2] == 'N' && bytes[3] == 'G';
            boolean jpeg = bytes[0] == (byte) 0xff && bytes[1] == (byte) 0xd8 && bytes[2] == (byte) 0xff;
            boolean webp = bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == 'F'
                    && bytes[8] == 'W' && bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P';
            if (!(avatar.startsWith("data:image/png;") && png
                    || avatar.startsWith("data:image/jpeg;") && jpeg
                    || avatar.startsWith("data:image/webp;") && webp)) throw new IllegalArgumentException();
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "이미지 파일을 확인해 주세요. 최대 280KB입니다.");
        }
    }
}
