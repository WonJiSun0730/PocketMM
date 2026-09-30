package com.example.projectmm.domain.character;

import com.example.projectmm.domain.member.Member;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "characters")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Character {

    /**
     * Test/legacy compatibility constructor. Production character creation should supply an owner.
     */
    public Character(String name) {
        this.name = name;
    }

    public Character(String name, Member owner) {
        this.name = name;
        this.owner = owner;
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_member_id", nullable = false)
    private Member owner;
}
