package com.example.projectmm.domain.state;

import com.example.projectmm.domain.character.Character;
import com.example.projectmm.domain.chat.Conversation;
import com.example.projectmm.domain.player.PlayerProfile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.*;

class CustomStateValueTest {
    private final PlayerProfile profile = new PlayerProfile(1, "Player", "Description", null);
    private final Conversation room = new Conversation(new Character("NPC"), LocalDateTime.of(2030, 1, 1, 12, 0));
    private final CustomStateDefinition definition =
            new CustomStateDefinition(profile, "피에타", 0, 100, 100, "%", "신앙");

    @Test
    void startsFromDefinitionDefault() {
        var value = new CustomStateValue(room, definition);
        assertThat(value.getCurrentValue()).isEqualTo(100);
        assertThat(value.getConversation()).isSameAs(room);
        assertThat(value.getDefinition()).isSameAs(definition);
        var mana = new CustomStateDefinition(profile, "마력", -10, 50, 12.5, null, null);
        assertThat(new CustomStateValue(room, mana).getCurrentValue()).isEqualTo(12.5);
    }

    @ParameterizedTest
    @ValueSource(doubles = {0, 67, 67.5, 100})
    void acceptsValuesIncludingBothBounds(double next) {
        var value = new CustomStateValue(room, definition);
        value.updateCurrentValue(next);
        assertThat(value.getCurrentValue()).isEqualTo(next);
        assertThat(definition.getDefaultValue()).isEqualTo(100);
    }

    @ParameterizedTest
    @ValueSource(doubles = {-0.01, 100.01, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY})
    void rejectsInvalidValuesWithoutChangingCurrentValue(double next) {
        var value = new CustomStateValue(room, definition);
        value.updateCurrentValue(67);
        assertThatIllegalArgumentException().isThrownBy(() -> value.updateCurrentValue(next));
        assertThat(value.getCurrentValue()).isEqualTo(67);
    }

    @Test
    void usesDefinitionRangeInsteadOfCoreStateRange() {
        var mana = new CustomStateDefinition(profile, "마력", -20, 200, 150, null, null);
        var value = new CustomStateValue(room, mana);
        value.updateCurrentValue(-10);
        assertThat(value.getCurrentValue()).isEqualTo(-10);
        value.updateCurrentValue(200);
        assertThat(value.getCurrentValue()).isEqualTo(200);
    }

    @Test
    void requiresConversationAndDefinition() {
        assertThatIllegalArgumentException().isThrownBy(() -> new CustomStateValue(null, definition));
        assertThatIllegalArgumentException().isThrownBy(() -> new CustomStateValue(room, null));
    }
}
