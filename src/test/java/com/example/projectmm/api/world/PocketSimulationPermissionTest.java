package com.example.projectmm.api.world;
import com.example.projectmm.application.chat.PocketProgressService;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;
class PocketSimulationPermissionTest {
    @Test void ordinaryMemberCannotSimulateButDeveloperCan() {
        var service=mock(PocketProgressService.class);
        var controller=new PocketProgressController(service);
        var user=new UsernamePasswordAuthenticationToken("member",null,List.of(new SimpleGrantedAuthority("ROLE_USER")));
        assertThatThrownBy(()->controller.simulate(9L,new PocketProgressController.Simulation(1),user))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class).hasMessageContaining("403");
        verifyNoInteractions(service);
        var admin=new UsernamePasswordAuthenticationToken("developer",null,List.of(new SimpleGrantedAuthority("ROLE_DEVELOPER")));
        controller.simulate(9L,new PocketProgressController.Simulation(1),admin);
        verify(service).simulateAbsence(9L,"developer",1);
    }
    @Test void originalStoryTimelineIsReturnedOnlyToDeveloper() {
        var service=mock(PocketProgressService.class);var controller=new PocketProgressController(service);
        var user=new UsernamePasswordAuthenticationToken("member",null,List.of(new SimpleGrantedAuthority("ROLE_USER")));
        var admin=new UsernamePasswordAuthenticationToken("developer",null,List.of(new SimpleGrantedAuthority("ROLE_DEVELOPER")));
        var activity=new com.example.projectmm.application.chat.PocketActivityPlan.Activity(
                java.time.LocalDateTime.now(),java.time.LocalDateTime.now().plusMinutes(10),
                com.example.projectmm.application.chat.PocketActivityPlan.Kind.REST,List.of(1L),"Home","Original",false,List.of());
        var usage=new tools.jackson.databind.ObjectMapper().readTree("{\"model\":\"actual\",\"totalTokens\":150}");
        var view=new PocketProgressService.StoryReport(7L,List.of(activity),List.of("Brief"),"Saved prose",false,usage,usage);
        when(service.readStory(eq(9L),eq(7L),anyString())).thenReturn(view);
        assertThat(controller.story(9L,7L,user).activities()).isEmpty();
        assertThat(controller.story(9L,7L,user).originalUsage()).isNull();
        assertThat(controller.story(9L,7L,admin).originalUsage()).isEqualTo(usage);
        assertThat(controller.story(9L,7L,user).briefLogs()).containsExactly("Brief");
        assertThat(controller.story(9L,7L,admin).activities()).containsExactly(activity);
        when(service.generateStory(eq(9L),eq(7L),eq("member"))).thenReturn(view);
        assertThat(controller.generateStory(9L,7L,user).activities()).isEmpty();
        verify(service).generateStory(9L,7L,"member");
    }
}
