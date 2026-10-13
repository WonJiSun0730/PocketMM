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
}
