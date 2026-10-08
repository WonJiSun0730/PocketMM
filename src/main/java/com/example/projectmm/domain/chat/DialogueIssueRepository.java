package com.example.projectmm.domain.chat;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;
import java.util.List;

public interface DialogueIssueRepository extends JpaRepository<DialogueIssue, Long> {
    List<DialogueIssue> findByOwnerMemberIdAndIdLessThanOrderByIdDesc(Long owner, Long before, Pageable page);
}
