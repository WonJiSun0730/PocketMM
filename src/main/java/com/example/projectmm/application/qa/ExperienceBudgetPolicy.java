package com.example.projectmm.application.qa;

import java.util.*;

/** Experience QA only: forecast is not a spending guarantee. */
public final class ExperienceBudgetPolicy {
    public static final String VERSION="hard-cap-v2-scene-continuity";
    public static Map<String,Object> create(List<Long> outputs) {
        var recent=outputs.stream().filter(n->n>0).limit(12).sorted().toList();
        long mean=recent.isEmpty()?1000:Math.round(recent.stream().mapToLong(Long::longValue).average().orElse(1000));
        long p95=recent.isEmpty()?1000:recent.get(Math.max(0,(int)Math.ceil(recent.size()*.95)-1));
        long max=recent.isEmpty()?1000:recent.getLast();
        long safe=Math.max(mean*2,p95+512);
        long target=Math.max(10000,Math.max(max*3+512,safe));
        return Map.of("version",VERSION,"sampleCount",recent.size(),"expectedTargetOutputTokens",mean,
                "safeExpectedTargetOutputTokens",safe,"sampleP95",p95,"sampleMaximum",max,
                "outputCaps",Map.of("PLAYER_SIMULATION",4096,"TARGET",target,"MEMORY_EXTRACTION",4096,
                        "MEMORY_COMPRESSION",8192,"TIME_INTERPRETATION",2048,"AUXILIARY",4096,"SCENE_CONTINUITY",Math.max(16384,target*2)),
                "differences","실서비스 프롬프트는 유지합니다. 하드캡 보호를 위해 테스트 요청에만 역할별 출력 상한을 적용합니다. 예상량은 지출 보장이 아닙니다. 입력 계수나 최대 과금 범위를 확인할 수 없으면 전송하지 않습니다.");
    }
}
