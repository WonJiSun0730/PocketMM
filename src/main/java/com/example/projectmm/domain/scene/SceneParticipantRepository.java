package com.example.projectmm.domain.scene;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SceneParticipantRepository
        extends JpaRepository<SceneParticipant, Long> {
    List<SceneParticipant>
    findByCharacterIdAndSceneActiveTrue(Long characterId);


}