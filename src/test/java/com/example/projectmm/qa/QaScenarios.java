package com.example.projectmm.qa;

import java.util.*;

/** Only messageFor() enters the conversation. Expectations stay in the evaluator process. */
public final class QaScenarios {
    public enum Metric { STORAGE, RECALL, CORRECTION, COLLISION, HALLUCINATION, RELATIONSHIP }
    public record Event(int turn, String id, String message, Metric metric, List<String> required,
                        List<String> forbidden, boolean semanticReviewRequired) {}
    private final List<Event> events;
    public QaScenarios(List<Event> events) {
        this.events = List.copyOf(events);
        if (events.stream().map(Event::turn).distinct().count() != events.size()
                || events.stream().anyMatch(e -> e.turn() < 1)) throw new IllegalArgumentException("Duplicate/invalid turn");
    }
    public List<Event> events() { return events; }
    public Optional<Event> at(int turn) { return events.stream().filter(e -> e.turn()==turn).findFirst(); }
    public String messageFor(int turn) { return at(turn).map(Event::message).orElse(null); }
    public static QaScenarios standard(int turns) {
        var e = new ArrayList<Event>();
        e.add(new Event(2,"gift", "내가 준 은색 나침반에는 B731이 새겨져 있어. 오래 간직해 줘.",Metric.STORAGE,List.of("B731"),List.of(),false));
        e.add(new Event(4,"appointment","서윤과의 약속은 금요일 아홉 시에 동문 앞이야.",Metric.STORAGE,List.of("서윤","동문"),List.of(),false));
        e.add(new Event(6,"collision-source","서연과의 약속은 토요일 열 시에 북문 앞이야. 서윤과는 다른 약속이야.",Metric.STORAGE,List.of("서연","북문"),List.of(),false));
        e.add(new Event(8,"correction-source","아까 서윤과의 약속을 정정할게. 금요일 아홉 시 반, 서문 앞이야. 동문 약속은 취소했어.",Metric.STORAGE,List.of("서윤","서문"),List.of(),false));
        e.add(new Event(12,"unknown","전에 우리 같이 달빛섬에서 용을 구했다던데, 그때 무슨 일이 있었지?",Metric.HALLUCINATION,List.of(),List.of(),true));
        e.add(new Event(16,"latest","정정한 서윤과의 약속 장소가 어디였지?",Metric.CORRECTION,List.of("서문"),List.of("동문"),true));
        e.add(new Event(18,"distinct","서연과의 약속 장소도 알려줘. 서윤과 혼동하지 않게.",Metric.COLLISION,List.of("북문"),List.of("서문"),true));
        e.add(new Event(20,"relationship","아까 내가 건넨 이야기를 듣고 나를 어떻게 느끼는지 편하게 말해 줄래?",Metric.RELATIONSHIP,List.of(),List.of(),true));
        for (int gap : List.of(50,100,300,500,1000)) {
            int t=2+gap;
            if (t<=turns) e.add(new Event(t,"gift-recall-"+gap,"내가 준 은색 나침반의 각인이 뭐였지?",Metric.RECALL,List.of("B731"),List.of(),true));
        }
        return new QaScenarios(e.stream().filter(x->x.turn()<=turns).toList());
    }
    public record Verdict(String eventId, int turn, Metric metric, String result, String evidence,
                          String method, String ruleVersion) {}
    public Verdict evaluate(int turn, String reply, List<String> memoryEvidence) {
        var event=at(turn).orElse(null);
        if(event==null)return null;
        String evidence=event.metric()==Metric.STORAGE ? String.join("\n",memoryEvidence) : reply;
        boolean required=event.required().stream().allMatch(evidence::contains);
        boolean forbidden=event.forbidden().stream().anyMatch(evidence::contains);
        // Text presence cannot prove the meaning of a negation, correction or relationship.
        boolean stored=event.metric()!=Metric.STORAGE||memoryEvidence.stream().anyMatch(m->m.contains(event.message()));
        String result=event.semanticReviewRequired() ? "UNEVALUATED" : required&&!forbidden&&stored ? "PASS" : "FAIL";
        String method=event.semanticReviewRequired() ? "LEXICAL_CANDIDATE_ONLY required="+required+" forbidden="+forbidden : "EXACT_EVIDENCE_MATCH";
        return new Verdict(event.id(),turn,event.metric(),result,evidence,method,"qa-rules-v1");
    }
}
