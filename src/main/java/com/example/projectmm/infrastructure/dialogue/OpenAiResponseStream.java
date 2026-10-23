package com.example.projectmm.infrastructure.dialogue;

import tools.jackson.databind.ObjectMapper;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

/** Responses SSE -> terminal response, with provisional decoded reply snapshots. No retries. */
public final class OpenAiResponseStream {
    private static final ObjectMapper JSON=new ObjectMapper();
    private OpenAiResponseStream() {}
    public static String read(InputStream input,Consumer<String> reply) throws IOException {
        var reader=new BufferedReader(new InputStreamReader(input,StandardCharsets.UTF_8));
        var data=new StringBuilder();var payload=new StringBuilder();String delivered="";
        String terminal=null;int size=0;Integer selectedOutput=null;
        String line;
        while((line=reader.readLine())!=null) {
            size+=line.length();if(size>16_000_000)throw new IOException("Response stream limit exceeded");
            if(line.startsWith("data:")) {if(!data.isEmpty())data.append('\n');data.append(line.substring(5).stripLeading());}
            if(!line.isEmpty())continue;
            if(data.isEmpty())continue;
            if(data.toString().equals("[DONE]"))break;
            var event=JSON.readTree(data.toString());data.setLength(0);
            var type=event.path("type").asText();
            if(type.equals("response.output_text.delta")) {
                if(selectedOutput==null)selectedOutput=event.path("output_index").asInt(0);
                if(event.path("output_index").asInt(0)!=selectedOutput || event.path("content_index").asInt(0)!=0)
                    throw new IOException("Multiple response outputs unsupported");
                payload.append(event.path("delta").asText());
                var visible=replyPrefix(payload.toString());
                if(!visible.equals(delivered)) {delivered=visible;reply.accept(visible);}
            } else if(type.equals("response.completed") || type.equals("response.incomplete") || type.equals("response.failed")) {
                terminal=JSON.writeValueAsString(event.path("response"));
                break;
            } else if(type.equals("error"))throw new IOException("Provider stream error");
        }
        if(terminal==null)throw new IOException("Response stream ended without terminal usage; no retry");
        return terminal;
    }

    // Only a root reply property is visible. Metadata order must not suppress partial delivery.
    static String replyPrefix(String json) {
        int depth=0;
        for(int i=0;i<json.length();i++) {
            char c=json.charAt(i);
            if(c=='{' || c=='[')depth++;
            else if(c=='}' || c==']')depth--;
            else if(c=='"') {
                int end=stringEnd(json,i+1);
                if(end<0)return "";
                int colon=skipWhitespace(json,end+1);
                if(depth==1 && colon<json.length() && json.charAt(colon)==':'
                        && JSON.readTree(json.substring(i,end+1)).asText().equals("reply")) {
                    int value=skipWhitespace(json,colon+1);
                    return value<json.length() && json.charAt(value)=='"'?decodePrefix(json,value+1):"";
                }
                i=end;
            }
        }
        return "";
    }
    private static int skipWhitespace(String text,int start) {
        while(start<text.length() && Character.isWhitespace(text.charAt(start)))start++;
        return start;
    }
    private static int stringEnd(String text,int start) {
        boolean escaped=false;
        for(int i=start;i<text.length();i++) {
            char c=text.charAt(i);
            if(escaped)escaped=false;
            else if(c=='\\')escaped=true;
            else if(c=='"')return i;
        }
        return -1;
    }
    private static String decodePrefix(String json,int i) {
        var result=new StringBuilder();
        while(i<json.length()) {
            char c=json.charAt(i++);
            if(c=='"')break;
            if(c=='\\') {
                if(i==json.length())break;
                char escaped=json.charAt(i++);
                switch(escaped) {
                    case '"','\\','/' -> result.append(escaped);
                    case 'n' -> result.append('\n');case 'r' -> result.append('\r');case 't' -> result.append('\t');
                    case 'b' -> result.append('\b');case 'f' -> result.append('\f');
                    case 'u' -> {
                        if(i+4>json.length())return withoutDanglingSurrogate(result);
                        try {result.append((char)Integer.parseInt(json.substring(i,i+4),16));}
                        catch(NumberFormatException invalid){throw new IllegalStateException("Invalid streamed JSON escape");}
                        i+=4;
                    }
                    default -> throw new IllegalStateException("Invalid streamed JSON escape");
                }
            } else {if(c<32)throw new IllegalStateException("Invalid streamed JSON string");result.append(c);}
        }
        return withoutDanglingSurrogate(result);
    }
    private static String withoutDanglingSurrogate(StringBuilder value) {
        int n=value.length();return n>0&&Character.isHighSurrogate(value.charAt(n-1))?value.substring(0,n-1):value.toString();
    }
}
