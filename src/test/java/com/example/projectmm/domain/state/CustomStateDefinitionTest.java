package com.example.projectmm.domain.state;

import com.example.projectmm.domain.player.PlayerProfile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.*;

class CustomStateDefinitionTest {
    private final PlayerProfile profile = new PlayerProfile(1, "Player", "Description", null);

    @ParameterizedTest
    @CsvSource({"0,100,0", "0,100,100", "5,5,5", "-10.5,20.5,-0.5"})
    void acceptsInclusiveBoundsAndNegativeOrFractionalValues(double min, double max, double initial) {
        var definition = new CustomStateDefinition(profile, "피에타", min, max, initial, "%", "설명");
        assertThat(definition.getDefaultValue()).isEqualTo(initial);
    }

    @ParameterizedTest
    @CsvSource({"10,0,5", "0,100,-1", "0,100,101",
            "NaN,100,50", "0,NaN,50", "0,100,NaN",
            "-Infinity,100,50", "0,Infinity,50", "0,100,Infinity"})
    void rejectsInvalidRangesOnCreationAndLeavesDefinitionIntactOnFailedUpdate(double min, double max, double initial) {
        assertThatIllegalArgumentException().isThrownBy(() ->
                new CustomStateDefinition(profile, "피에타", min, max, initial, "%", "설명"));
        var definition = new CustomStateDefinition(profile, "피에타", 0, 100, 100, "%", "설명");
        assertThatIllegalArgumentException().isThrownBy(() ->
                definition.update("마력", min, max, initial, "MP", "변경"));
        assertThat(definition.getName()).isEqualTo("피에타");
        assertThat(definition.getMinValue()).isZero();
        assertThat(definition.getMaxValue()).isEqualTo(100);
        assertThat(definition.getDefaultValue()).isEqualTo(100);
        assertThat(definition.getUnit()).isEqualTo("%");
        assertThat(definition.getDescription()).isEqualTo("설명");
    }

    @Test
    void requiresOwnerAndName() {
        assertThatIllegalArgumentException().isThrownBy(() ->
                new CustomStateDefinition(null, "피에타", 0, 100, 100, null, null));
        for (String name : new String[]{null, "", " "}) {
            assertThatIllegalArgumentException().isThrownBy(() ->
                    new CustomStateDefinition(profile, name, 0, 100, 100, null, null));
        }
    }
}
