package com.example.projectmm.application.memory;

import com.example.projectmm.domain.memory.EffectiveMemory;
import com.example.projectmm.domain.memory.MemoryFact;
import java.util.*;

/** Request-local selection only. Never mutates stored memories or their timestamps. */
final class PocketMemoryDeduplicator {
    private PocketMemoryDeduplicator() {}

    static List<EffectiveMemory> select(List<EffectiveMemory> ranked, int limit) {
        var selected = new ArrayList<EffectiveMemory>();
        for (var candidate : ranked) {
            var representative=candidate;
            int position=selected.size();
            for(int i=0;i<selected.size();) {
                if(equivalent(selected.get(i),representative)) {
                    position=Math.min(position,i);
                    representative=preferred(selected.remove(i),representative);
                    i=0; // A more complete representative can subsume another existing group.
                } else i++;
            }
            selected.add(Math.min(position,selected.size()),representative);
        }
        return selected.stream().limit(limit).toList();
    }

    private static boolean equivalent(EffectiveMemory a, EffectiveMemory b) {
        if(a.accessLevel()!=b.accessLevel() || !sameFact(a.fact(),b.fact())) return false;
        String left=text(a), right=text(b);
        if(left.isBlank() || right.isBlank()) return false;
        if(left.equals(right)) return true;
        // Only complete quoted sentences may be subsumed. No synonym or contradiction inference.
        if(hasCorrection(left) || hasCorrection(right)) return false;
        return containsSentences(left,right) || containsSentences(right,left);
    }

    private static boolean containsSentences(String longer, String shorter) {
        if(longer.length()<=shorter.length() || !shorter.matches("(?s).*[.!?。！？]$")) return false;
        var whole=Arrays.asList(longer.split("(?<=[.!?。！？])\\s+"));
        var part=Arrays.asList(shorter.split("(?<=[.!?。！？])\\s+"));
        return Collections.indexOfSubList(whole,part)>=0;
    }

    private static boolean hasCorrection(String text) {
        return text.matches("(?is).*(아니|취소|변경|정정|대신|not\\b|cancel|instead|correct).*");
    }

    private static EffectiveMemory preferred(EffectiveMemory a, EffectiveMemory b) {
        int detail=Integer.compare(text(a).length(),text(b).length());
        if(detail!=0) return detail>0?a:b;
        int time=Comparator.nullsFirst(java.time.LocalDateTime::compareTo).compare(a.rememberedAt(),b.rememberedAt());
        if(time!=0) return time>0?a:b;
        return Comparator.nullsFirst(Long::compareTo).compare(a.memoryId(),b.memoryId())>=0?a:b;
    }

    private static String text(EffectiveMemory memory) {
        String text=memory.evidenceQuote();
        if(text==null || text.isBlank()) text=memory.summary();
        return normalize(text);
    }
    private static String normalize(String text) {return text==null?"":text.strip().replaceAll("\\s+"," ");}
    private static boolean sameFact(MemoryFact a, MemoryFact b) {
        if(a==null || b==null) return a==b;
        return a.getKind()==b.getKind() && Objects.equals(a.getSubject(),b.getSubject())
                && Objects.equals(a.getRelation(),b.getRelation()) && Objects.equals(a.getValue(),b.getValue());
    }
}
