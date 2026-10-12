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
}
