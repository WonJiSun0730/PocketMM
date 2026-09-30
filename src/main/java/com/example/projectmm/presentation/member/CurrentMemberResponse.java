package com.example.projectmm.presentation.member;

public record CurrentMemberResponse(
        Long id,
        String loginId,
        String name,
        String email,
        String role
) {
}
