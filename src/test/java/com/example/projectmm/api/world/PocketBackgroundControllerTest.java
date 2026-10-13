package com.example.projectmm.api.world;

import com.example.projectmm.application.chat.PocketBackgroundService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class PocketBackgroundControllerTest {
    @Test void foreignPocketCannotReadImageOrResolveOrQueueGeneration() throws Exception {
        var service=mock(PocketBackgroundService.class);
        when(service.context(9,"other")).thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND));
        var mvc=MockMvcBuilders.standaloneSetup(new PocketBackgroundController(service)).build();
        var auth=new UsernamePasswordAuthenticationToken("other","",java.util.List.of());
        mvc.perform(get("/api/worlds/pockets/9/backgrounds").param("location","공원").principal(auth)).andExpect(status().isNotFound());
        mvc.perform(get("/api/worlds/pockets/9/backgrounds/images/"+"a".repeat(32)+"/background").principal(auth)).andExpect(status().isNotFound());
        mvc.perform(post("/api/worlds/pockets/9/backgrounds").principal(auth).contentType("application/json").content("{\"location\":\"공원\"}")).andExpect(status().isNotFound());
        verify(service,never()).request(any(),anyString());verify(service,never()).image(any(),anyString(),anyString());
    }
    @Test void asynchronousGenerationReturnsAcceptedRatherThanHoldingTheRequest() throws Exception {
        var service=mock(PocketBackgroundService.class);var context=new PocketBackgroundService.Context(9,"1-2","world");
        when(service.context(9,"owner")).thenReturn(context);
        when(service.request(context,"공원")).thenReturn(new PocketBackgroundService.Result("GENERATING",null,false,null,true,true));
        var mvc=MockMvcBuilders.standaloneSetup(new PocketBackgroundController(service)).build();
        mvc.perform(post("/api/worlds/pockets/9/backgrounds").principal(new UsernamePasswordAuthenticationToken("owner","",java.util.List.of()))
            .contentType("application/json").content("{\"location\":\"공원\"}")).andExpect(status().isAccepted()).andExpect(jsonPath("$.status").value("GENERATING"));
    }
}
