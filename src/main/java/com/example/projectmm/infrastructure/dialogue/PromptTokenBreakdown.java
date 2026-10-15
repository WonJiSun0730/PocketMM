package com.example.projectmm.infrastructure.dialogue;

import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingType;
import java.util.List;
import java.util.Map;

/** Local text-only estimate, never a substitute for provider billing usage. */
public record PromptTokenBreakdown(String tokenizer, String basis, long systemTokens,
                                   long historyTokens, long memoryTokens, long currentInputTokens,
                                   long schemaTokens) {
    private static final Encoding ENCODING = Encodings.newDefaultEncodingRegistry()
            .getEncoding(EncodingType.O200K_BASE);

    static PromptTokenBreakdown estimate(String instructions, List<Map<String,Object>> input, String schema) {
        String marker = "Relevant recalled memories of currently active characters:\n";
        int memoryStart = instructions.indexOf(marker);
        // Memory is the final section of the shared prompt; OpenAI-specific rules follow it.
        int memoryEnd = memoryStart < 0 ? -1 : instructions.indexOf("Return the response using the supplied strict JSON schema.", memoryStart);
        String memory = memoryStart < 0 ? "" : instructions.substring(memoryStart,
                memoryEnd < 0 ? instructions.length() : memoryEnd);
        String system = memoryStart < 0 ? instructions : instructions.substring(0,memoryStart)
                + (memoryEnd < 0 ? "" : instructions.substring(memoryEnd));
        long history=0;
        for(int i=0;i<input.size()-1;i++) history+=count((String)input.get(i).get("content"));
        return new PromptTokenBreakdown("o200k_base", "LOCAL_TEXT_ESTIMATE_EXCLUDES_MESSAGE_FRAMING",
                count(system), history, count(memory), input.isEmpty()?0:count((String)input.getLast().get("content")), count(schema));
    }
    private static long count(String text) {return text==null?0:ENCODING.countTokensOrdinary(text);}
}
