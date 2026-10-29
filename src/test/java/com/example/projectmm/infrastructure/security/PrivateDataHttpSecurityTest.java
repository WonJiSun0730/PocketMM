package com.example.projectmm.infrastructure.security;

import com.example.projectmm.domain.character.CharacterRepository;
import com.example.projectmm.domain.character.Character;
import com.example.projectmm.domain.member.Member;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import java.util.Optional;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringJUnitWebConfig(PrivateDataHttpSecurityTest.Config.class)
@TestPropertySource(properties="app.private-test.enabled=false")
class PrivateDataHttpSecurityTest {
    @Configuration @EnableWebMvc @EnableWebSecurity
    @Import({SecurityConfig.class,PrivateDataAccessConfiguration.class,Probe.class})
    static class Config {
        @Bean UserDetailsService users() { return new InMemoryUserDetailsManager(); }
        @Bean CharacterRepository characters() { return mock(CharacterRepository.class); }
        @Bean PlatformTransactionManager transactions() {
            var tx=mock(PlatformTransactionManager.class);
            when(tx.getTransaction(any())).thenReturn(mock(TransactionStatus.class));return tx;
        }
    }
    @RestController static class Probe {
        @GetMapping("/api/admin/private-data/probe") String operatorProbe(){return "verified session";}
        @GetMapping({"/api/chat/{characterId}/messages","/api/worlds/{worldId}/characters/{characterId}/messages"})
        String history(){return "owned history";}
    }
    @Autowired WebApplicationContext context;
    @Autowired CharacterRepository characters;
    @Test void anonymousAndOtherOwnersCannotReadLegacyMessagesEvenWithAdminRole() throws Exception {
        var member=mock(Member.class);when(member.getLoginId()).thenReturn("owner");
        var character=mock(Character.class);when(character.getOwner()).thenReturn(member);
        when(characters.findById(7L)).thenReturn(Optional.of(character));
        var mvc=MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        mvc.perform(get("/api/chat/7/messages")).andExpect(status().is3xxRedirection());
        mvc.perform(get("/api/chat/7/messages").with(user("other").roles("ADMIN"))).andExpect(status().isNotFound());
        mvc.perform(get("/api/chat/7/messages").with(user("owner"))).andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"));
        mvc.perform(get("/api/worlds/1/characters/7/messages").with(user("other"))).andExpect(status().isNotFound());
        when(character.getOwner()).thenReturn(null);
        mvc.perform(get("/api/chat/7/messages").with(user("owner"))).andExpect(status().isNotFound());
    }
}
