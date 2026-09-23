package com.example.projectmm.domain.player;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface PlayerProfileRepository extends JpaRepository<PlayerProfile, Long> {
    List<PlayerProfile> findAllByOrderBySlotAsc();
}
