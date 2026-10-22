package com.example.projectmm.qa;
import com.example.projectmm.application.qa.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.http.client.*;
import org.springframework.http.*;
import tools.jackson.databind.*;
import java.nio.file.*;
import java.net.URI;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.assertj.core.api.Assertions.*;
class ExperienceBudgetTest {
 @TempDir Path dir;final ObjectMapper m=new ObjectMapper();
 QaSpec spec(BigDecimal budget) {
  var s=QaSpec.mock("hard-cap",100);
  var rate=new QaSpec.Rate(new BigDecimal("4"),new BigDecimal("0.4"),new BigDecimal("5"),new BigDecimal("20"),272000,new BigDecimal("2"),new BigDecimal("1.5"),"FIXTURE","2026-10-16");
  return new QaSpec(s.runId(),100,true,s.runId(),s.persona(),"gpt-5.6-luna","gpt-5.6-sol","gemini-3.5-flash-lite",budget,new BigDecimal("0.000001"),
   Map.of("gpt-5.6-sol",rate,"gpt-5.6-luna",rate,"gemini-3.5-flash-lite",rate,"text-embedding-3-small",rate),6,true);
 }
 ExperienceTransport transport(BigDecimal budget,long input) throws Exception {
  var manifest=m.createObjectNode();manifest.set("budgetPolicy",m.valueToTree(ExperienceBudgetPolicy.create(List.of(900L,1100L,800L))));
  manifest.set("maximumOutputTokens",m.valueToTree(Map.of("gpt-5.6-sol",128000,"gpt-5.6-luna",128000,"gemini-3.5-flash-lite",65536,"text-embedding-3-small",0)));
  Files.writeString(dir.resolve("manifest.json"),m.writeValueAsString(manifest));
  return new ExperienceTransport(spec(budget),dir,(p,model,j,e)->input);
 }
 MockClientHttpRequest req(){return new MockClientHttpRequest(HttpMethod.POST,URI.create("https://api.openai.com/v1/responses"));}
 byte[] body(){return "{\"model\":\"gpt-5.6-sol\",\"reasoning\":{\"effort\":\"none\"},\"input\":[{\"role\":\"user\",\"content\":\"장면을 이어간다\"}],\"text\":{\"format\":{\"name\":\"pocket_turn\"}}}".getBytes(java.nio.charset.StandardCharsets.UTF_8);}
 MockClientHttpResponse response(long in,long cache,long out) {return new MockClientHttpResponse(("{\"status\":\"completed\",\"usage\":{\"input_tokens\":"+in+",\"input_tokens_details\":{\"cached_tokens\":"+cache+",\"cache_write_tokens\":0},\"output_tokens\":"+out+"}}").getBytes(),HttpStatus.OK);}
 JsonNode budget() throws java.io.IOException{return m.readTree(Files.readString(dir.resolve("budget.json")));}
 @Test void rejectedReviewResponseIsSavedWithLedgerAndWithoutAuthHeaders() throws Exception {
  var t=transport(BigDecimal.TEN,1000);
  String review="{\"approved\":false,\"turn\":null,\"changes\":[],\"conflicts\":[\"Custody conflict\"]}";
  var root=(tools.jackson.databind.node.ObjectNode)m.readTree(response(1000,0,10).getBody());
  root.set("output",m.valueToTree(List.of(Map.of("type","message","content",List.of(Map.of("type","output_text","text",review))))));
  var request=req();request.getHeaders().setBearerAuth("SECRET_HEADER");
  com.example.projectmm.infrastructure.dialogue.AiUsageTracker.withPurpose(1L,"SCENE_CONTINUITY",()->{
   try{return t.intercept(request,body(),(r,b)->new MockClientHttpResponse(m.writeValueAsBytes(root),HttpStatus.OK));}
   catch(java.io.IOException e){throw new java.io.UncheckedIOException(e);}
  }).close();
  var calls=t.since(Set.of());assertThat(calls).hasSize(1);
  var saved=Files.readString(dir.resolve("review-responses").resolve(calls.getFirst().callId()+".json"));
  assertThat(m.readTree(saved).path("outputTexts").get(0).asText()).isEqualTo(review);
  assertThat(saved).doesNotContain("SECRET_HEADER","Authorization");
  assertThat(budget().path("reservedUsd").decimalValue()).isZero();
 }
 @Test void postResponseLocalSaveFailurePreservesKnownReceiptAndNeverResendsHttp() throws Exception {
  var t=transport(new BigDecimal("4"),1000);var sent=new AtomicInteger();
  assertThatThrownBy(()->t.intercept(req(),body(),(r,b)->{
   sent.incrementAndGet();
   Files.delete(dir.resolve("budget.json"));
   Files.createDirectory(dir.resolve("budget.json"));
   return response(1000,0,100);
  })).isInstanceOf(java.io.IOException.class);
  var recorded=t.since(Set.of());
  assertThat(recorded).hasSize(1);
  assertThat(recorded.getFirst().usd()).isEqualByComparingTo("0.006");
  assertThat(recorded.getFirst().status()).isEqualTo("completed");
  assertThat(Files.readString(dir.resolve("ledger.jsonl"))).contains("LOCAL_PERSISTENCE_FAILED");
  assertThatThrownBy(()->t.intercept(req(),body(),(r,b)->{sent.incrementAndGet();return response(1000,0,100);}));
  assertThat(sent.get()).isEqualTo(1);
 }
 @Test void fourDollarHardCapStopsBeforeNextHttpEvenAtEveryMaximumAndReleasesUnusedReservation() throws Exception {
  var t=transport(new BigDecimal("4"),1000);var sent=new AtomicInteger();
  while(!t.budgetBlocked) {
   try {t.intercept(req(),body(),(r,b)->{sent.incrementAndGet();assertThat(m.readTree(b).path("max_output_tokens").asLong()).isEqualTo(10000);
    assertThat(budget().path("reservedUsd").decimalValue()).isEqualByComparingTo("0.205");
    return response(1000,0,10000);}).close();}catch(IllegalStateException e){assertThat(t.budgetBlocked).isTrue();}
  }
  assertThat(sent.get()).isEqualTo(19);assertThat(budget().path("actualUsd").decimalValue()).isEqualByComparingTo("3.876");
  assertThat(budget().path("actualUsd").decimalValue()).isLessThanOrEqualTo(new BigDecimal("4"));
  assertThat(budget().path("reservedUsd").decimalValue()).isZero();
  assertThat(budget().path("remainingUsd").decimalValue()).isEqualByComparingTo("0.124");
  assertThat(budget().path("nextMaximumUsd").decimalValue()).isEqualByComparingTo("0.205");
 }
 @Test void forecastsAreNotCapsCacheSavingsAreSettledNotReservedAndSurplusIsReleased() throws Exception {
  var t=transport(new BigDecimal("4"),1000);
  t.intercept(req(),body(),(r,b)->response(1000,900,100)).close();var b=budget();
  assertThat(b.path("actualUsd").decimalValue()).isEqualByComparingTo("0.00276");
  assertThat(b.path("reservedUsd").decimalValue()).isZero();
  assertThat(b.path("nextMaximumUsd").decimalValue()).isEqualByComparingTo("0.205");
  assertThat(b.path("expectedUsd").decimalValue()).isLessThan(b.path("nextMaximumUsd").decimalValue());
  assertThat(b.path("differenceFromExpectedUsd").decimalValue()).isNegative();
 }
 @Test void realInputCountDeterminesLargeContextSurcharge() throws Exception {
  var t=transport(BigDecimal.TEN,272000);t.intercept(req(),body(),(r,b)->response(272000,0,10)).close();
  assertThat(budget().path("largeContextSurcharge").asBoolean()).isFalse();assertThat(budget().path("nextMaximumUsd").decimalValue()).isEqualByComparingTo("1.56");
  t=new ExperienceTransport(spec(BigDecimal.TEN),dir,(p,model,j,e)->272001);
  t.intercept(req(),body(),(r,b)->response(272001,0,10)).close();
  assertThat(budget().path("largeContextSurcharge").asBoolean()).isTrue();assertThat(budget().path("nextMaximumUsd").decimalValue()).isEqualByComparingTo("3.02001");
 }
 @Test void uncertainCostRetainsReservationAndBlocksRestartAndEveryRole() throws Exception {
  var t=transport(new BigDecimal("4"),1000);var sent=new AtomicInteger();
  assertThatThrownBy(()->t.intercept(req(),body(),(r,b)->{sent.incrementAndGet();throw new java.io.IOException("secret-key");})).isInstanceOf(java.io.IOException.class);
  assertThat(budget().path("reservedUsd").decimalValue()).isEqualByComparingTo("0.205");
  var restarted=new ExperienceTransport(spec(new BigDecimal("4")),dir,(p,model,j,e)->1000);
  assertThatThrownBy(()->restarted.intercept(req(),body(),(r,b)->{sent.incrementAndGet();return response(1000,0,1);})).hasMessageContaining("automatic retry prohibited");
  assertThat(sent.get()).isEqualTo(1);assertThat(Files.readString(dir.resolve("budget-events.jsonl"))).doesNotContain("secret-key");
 }
 @Test void failureToCountDoesNotFallBackToHeuristicOrSendPaidRequest() throws Exception {
  transport(new BigDecimal("4"),1000);var t=new ExperienceTransport(spec(new BigDecimal("4")),dir,(p,model,j,e)->{throw new IllegalStateException("secret");});var sent=new AtomicInteger();
  assertThatThrownBy(()->t.intercept(req(),body(),(r,b)->{sent.incrementAndGet();return null;})).hasMessageContaining("입력 토큰 계수 실패");
  assertThat(sent.get()).isZero();assertThat(budget().path("reservedUsd").decimalValue()).isZero();assertThat(Files.exists(dir.resolve("ledger.jsonl"))).isFalse();
 }
 @Test void differentRoleCapsAndSmallerExistingServiceCapsAreHonored() throws Exception {
  var t=transport(new BigDecimal("4"),1000);
  ExperiencePersonaClient.personaCall.set(true);
  try {t.intercept(req(),body(),(r,b)->{assertThat(m.readTree(b).path("max_output_tokens").asLong()).isEqualTo(4096);return response(1000,0,1);}).close();}
  finally {ExperiencePersonaClient.personaCall.remove();}
  var json=(tools.jackson.databind.node.ObjectNode)m.readTree(body());json.put("max_output_tokens",2048);
  t.intercept(req(),m.writeValueAsBytes(json),(r,b)->{assertThat(m.readTree(b).path("max_output_tokens").asLong()).isEqualTo(2048);return response(1000,0,1);}).close();
 }
 @Test void geminiCallsAreBlockedBeforeTokenCountingOrNetwork() throws Exception {
  var t=transport(BigDecimal.TEN,1000);var sent=new AtomicInteger();
  for(String operation:List.of("generateContent","embedContent")) {
   var request=new MockClientHttpRequest(HttpMethod.POST,URI.create("https://generativelanguage.googleapis.com/v1beta/models/gemini-test:"+operation));
   assertThatThrownBy(()->t.intercept(request,"{}".getBytes(),(r,b)->{sent.incrementAndGet();return response(1,0,1);}))
       .isInstanceOf(IllegalStateException.class);
  }
  assertThat(sent.get()).isZero();assertThat(t.budgetBlocked).isTrue();
 }
 @Test void openAiEmbeddingReservesInputOnlyAndSettlesPromptTokens() throws Exception {
  var t=transport(BigDecimal.TEN,1000);
  var request=new MockClientHttpRequest(HttpMethod.POST,URI.create("https://api.openai.com/v1/embeddings"));
  t.intercept(request,"{\"model\":\"text-embedding-3-small\",\"input\":\"memory\"}".getBytes(),(r,b)->{
   assertThat(m.readTree(b).has("max_output_tokens")).isFalse();
   return new MockClientHttpResponse("{\"data\":[{\"embedding\":[0.1]}],\"usage\":{\"prompt_tokens\":1000,\"total_tokens\":1000}}".getBytes(),HttpStatus.OK);
  }).close();
  assertThat(budget().path("outputCapTokens").asLong()).isZero();
  assertThat(budget().path("actualUsd").decimalValue()).isEqualByComparingTo("0.004");
  assertThat(budget().path("reservedUsd").decimalValue()).isZero();
 }
 @Test void recentTwelveResponseDistributionChangesForecastAndTargetHeadroomOnly() {
  var samples=new ArrayList<Long>();for(int i=0;i<12;i++)samples.add(4000L);samples.add(90000L);
  var p=ExperienceBudgetPolicy.create(samples);
  assertThat(p.get("sampleCount")).isEqualTo(12);assertThat(p.get("expectedTargetOutputTokens")).isEqualTo(4000L);
  assertThat(((Map<?,?>)p.get("outputCaps")).get("TARGET")).isEqualTo(12512L);
  assertThat(((Map<?,?>)p.get("outputCaps")).get("PLAYER_SIMULATION")).isEqualTo(4096);
 }
 @Test void allRolesShareOneFourDollarBalanceAndNoFurtherCallsAfterBudgetStop() throws Exception {
  var t=transport(new BigDecimal("4"),1000);var sent=new AtomicInteger();
  for(int i=0;i<100&&!t.budgetBlocked;i++) {
   ExperiencePersonaClient.personaCall.set(i%2==0);
   try {t.intercept(req(),body(),(r,b)->{sent.incrementAndGet();return response(1000,0,m.readTree(b).path("max_output_tokens").asLong());}).close();}
   catch(IllegalStateException e){assertThat(t.budgetBlocked).isTrue();}
   finally {ExperiencePersonaClient.personaCall.remove();}
  }
  assertThat(budget().path("actualUsd").decimalValue()).isLessThanOrEqualTo(new BigDecimal("4"));int before=sent.get();
  assertThatThrownBy(()->t.intercept(req(),body(),(r,b)->{sent.incrementAndGet();return response(1000,0,1);})).hasMessageContaining("already blocked");
  assertThat(sent.get()).isEqualTo(before);
 }
 @Test void missingUsageKeepsFullReservationAndNeverAssumesZeroCost() throws Exception {
  var t=transport(new BigDecimal("4"),1000);
  t.intercept(req(),body(),(r,b)->new MockClientHttpResponse("{}".getBytes(),HttpStatus.OK)).close();
  assertThat(budget().path("reservedUsd").decimalValue()).isEqualByComparingTo("0.205");assertThat(budget().path("uncertain").asBoolean()).isTrue();
  assertThatThrownBy(()->t.intercept(req(),body(),(r,b)->{throw new AssertionError("Must not send");})).hasMessageContaining("automatic retry prohibited");
 }

}
