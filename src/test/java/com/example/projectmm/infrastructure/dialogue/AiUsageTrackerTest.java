package com.example.projectmm.infrastructure.dialogue;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class AiUsageTrackerTest {
 @Test void countsCachedInputOnceAndRefreshesLatestCost() {
  var tracker=new AiUsageTracker();
  tracker.record(1L,"OPENAI","gpt-5.6-luna",1000,800,500,1500);
  assertThat(tracker.latest(1L).estimatedCostUsd()).isEqualByComparingTo("0.000656");
  tracker.record(1L,"OPENAI","gpt-5.6-terra",1000,0,500,1500);
  assertThat(tracker.latest(1L).estimatedCostUsd()).isEqualByComparingTo("0.008");
 }
 @Test void appliesLongContextPricingAndPreservesUnknownAsUnavailable() {
  var tracker=new AiUsageTracker();
  tracker.record(1L,"OPENAI","gpt-5.6-sol",300000,100000,1000,301000);
  assertThat(tracker.latest(1L).estimatedCostUsd()).isEqualByComparingTo("1.71");
  tracker.record(1L,"OPENAI","unknown",1000,0,500,1500);
  assertThat(tracker.latest(1L).estimatedCostUsd()).isNull();
 }
 @Test void costIsSerializedInUsageResponse() throws Exception {
  var tracker=new AiUsageTracker();tracker.record(1L,"OPENAI","gpt-5.6-luna",1000,800,500,1500);
  var mapper=tools.jackson.databind.json.JsonMapper.builder().findAndAddModules().build();
  assertThat(mapper.readTree(mapper.writeValueAsString(tracker.latest(1L))).get("estimatedCostUsd").decimalValue()).isEqualByComparingTo("0.000656");
 }
 @Test void capturesOnlyCurrentCallAndSessionWithoutReusingStaleLatest() {
  var tracker=new AiUsageTracker();tracker.record(1L,"OPENAI","old",10,0,5,15);
  assertThat(AiUsageTracker.capture(1L,()->"No reported usage").usage()).isNull();
  var result=AiUsageTracker.capture(1L,()->{
   tracker.record(2L,"OPENAI","foreign",100,0,200,300);
   tracker.record(1L,"GEMINI","actual",30,5,20,50);
   return "reply";
  });
  assertThat(result.value()).isEqualTo("reply");assertThat(result.usage().model()).isEqualTo("actual");
  assertThat(result.usage().totalTokens()).isEqualTo(50);
  assertThat(AiUsageTracker.capture(1L,()->"Cached replay").usage()).isNull();
 }
}
