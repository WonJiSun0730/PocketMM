package com.example.projectmm.domain.memory;

import com.example.projectmm.infrastructure.security.EncryptedTextConverter;
import jakarta.persistence.Convert;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.AccessLevel;

/** 사용자 발언의 검색용 구조. 객관적 사실을 확정하거나 원문 근거를 대체하지 않는다. */
@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MemoryFact {
    public enum Kind { IDENTITY, PREFERENCE, RELATIONSHIP, CONSTRAINT, PLAN, REPORTED_EVENT }

    @Enumerated(EnumType.STRING)
    @Column(name = "fact_kind", length = 32)
    private Kind kind;
    @Convert(converter = EncryptedTextConverter.class)
    @Column(name = "fact_subject", columnDefinition = "LONGTEXT")
    private String subject;
    @Convert(converter = EncryptedTextConverter.class)
    @Column(name = "fact_relation", columnDefinition = "LONGTEXT")
    private String relation;
    @Convert(converter = EncryptedTextConverter.class)
    @Column(name = "fact_value", columnDefinition = "LONGTEXT")
    private String value;

    public MemoryFact(Kind kind, String subject, String relation, String value) {
        this.kind = kind;
        this.subject = subject;
        this.relation = relation;
        this.value = value;
    }

    public boolean groundedIn(String quote) {
        // 문자열 출현만 검사한다. 동일 인용 안의 인물 간 관계나 부정·시제 의미까지 입증하는 검증은 아니다.
        return kind != null && quote != null && part(subject, 120, quote)
                && part(relation, 120, quote) && part(value, 400, quote);
    }

    private boolean part(String text, int max, String quote) {
        return text != null && !text.isBlank() && text.equals(text.strip())
                && text.length() <= max && quote.contains(text);
    }
}
