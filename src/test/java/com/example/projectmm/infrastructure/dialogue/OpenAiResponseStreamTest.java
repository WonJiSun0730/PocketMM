package com.example.projectmm.infrastructure.dialogue;

import org.junit.jupiter.api.Test;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import tools.jackson.databind.ObjectMapper;
import static org.assertj.core.api.Assertions.*;

class OpenAiResponseStreamTest {
    static final ObjectMapper JSON=new ObjectMapper();
    static String frame(Object event) {return "data: "+JSON.writeValueAsString(event)+"\n\n";}
    static String delta(String text) {return frame(Map.of("type","response.output_text.delta","output_index",1,"content_index",0,"delta",text));}
    @Test void deliversDecodedKoreanBeforeTerminalBytesAreReadAndNeverLeaksMetadata() throws Exception {
        var first=delta("{\"reply\":\"안녕");var observed=new AtomicBoolean();
        var bytes=(first+delta("\\n\\\"친구\\\" \\uD83D")+delta("\\uDE00\",\"systemChanges\":[{\"delta\":999}]}")
                +frame(Map.of("type","response.completed","response",Map.of("status","completed","usage",Map.of("output_tokens",10))))).getBytes(StandardCharsets.UTF_8);
        var input=new ByteArrayInputStream(bytes) {
            @Override public synchronized int available(){return 0;}
            @Override public synchronized int read(byte[] b,int off,int len) {
                if(pos>=first.getBytes(StandardCharsets.UTF_8).length)assertThat(observed).isTrue();
                return super.read(b,off,Math.min(len,1));
            }
        };
        var replies=new ArrayList<String>();
        String terminal=OpenAiResponseStream.read(input,value->{observed.set(true);replies.add(value);});
        assertThat(replies).containsExactly("안녕","안녕\n\"친구\" ","안녕\n\"친구\" 😀");
        assertThat(terminal).contains("completed","output_tokens");
        assertThat(replies).allSatisfy(value->assertThat(value).doesNotContain("systemChanges","999","reply"));
    }
    @Test void truncatedOrErrorStreamCannotBecomeCompletedResponse() {
        for(String text:List.of(delta("{\"reply\":\"partial"),frame(Map.of("type","error","message","SECRET"))))
            assertThatThrownBy(()->OpenAiResponseStream.read(new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)),s->{}))
                    .isInstanceOf(IOException.class).hasMessageNotContaining("SECRET");
    }
    @Test void failedAndIncompleteTerminalsPreserveUsageForSettlement() throws Exception {
        for(String status:List.of("failed","incomplete")) {
            var terminal=frame(Map.of("type","response."+status,"response",Map.of("status",status,"usage",Map.of("output_tokens",12))));
            assertThat(OpenAiResponseStream.read(new ByteArrayInputStream(terminal.getBytes(StandardCharsets.UTF_8)),s->{}))
                    .contains(status,"output_tokens");
        }
    }
    @Test void unknownPropertyOrderAndPartialEscapeRemainSafe() {
        assertThat(OpenAiResponseStream.replyPrefix("{\"elapsedSeconds\":1,\"reply\":\"text\"}")).isEmpty();
        assertThat(OpenAiResponseStream.replyPrefix("{\"reply\":\"hello\\")).isEqualTo("hello");
        assertThat(OpenAiResponseStream.replyPrefix("{\"reply\":\"hello\\u12")).isEqualTo("hello");
    }
}
