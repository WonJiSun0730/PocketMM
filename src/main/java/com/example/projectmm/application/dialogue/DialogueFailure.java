package com.example.projectmm.application.dialogue;

import java.util.*;

/** Diagnostic metadata only. Never includes exception messages, prompts or provider bodies. */
public final class DialogueFailure {
    private DialogueFailure() {}
    public record Detail(String code,String phase,List<String> exceptionTypes,List<String> locations) {}
    public static Detail describe(Throwable failure,DialogueTiming.Snapshot timing) {
        var seen=Collections.newSetFromMap(new IdentityHashMap<Throwable,Boolean>());
        var types=new ArrayList<String>();var locations=new ArrayList<String>();boolean rejected=false;
        for(Throwable current=failure;current!=null && seen.add(current) && types.size()<8;current=current.getCause()) {
            types.add(current.getClass().getName());
            if(current instanceof SceneContinuityService.ReviewRejectedException)rejected=true;
            Arrays.stream(current.getStackTrace()).filter(frame->frame.getClassName().startsWith("com.example.projectmm."))
                    .findFirst().ifPresent(frame->locations.add(frame.getClassName()+"."+frame.getMethodName()+":"+frame.getLineNumber()));
        }
        String phase=timing.reviewCompleteMs()!=null?"PERSISTENCE_OR_MEMORY"
                : timing.bodyReadyMs()!=null?"CONTINUITY"
                : timing.preGenerationMs()!=null?"GENERATION":"PREPARATION";
        return new Detail(rejected?"CONTINUITY_REJECTED":"CHAT_"+phase,phase,List.copyOf(types),List.copyOf(locations));
    }
    public static String message(Detail detail,String requestId) {
        String reason=detail.code().equals("CONTINUITY_REJECTED")?"장면 연속성 검사에서 승인이 나지 않았어요."
                :switch(detail.phase()) {
                    case "PREPARATION" -> "대화 생성 준비 중 오류가 발생했어요.";
                    case "GENERATION" -> "AI 본문 생성 또는 응답 수신 중 오류가 발생했어요.";
                    case "CONTINUITY" -> "장면 연속성 검사 처리 중 오류가 발생했어요.";
                    default -> "상태 저장 또는 기억 처리 중 오류가 발생했어요.";
                };
        return reason+" 저장된 대화를 확인해 주세요. 자동 재시도하지 않습니다. ["+detail.code()+" · "+requestId+"]";
    }
}
