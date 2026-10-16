package com.example.projectmm.infrastructure.dialogue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Pattern;

/** Immutable UTF-8 classpath modules: works inside an executable jar, no filesystem or network lookup. */
public final class PromptModules {
    private static final Pattern SECTION=Pattern.compile("^<!-- module: ([a-z0-9.-]+) -->\\n(.*?)^<!-- /module -->\\n?",Pattern.MULTILINE|Pattern.DOTALL);
    private static final PromptModules DEFAULT=new PromptModules(PromptModules.class.getClassLoader());
    private final Map<String,Map<String,String>> modules;

    PromptModules(ClassLoader loader) {
        var loaded=new LinkedHashMap<String,Map<String,String>>();
        for(String name:List.of("SYSTEM","SCENE","NARRATOR")) {
            String path="prompts/"+name+".md";
            try(var input=loader.getResourceAsStream(path)) {
                if(input==null)throw new IllegalStateException("Required prompt resource is missing: "+path);
                loaded.put(name,parse(new String(input.readAllBytes(),StandardCharsets.UTF_8),path));
            } catch(IOException ex) {throw new IllegalStateException("Cannot read required prompt resource: "+path,ex);}
        }
        modules=Map.copyOf(loaded);
        required("NARRATOR","narrator.expression");
        for(String key:List.of("world.setup","world.context-template","world.authority","world.character-output","legacy.resolved-input","legacy.setup","legacy.memory-character","legacy.perception")) required("SYSTEM",key);
        for(String key:List.of("world.player-settings","world.cast","legacy.continue","legacy.time","legacy.prose-conventions","legacy.scene-focus","legacy.authorship","live.strict","live.elaborate","live.pacing","continuation.progress","continuation.strict","continuation.low","continuation.moderate","continuation.high","continuation.delegated-intro","continuation.delegated-end")) required("SCENE",key);
    }

    static Map<String,String> parse(String content,String path) {
        var result=new LinkedHashMap<String,String>();
        var matcher=SECTION.matcher(content.replace("\r\n","\n"));
        while(matcher.find()) {
            if(matcher.group(2).isBlank()||result.putIfAbsent(matcher.group(1),matcher.group(2))!=null)
                throw new IllegalStateException("Empty or duplicate prompt section in "+path+": "+matcher.group(1));
        }
        if(result.isEmpty())throw new IllegalStateException("No prompt sections in "+path);
        return Map.copyOf(result);
    }

    public static String section(String module,String key) {return DEFAULT.required(module,key);}
    String required(String module,String key) {
        var sections=modules.get(module);
        if(sections==null||!sections.containsKey(key))throw new IllegalStateException("Required prompt section is missing: "+module+"/"+key);
        return sections.get(key);
    }
    public static String narrator() { return "\n" + section("NARRATOR", "narrator.expression"); }
    public static void validate() { DEFAULT.required("SYSTEM","world.setup");DEFAULT.required("SCENE","live.pacing"); }
}
