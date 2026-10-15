package com.example.projectmm.application.memory;
import com.example.projectmm.domain.memory.*;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
class PocketMemoryQueryTest {
    private EffectiveMemory memory(long id,String quote) {
        return new EffectiveMemory(id,quote,7,70,LocalDateTime.of(2026,10,15,12,0),false,quote,null);
    }
    @Test void vocativeAndThinkingAloneDoNotRetrieveCaseDuringTeaConversation() {
        var candidates=List.of(memory(1,"홈즈, 오늘 열쇠를 받았어요."),memory(2,"그런데 생각해보니 교회 근처에 수상한 사람이 있었어요."));
        assertThat(new MemoryRetrievalRanker().retrieveRelevant("조사와 상관없는 얘기지만 저는 레몬 차를 좋아해요. 홈즈는 쉴 때도 계속 사건을 생각하나요?",candidates,5)).isEmpty();
    }
    @Test void questionAboutDeliveryPrioritizesPreviouslyMissedDeliveryFact() {
        var candidates=List.of(memory(1,"홈즈, 열쇠의 각인은 B-731이에요."),memory(2,"열쇠는 그대로 둔 채 홈즈의 판단을 기다린다."),memory(3,"진료소 문틈에 놓여 있었고 봉투에는 소인이 없었어요."));
        assertThat(new MemoryRetrievalRanker().retrieveRelevant("열쇠를 처음 어디서 어떤 방식으로 받았다고 말했는지 정리해주세요",candidates,1))
                .extracting(EffectiveMemory::memoryId).containsExactly(3L);
    }
}
