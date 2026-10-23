package com.example.projectmm.infrastructure.memory;

import com.example.projectmm.application.memory.*;
import com.example.projectmm.domain.chat.*;
import com.example.projectmm.domain.character.Character;
import com.example.projectmm.domain.member.Member;
import com.example.projectmm.domain.world.World;
import com.example.projectmm.domain.memory.ChatMemoryCandidate;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;
import tools.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@DataJpaTest(properties={"spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop","gemini.api-key=","openai.api-key="},showSql=false)
@Import({PocketMemoryWriter.class,MemoryDetailService.class,PocketMemoryInspectorService.class})
class OpenAiPocketMemoryPersistenceTest {
    @Autowired EntityManager em;
    @Autowired PocketMemoryWriter writer;
    @Autowired PocketMemoryInspectorService inspector;
    @Autowired com.example.projectmm.domain.memory.MemoryRepository memories;

    @Test void openAiCandidatesPersistInTheirPocketAndInspectorAfterReloadWithoutInventedFacts() {
        var encoder=mock(PasswordEncoder.class);
        when(encoder.encode("test")).thenReturn("encoded");
        var owner=Member.createLocal("memory-test","test","Memory Test","memory@example.test",encoder);
        em.persist(owner);
        var world=new World(owner,"Memory World","Test world"); em.persist(world);
        var at=LocalDateTime.of(2026,10,15,9,0);
        var room=new WorldConversation(world,owner,"Room",WorldConversation.Visibility.PRIVATE,at); em.persist(room);
        var intro=new WorldRoomIntro(room,"Intro","A new scene",0); em.persist(intro);
        var session=new WorldRoomSession(room,owner,null,intro,at);
        session.updatePocketSettings(null,null,true,"OPENAI","gpt-5.6-sol"); em.persist(session);
        var character=new Character("Marbas",owner); character.assignWorld(world); em.persist(character);
        var cast=new WorldRoomSessionCast(session,character); cast.enter(at); em.persist(cast);
        String user="私の話: 私が先に話すまで傷について質問しないでください。";
        var source=new WorldRoomMessage(session,ChatRole.USER,user,at); em.persist(source);
        var mapper=new ObjectMapper();
        var extractor=new OpenAiChatMemoryExtractor("never-sent","gpt-5.6-luna",mapper);
        String json=mapper.writeValueAsString(Map.of("memories",List.of(Map.of("quote",user,"importance",6,"details",List.of()))));
        String response=mapper.writeValueAsString(Map.of("status","completed","output",List.of(Map.of("type","message",
                "content",List.of(Map.of("type","output_text","text",json))))));
        var candidates=new ArrayList<>(extractor.parseResponse(response));
        candidates.add(new ChatMemoryCandidate("원문에 없는 사건이 벌어졌다.",6));
        var result=writer.saveUserSpeech(session,source,character,user,candidates,at);
        assertThat(result.status()).isEqualTo("SAVED");
        assertThat(result.saved()).hasSize(1);
        long id=session.getId(); em.flush(); em.clear();
        assertThat(memories.findForWorldRoomRecall(id,character.getId(),at)).hasSize(1);
        assertThat(memories.findForWorldRoomRecall(id,character.getId(),at.minusSeconds(1))).isEmpty();
        assertThat(memories.findForWorldRoomRecall(id,-1L,at)).isEmpty();
        assertThat(memories.findForWorldRoomRecall(-1L,character.getId(),at)).isEmpty();
        var groups=inspector.latest(id);
        assertThat(groups).hasSize(1);
        assertThat(groups.getFirst().memories()).hasSize(1);
        assertThat(groups.getFirst().memories().getFirst().evidenceQuote()).isEqualTo(user);
        assertThat(inspector.latestTurnDiff(id).added()).isEqualTo(1);
    }
}
