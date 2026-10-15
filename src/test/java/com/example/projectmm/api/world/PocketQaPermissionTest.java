package com.example.projectmm.api.world;

import com.example.projectmm.application.chat.WorldRoomSessionService;
import com.example.projectmm.application.qa.QaUiService;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class PocketQaPermissionTest {
    @Test void allEndpointsRejectNonDeveloperBeforeReadingAnyRunOrPocket() {
        var service=mock(QaUiService.class);var sessions=mock(WorldRoomSessionService.class);var controller=new PocketQaController(service,sessions);
        var user=new UsernamePasswordAuthenticationToken("user",null,List.of(new SimpleGrantedAuthority("ROLE_USER")));
        assertThatThrownBy(()->controller.capabilities(1L,user)).isInstanceOf(ResponseStatusException.class).hasMessageContaining("403");
        assertThatThrownBy(()->controller.list(1L,user)).hasMessageContaining("403");
        assertThatThrownBy(()->controller.start(1L,null,user)).hasMessageContaining("403");
        assertThatThrownBy(()->controller.current(1L,"ui-123",user)).hasMessageContaining("403");
        assertThatThrownBy(()->controller.control(1L,"ui-123","stop",user)).hasMessageContaining("403");
        assertThatThrownBy(()->controller.report(1L,"ui-123","json",user)).hasMessageContaining("403");
        assertThatThrownBy(()->controller.capabilities(1L,null)).hasMessageContaining("403");verifyNoInteractions(service,sessions);
    }
    @Test void developerMustOwnPocketBeforeAccessingQaRuns() throws Exception {
        var service=mock(QaUiService.class);var sessions=mock(WorldRoomSessionService.class);var controller=new PocketQaController(service,sessions);
        var developer=new UsernamePasswordAuthenticationToken("developer",null,List.of(new SimpleGrantedAuthority("ROLE_DEVELOPER")));
        when(sessions.pocket(2L,"developer")).thenThrow(new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND));
        assertThatThrownBy(()->controller.list(2L,developer)).hasMessageContaining("404");verifyNoInteractions(service);
        controller.list(1L,developer);verify(sessions).pocket(1L,"developer");verify(service).list(1L,"developer");
    }
}
