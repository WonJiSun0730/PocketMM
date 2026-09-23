package com.example.projectmm.domain.state;

import com.example.projectmm.domain.chat.Conversation;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "custom_state_values", uniqueConstraints = @UniqueConstraint(
        name = "uk_custom_state_value_conversation_definition",
        columnNames = {"conversation_id", "definition_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CustomStateValue {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "conversation_id", nullable = false)
    private Conversation conversation;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "definition_id", nullable = false)
    private CustomStateDefinition definition;

    @Column(nullable = false)
    private double currentValue;

    public CustomStateValue(Conversation conversation, CustomStateDefinition definition) {
        if (conversation == null || definition == null) {
            throw new IllegalArgumentException("Conversation and custom state definition are required.");
        }
        this.conversation = conversation;
        this.definition = definition;
        updateCurrentValue(definition.getDefaultValue());
    }

    public void updateCurrentValue(double currentValue) {
        if (!Double.isFinite(currentValue)
                || currentValue < definition.getMinValue() || currentValue > definition.getMaxValue()) {
            throw new IllegalArgumentException("Current value must be finite and within the definition range.");
        }
        // 검증 실패 시 기존 방의 현재값을 유지한다.
        this.currentValue = currentValue;
    }
}
