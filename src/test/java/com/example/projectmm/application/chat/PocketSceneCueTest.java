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
}
