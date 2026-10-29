package com.example.projectmm.domain.memory;

import com.example.projectmm.infrastructure.security.EncryptedTextConverter;
import jakarta.persistence.Convert;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@EntityListeners(com.example.projectmm.infrastructure.security.ProtectedDetailFingerprintListener.class)
@Table(
        name = "protected_memory_detail",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_protected_memory_detail",
                columnNames = {
                        "memory_id",
                        "detail_hash"
                }
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProtectedMemoryDetail {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(
            fetch = FetchType.LAZY,
            optional = false
    )
    @JoinColumn(
            name = "memory_id",
            nullable = false
    )
    private Memory memory;

    @Convert(converter = EncryptedTextConverter.class)
    @Column(name = "detail_text",
            nullable = false, columnDefinition = "LONGTEXT")
    private String detailText;

    @Column(name = "detail_hash", length = 64, nullable = false)
    private String detailHash;

    public void assignDetailFingerprint(String fingerprint) { this.detailHash = fingerprint; }

    @Enumerated(EnumType.STRING)
    @Column(
            name = "salience",
            nullable = false,
            length = 16
    )
    private MemoryDetailSalience salience;

    @Column(
            name = "protected_anchor",
            nullable = false
    )
    private boolean protectedAnchor;

    public ProtectedMemoryDetail(
            Memory memory,
            String detailText,
            MemoryDetailSalience salience,
            boolean protectedAnchor
    ) {
        if (memory == null) {
            throw new IllegalArgumentException(
                    "memory is required"
            );
        }

        if (detailText == null
                || detailText.isBlank()
                || detailText.length() > 300) {
            throw new IllegalArgumentException(
                    "detailText must be 1..300 characters"
            );
        }

        if (salience == null) {
            throw new IllegalArgumentException(
                    "salience is required"
            );
        }

        this.memory = memory;
        this.detailText = detailText.strip();
        this.salience = salience;
        this.protectedAnchor =
                protectedAnchor
                        || salience.protectedByDefault();
    }

    public boolean mustPreserve() {
        return protectedAnchor
                || salience.protectedByDefault();
    }
}
