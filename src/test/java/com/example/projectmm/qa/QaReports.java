package com.example.projectmm.qa;

import tools.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.*;
import java.math.BigDecimal;
import java.util.*;

public final class QaReports {
    private QaReports() {}
    public static void write(Path directory,QaRunner.State state) throws IOException {
        Files.createDirectories(directory);
        Files.writeString(directory.resolve("raw.json"),new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(state));
        var csv=new StringBuilder("turn,request_id,latency_ms,metric,result,method,known_usd,unknown_calls,input,cached,output\n");
        for(var t:state.turns)csv.append(t.number()).append(',').append(q(t.requestId())).append(',').append(t.latencyMillis()).append(',')
                .append(q(t.verdict()==null?"":t.verdict().metric().name())).append(',')
                .append(q(t.verdict()==null?"":t.verdict().result())).append(',')
                .append(q(t.verdict()==null?"":t.verdict().method())).append(',')
                .append(state.calls.stream().filter(c->c.turn()==t.number()).map(state::cost).filter(Objects::nonNull).reduce(BigDecimal.ZERO,BigDecimal::add))
                .append(',').append(state.calls.stream().filter(c->c.turn()==t.number()&&state.cost(c)==null).count())
                .append(',').append(state.calls.stream().filter(c->c.turn()==t.number()).mapToLong(QaRunner.Usage::input).sum())
                .append(',').append(state.calls.stream().filter(c->c.turn()==t.number()).mapToLong(QaRunner.Usage::cached).sum())
                .append(',').append(state.calls.stream().filter(c->c.turn()==t.number()).mapToLong(QaRunner.Usage::output).sum()).append('\n');
        Files.writeString(directory.resolve("turns.csv"),csv);
        var calls=new StringBuilder("call,turn,call_id,role,model,purpose,input,cached,cache_write,output,status,usd\n");int n=0;
        for(var c:state.calls)calls.append(++n).append(',').append(c.turn()).append(',').append(q(c.callId())).append(',').append(q(c.role())).append(',').append(q(c.model())).append(',')
                .append(q(c.purpose())).append(',').append(c.input()).append(',').append(c.cached()).append(',')
                .append(c.written()==null?"":c.written()).append(',').append(c.output()).append(',').append(q(c.status()+(state.reconciledCosts.containsKey(c.callId())?";COST_RECONCILED":""))).append(',')
                .append(state.cost(c)==null?"":state.cost(c)).append('\n');
        Files.writeString(directory.resolve("calls.csv"),calls);
        var md=new StringBuilder("# PocketMM QA 결과\n\n");
        md.append(state.spec.paid()?"실제 API 실행. 가격표에 따른 계산이며 청구서 확정액과 구분합니다.\n\n":"무료 모의 실행. 실행기·실제 저장/검색 경로 검증이며 AI 응답 품질이나 수익성 실측이 아닙니다.\n\n");
        md.append("- 상태: ").append(state.status).append("\n- 완료: ").append(state.turns.size()).append('/').append(state.spec.turns())
                .append("턴\n- 시작: ").append(state.startedAt).append("\n- 마지막 기록: ").append(state.updatedAt)
                .append("\n- 알려진 비용 소계: $").append(state.knownUsd()).append("\n- 비용 미확인 호출: ")
                .append(state.calls.stream().filter(c->state.cost(c)==null).count()).append("\n- 진행 중 예약액: $").append(state.heldUsd)
                .append("\n- 다음 턴 예약 기준 예상 상한: $").append(state.spec.turnReservationUsd().multiply(BigDecimal.valueOf(state.spec.turns())))
                .append(" (실측·보장 아님)\n\n");
        md.append("| 역할 | 알려진 비용 USD | 입력 | 캐시 읽기 | 출력 |\n|---|---:|---:|---:|---:|\n");
        for(var role:List.of("PERSONA","TARGET","AUXILIARY","JUDGE")) {
            var list=state.calls.stream().filter(c->c.role().equals(role)).toList();
            md.append('|').append(role).append('|').append(list.stream().map(state::cost).filter(Objects::nonNull).reduce(BigDecimal.ZERO,BigDecimal::add))
                    .append('|').append(list.stream().mapToLong(QaRunner.Usage::input).sum()).append('|').append(list.stream().mapToLong(QaRunner.Usage::cached).sum())
                    .append('|').append(list.stream().mapToLong(QaRunner.Usage::output).sum()).append("|\n");
        }
        md.append("\n| 지표 | 통과 | 실패 | 미평가 | 평가된 항목 중 통과율 |\n|---|---:|---:|---:|---:|\n");
        for(var metric:QaScenarios.Metric.values()) {
            var v=state.turns.stream().map(QaRunner.Turn::verdict).filter(Objects::nonNull).filter(x->x.metric()==metric).toList();
            md.append('|').append(metric);
            for(var result:List.of("PASS","FAIL","UNEVALUATED"))md.append('|').append(v.stream().filter(x->x.result().equals(result)).count());
            long pass=v.stream().filter(x->x.result().equals("PASS")).count(),fail=v.stream().filter(x->x.result().equals("FAIL")).count();
            md.append('|').append(pass+fail==0?"미평가":String.format(java.util.Locale.ROOT,"%.1f%%",100.0*pass/(pass+fail))).append("|\n");
        }
        md.append("\n서술형 회상·정정·관계·환각은 단어 일치만으로 정답 처리하지 않습니다. AI 판정기는 미사용이며 해당 항목은 미평가입니다.\n\n");
        if(state.reason!=null)md.append("중단 사유: ").append(state.reason).append("\n\n");
        for(var t:state.turns) if(t.verdict()!=null)md.append("## 턴 ").append(t.number()).append(" · ").append(t.verdict().eventId())
                .append("\n\n결과: ").append(t.verdict().result()).append("\n\n입력: ").append(t.input()).append("\n\n응답: ").append(t.outcome().reply())
                .append("\n\n근거:\n\n").append(t.verdict().evidence()).append("\n\n");
        Files.writeString(directory.resolve("report.md"),md);
    }
    private static String q(String value) {return '"'+value.replace("\"","\"\"")+'"';}
}
