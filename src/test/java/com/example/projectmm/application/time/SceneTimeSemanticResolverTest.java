package com.example.projectmm.application.time;

import com.example.projectmm.domain.time.AiTimeParseResult;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import static org.assertj.core.api.Assertions.assertThat;

class SceneTimeSemanticResolverTest {
    private final TimeChangeResolver resolver = new TimeChangeResolver();
    private final LocalDateTime current = LocalDateTime.of(2026, 10, 8, 15, 32);

    @Test
    void explicitSemanticClockCanCorrectEarlierTimeAndDoesNotGetApproximateVariation() {
        var target = current.withMinute(25);
        var result = parse(target, .95);
        assertThat(resolver.resolve(result, current).newTime()).isEqualTo(target);
        assertThat(resolver.resolve(parse(target.plusDays(3), .95), current).newTime()).isEqualTo(target.plusDays(3));
        assertThat(resolver.resolve(parse(target, .5), current).changed()).isFalse();
        assertThat(resolver.resolve(parse(null, .95), current).changed()).isFalse();
    }

    @Test
    void additionalApproximateSleepIsBoundedAndReproducible() {
        var result = new AiTimeParseResult(true, AiTimeParseResult.TimeType.RELATIVE, 6L,
                AiTimeParseResult.TimeUnit.HOUR, null, AiTimeParseResult.TimePrecision.APPROXIMATE,
                .95, "이후 6시간정도 더 잤다", AiTimeParseResult.DurationScale.UNSPECIFIED);
        var resolved = resolver.resolve(result, current);
        assertThat(resolved.newTime()).isBetween(current.plusMinutes(350), current.plusMinutes(370));
        assertThat(resolver.resolve(result, current)).isEqualTo(resolved);
    }

    private AiTimeParseResult parse(LocalDateTime target, double confidence) {
        return new AiTimeParseResult(true, AiTimeParseResult.TimeType.SCENE_TIME, null, null, null,
                AiTimeParseResult.TimePrecision.EXACT, confidence, "돌아오니 3시 25분이다",
                AiTimeParseResult.DurationScale.UNSPECIFIED, target);
    }
}
