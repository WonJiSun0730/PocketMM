package com.example.projectmm.infrastructure.dialogue;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class AiUsageTrackerTest {
 @Test void countsCachedInputOnceAndRefreshesLatestCost() {
  var tracker=new AiUsageTracker();
  tracker.record(1L,"OPENAI","gpt-5.6-luna",1000,800,0L,500,1500);
  assertThat(tracker.latest(1L).estimatedCostUsd()).isEqualByComparingTo("0.000656");
  tracker.record(1L,"OPENAI","gpt-5.6-terra",1000,0,0L,500,1500);
  assertThat(tracker.latest(1L).estimatedCostUsd()).isEqualByComparingTo("0.008");
 }
 @Test void appliesLongContextPricingAndPreservesUnknownAsUnavailable() {
  var tracker=new AiUsageTracker();
  tracker.record(1L,"OPENAI","gpt-5.6-sol",300000,100000,0L,1000,301000);
  assertThat(tracker.latest(1L).estimatedCostUsd()).isEqualByComparingTo("1.71");
  tracker.record(1L,"OPENAI","unknown",1000,0,500,1500);
  assertThat(tracker.latest(1L).estimatedCostUsd()).isNull();
 }
 @Test void costIsSerializedInUsageResponse() throws Exception {
  var tracker=new AiUsageTracker();tracker.record(1L,"OPENAI","gpt-5.6-luna",1000,800,0L,500,1500);
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
 @Test void separatesWritesFromOrdinaryInputWithoutDoubleCounting() throws Exception {
  var tracker=new AiUsageTracker();
  tracker.record(1L,"OPENAI","gpt-5.6-terra",1000,600,300L,100,1100);
  assertThat(tracker.latest(1L).ordinaryInputTokens()).isEqualTo(100);
  assertThat(tracker.latest(1L).estimatedCostUsd()).isEqualByComparingTo("0.00227");
  var mapper=tools.jackson.databind.json.JsonMapper.builder().findAndAddModules().build();
  var json=mapper.readTree(mapper.writeValueAsString(tracker.latest(1L)));
  assertThat(json.get("cacheWriteTokens").asLong()).isEqualTo(300);
  assertThat(json.get("ordinaryInputTokens").asLong()).isEqualTo(100);
  tracker.record(1L,"OPENAI","gpt-5.6-sol",300000,100000,50000L,1000,301000);
  assertThat(tracker.latest(1L).estimatedCostUsd()).isEqualByComparingTo("1.81");
 }
 @Test void unknownAndInvalidWritesDoNotProduceMisleadingCost() {
  var tracker=new AiUsageTracker();
  tracker.record(1L,"OPENAI","gpt-5.6-terra",1000,600,100,1100);
  assertThat(tracker.latest(1L).cacheWriteTokens()).isNull();
  assertThat(tracker.latest(1L).ordinaryInputTokens()).isNull();
  assertThat(tracker.latest(1L).estimatedCostUsd()).isNull();
  tracker.record(1L,"OPENAI","gpt-5.6-terra",1000,600,500L,100,1100);
  assertThat(tracker.latest(1L).estimatedCostUsd()).isNull();
 }
 @Test void oldSavedUsageRemainsReadableWithoutInventingWrites() {
  var mapper=tools.jackson.databind.json.JsonMapper.builder().findAndAddModules().build();
  var old="""
    {"provider":"OPENAI","model":"gpt-5.6-terra","inputTokens":1000,
     "cachedInputTokens":600,"outputTokens":100,"totalTokens":1100,"recordedAt":"2026-10-14T00:00:00Z"}
    """;
  var usage=mapper.readValue(old,AiUsageTracker.Snapshot.class);
  assertThat(usage.totalTokens()).isEqualTo(1100);
  assertThat(usage.cacheWriteTokens()).isNull();
  assertThat(usage.estimatedCostUsd()).isNull();
 }
}
