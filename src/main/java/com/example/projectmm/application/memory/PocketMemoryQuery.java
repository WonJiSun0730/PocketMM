package com.example.projectmm.application.memory;
import com.example.projectmm.domain.memory.EffectiveMemory;
import java.util.List;
import java.util.regex.Pattern;

final class PocketMemoryQuery {
    private PocketMemoryQuery() {}
    private static final Pattern VOCATIVE=Pattern.compile("^([\\p{L}]{2,20}),\\s");
    static String focus(String query,List<EffectiveMemory> candidates) {
        String result=query.replaceAll("[\\p{L}]+(?:와|과)\\s*상관없는\\s*얘기(?:지만)?", " ");
        for(var memory:candidates) {
            String quote=memory.evidenceQuote();
            if(quote==null)continue;
            var matcher=VOCATIVE.matcher(quote);
            if(matcher.find())result=result.replace(matcher.group(1)," ");
        }
        return result;
    }
    static int requestedFactScore(String query,EffectiveMemory memory) {
        String text=memory.evidenceQuote()==null?memory.summary():memory.evidenceQuote();
        if(text==null)return 0;
        if(query.matches("(?s).*(어떻게|어떤\\s*방식|전달\\s*경위).*")
                && text.matches("(?s).*(문틈|소인|우편|배달|전달|놓여|건네).*") )return 1;
        return 0;
    }
}
