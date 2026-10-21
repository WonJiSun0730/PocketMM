package com.example.projectmm.domain.chat;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WorldRoomIntroRepository extends JpaRepository<WorldRoomIntro, Long> {
    List<WorldRoomIntro> findByRoomWorldIdOrderByRoomIdAscSortOrderAscIdAsc(Long worldId);
    List<WorldRoomIntro> findByRoomIdOrderBySortOrderAscIdAsc(Long roomId);
    Optional<WorldRoomIntro> findByIdAndRoomId(Long id, Long roomId);
    void deleteByRoomId(Long roomId);
}
