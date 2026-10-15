package com.example.projectmm.infrastructure.dialogue;

import com.example.projectmm.domain.dialogue.WorldRoomDialogueContext;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class OpenAiUsageParsingTest {
    @Test void recordsProviderWritesAndPreservesMissingAsUnknown() throws Exception {
        var mapper=JsonMapper.builder().findAndAddModules().build();
        var tracker=new AiUsageTracker();
        var gateway=new OpenAiDialogueGateway("", "gpt-5.6-terra", mapper,
                mock(GeminiDialogueGateway.class), tracker);
        var context=mock(WorldRoomDialogueContext.class);
        when(context.sessionId()).thenReturn(1L);
        when(context.sessionSystems()).thenReturn(List.of());
        var parse=OpenAiDialogueGateway.class.getDeclaredMethod("parseResponse",String.class,WorldRoomDialogueContext.class,String.class);
        parse.setAccessible(true);
        for (Long writes : new Long[]{300L,0L,null}) {
            var payload=mapper.createObjectNode().put("reply","테스트 응답");
            var response=mapper.createObjectNode();
            response.putArray("output").addObject().put("type","message")
                    .putArray("content").addObject().put("type","output_text").put("text",mapper.writeValueAsString(payload));
            var usage=response.putObject("usage").put("input_tokens",1000).put("output_tokens",100).put("total_tokens",1100);
            var details=usage.putObject("input_tokens_details").put("cached_tokens",600);
            if(writes!=null) details.put("cache_write_tokens",writes);
            parse.invoke(gateway,mapper.writeValueAsString(response),context,"gpt-5.6-terra");
            assertThat(tracker.latest(1L).cacheWriteTokens()).isEqualTo(writes);
            assertThat(tracker.latest(1L).totalTokens()).isEqualTo(1100);
            if(writes==null) assertThat(tracker.latest(1L).estimatedCostUsd()).isNull();
        }
    }
}
