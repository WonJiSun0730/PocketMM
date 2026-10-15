package com.example.projectmm.infrastructure.memory;
import com.example.projectmm.infrastructure.dialogue.AiUsageTracker;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;
import static org.assertj.core.api.Assertions.*;
class OpenAiAuxiliaryUsageTest {
    @Test void memoryExtractionIsMeteredWithoutReplacingRoleplayAndMissingUsageIsUnknown() {
        var tracker=new AiUsageTracker();
        var extractor=new OpenAiChatMemoryExtractor("not-sent","gpt-5.6-luna",new ObjectMapper());
        ReflectionTestUtils.setField(extractor,"usageTracker",tracker);
        tracker.record(9L,"OPENAI","gpt-5.6-sol",100,0,100L,20,120);
        AiUsageTracker.withScope(9L,"MEMORY_EXTRACTION",()-> {
            extractor.recordUsage("{\"usage\":{\"input_tokens\":100,\"output_tokens\":10,\"total_tokens\":110,\"input_tokens_details\":{\"cached_tokens\":20,\"cache_write_tokens\":80}}}");
            extractor.recordUsage("{}");
            return null;
        });
        assertThat(tracker.latest(9L).model()).isEqualTo("gpt-5.6-sol");
        assertThat(tracker.calls(9L)).hasSize(3);
        assertThat(tracker.calls(9L).get(1).purpose()).isEqualTo("MEMORY_EXTRACTION");
        assertThat(tracker.calls(9L).get(1).usage().estimatedCostUsd()).isNotNull();
        assertThat(tracker.calls(9L).get(2).usage()).isNull();
        assertThat(tracker.calls(9L).get(2).status()).isEqualTo("USAGE_NOT_REPORTED");
    }
}
