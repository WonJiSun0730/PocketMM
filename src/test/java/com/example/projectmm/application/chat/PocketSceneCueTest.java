package com.example.projectmm.application.chat;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
class PocketSceneCueTest {
 @Test void validatesVisualPosesWithoutAnyCastMutation() {
  for (String pose : List.of("DEPART","ARRIVE","WAVE","ARMS_CROSSED"))
   assertEquals(pose,new PocketActivityPlan.ActorCue("player",List.of(),"CALM",0,pose).pose());
  assertEquals("LIGHT",new PocketActivityPlan.ActorCue("player",List.of(),"CALM",0,"DELETE").pose());
  assertEquals("LIGHT",new PocketActivityPlan.ActorCue("player",List.of(),"CALM",0,null).pose());
 }
 @Test void completedArrivalOverridesOnlyTheReturningActorsDeparture() {
  assertEquals("ARRIVE",PocketActivityPlan.arrivalPose("마르바스는 상담실의 일과를 마치고 집으로 돌아왔고, 밀리는 그와 함께 귀가길을 보냈다.","마르바스","DEPART"));
  assertEquals("DEPART",PocketActivityPlan.arrivalPose("마르바스는 밀리가 집으로 돌아왔을 때 밖으로 나갔다.","마르바스","DEPART"));
  assertEquals("DEPART",PocketActivityPlan.arrivalPose("밀리는 그와 함께 귀가길을 보냈다.","밀리","DEPART"));
  assertEquals("DEPART",PocketActivityPlan.arrivalPose("마르바스는 집으로 돌아와 달라는 연락을 받았다.","마르바스","DEPART"));
  assertEquals("DEPART",PocketActivityPlan.arrivalPose("마르바스는 밀리가 돌아왔다는 소식을 들었다.","마르바스","DEPART"));
  assertEquals("DEPART",PocketActivityPlan.arrivalPose("마르바스는 집으로 돌아왔고 다시 밖으로 나갔다.","마르바스","DEPART"));
 }
}
