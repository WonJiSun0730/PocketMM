package com.example.projectmm.infrastructure.dialogue;

import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.EncodingType;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

/** Reproducible local text estimate; never contacts providers or resets the QA budget. */
class PromptTokenImpactTest {
    @Test void measureActualPromptsAgainstEquivalentPreNarratorBaseline() throws Exception {
        var encoding = Encodings.newDefaultEncodingRegistry().getEncoding(EncodingType.O200K_BASE);
        var rows = new ArrayList<Map<String,Object>>();
        for (var entry : PromptMigrationEquivalenceTest.prompts().entrySet()) {
            String after = entry.getValue();
            String before = after.replace(PromptModules.narrator(), "");
            int oldTokens = encoding.countTokensOrdinary(before), newTokens = encoding.countTokensOrdinary(after);
            int delta = newTokens-oldTokens;
            if (!entry.getKey().endsWith("stable") && !entry.getKey().equals("legacy")) assertThat(delta).isZero();
            else assertThat(delta).isPositive();
            rows.add(Map.of("fixture",entry.getKey(),"before",oldTokens,"after",newTokens,"delta",delta));
        }
        Files.createDirectories(Path.of("target"));
        Files.writeString(Path.of("target/prompt-token-impact.json"),
                PromptMigrationEquivalenceTest.M.writerWithDefaultPrettyPrinter().writeValueAsString(rows));
    }
}
