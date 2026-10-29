package com.example.projectmm.infrastructure.security;

import com.example.projectmm.domain.character.Character;
import com.example.projectmm.domain.chat.*;
import com.example.projectmm.domain.memory.*;
import com.example.projectmm.application.policy.ContentMode;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import java.time.LocalDateTime;
import java.util.Base64;
import java.security.SecureRandom;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import static org.assertj.core.api.Assertions.*;

@DataJpaTest(properties={"spring.datasource.driver-class-name=org.h2.Driver","spring.jpa.hibernate.ddl-auto=create-drop","spring.jpa.show-sql=false","pocketmm.encryption.required=true"},showSql=false)
class EncryptedJpaIntegrationTest {
    @DynamicPropertySource static void key(DynamicPropertyRegistry registry) {
        String key=StoredTextCipherTest.key();
        registry.add("POCKETMM_DATA_ENCRYPTION_KEY",()->key);
    }
    @Autowired EntityManager em;
    @Test void realJpaWritesCiphertextAndReadsMessagesFactsAndEmbeddings() {
        var character=new Character("Test");em.persist(character);
        var conversation=new Conversation(character,LocalDateTime.now());em.persist(conversation);
        var message=new ChatMessage(conversation,character,ChatRole.USER,"나는 커피를 좋아해",ContentMode.NORMAL,LocalDateTime.now());
        em.persist(message);
        var memory=Memory.fromChat(conversation,message,"나는 커피를 좋아해",5,LocalDateTime.now(),"fingerprint",
                new MemoryFact(MemoryFact.Kind.PREFERENCE,"나는","커피를","좋아해"));em.persist(memory);
        memory.attachSemanticMemory(new SemanticMemory(SemanticMemory.Type.PREFERENCE,"나","좋아함","커피",
                SemanticMemory.Polarity.POSITIVE,SemanticMemory.Temporality.PRESENT,SemanticMemory.Certainty.CERTAIN));
        var detail=new ProtectedMemoryDetail(memory,"커피",MemoryDetailSalience.values()[0],true);em.persist(detail);
        em.persist(new com.example.projectmm.infrastructure.memory.MemoryEmbeddingEntity(memory.getId(),"doc-hash","[0.1,0.2]"));
        em.flush();
        assertThat((String)em.createNativeQuery("select detail_hash from protected_memory_detail").getSingleResult())
                .isEqualTo(detail.getDetailHash()).isNotEqualTo(TextFingerprint.sha256("커피"));
        for(String sql:new String[]{"select content from chat_messages","select summary from memories","select evidence_quote from memories",
                "select fact_value from memories","select semantic_object from memories","select detail_text from protected_memory_detail","select embedding from memory_embeddings"})
            assertThat((String)em.createNativeQuery(sql).getSingleResult()).startsWith("enc:v2:").doesNotContain("커피");
        Long id=message.getId(),memoryId=memory.getId();em.clear();
        assertThat(em.find(ChatMessage.class,id).getContent()).isEqualTo("나는 커피를 좋아해");
        assertThat(em.find(Memory.class,memoryId).getFact().getValue()).isEqualTo("좋아해");
    }
}
