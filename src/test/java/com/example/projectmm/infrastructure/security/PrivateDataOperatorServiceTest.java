package com.example.projectmm.infrastructure.security;

import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class PrivateDataOperatorServiceTest {
    JdbcTemplate jdbc; StoredTextCipher cipher; PrivateDataAudit audit; PrivateDataOperatorService service;
    @BeforeEach void prepare() {
        jdbc=new JdbcTemplate(new DriverManagerDataSource("jdbc:h2:mem:"+UUID.randomUUID()+";DB_CLOSE_DELAY=-1","sa",""));
        cipher=new StoredTextCipher(StoredTextCipherTest.key(),true);audit=mock(PrivateDataAudit.class);
        service=new PrivateDataOperatorService(jdbc,cipher,audit,"operator");
        jdbc.execute("create table world_room_sessions(id bigint, member_id bigint)");
        jdbc.execute("create table world_room_messages(id bigint, session_id bigint, content clob)");
        jdbc.execute("insert into world_room_sessions values(10,1),(11,2)");
        jdbc.update("insert into world_room_messages values(100,10,?),(101,11,?)",cipher.encrypt("private one"),cipher.encrypt("private two"));
    }
    static UsernamePasswordAuthenticationToken auth(String name,String role) {
        return new UsernamePasswordAuthenticationToken(name,"",List.of(new SimpleGrantedAuthority(role)));
    }
    static PrivateDataOperatorService.Request request(long owner,long scope,List<Long> ids) {
        return new PrivateDataOperatorService.Request(PrivateDataOperatorService.Target.POCKET,owner,scope,null,ids,"Support ticket 12345");
    }
    @Test void requiresBothAllowlistAndRole() {
        assertThatThrownBy(()->service.search(auth("other","ROLE_ADMIN"),request(1,10,null))).hasMessageContaining("403");
        assertThatThrownBy(()->service.search(auth("operator","ROLE_USER"),request(1,10,null))).hasMessageContaining("403");
        assertThatThrownBy(()->service.search(null,request(1,10,null))).hasMessageContaining("401");
        verify(audit,times(2)).record(anyString(),anyString(),eq("ACCESS"),eq("UNKNOWN"),eq(0L),eq(0L),eq(""),anyString(),eq("DENIED"),eq(0));
    }
    @Test void searchReturnsOnlyIdsAndDecryptNeedsExactOwnerScopeIdsAndReason() {
        var auth=auth("operator","ROLE_ADMIN");
        assertThat(service.search(auth,request(1,10,null))).containsExactly(new PrivateDataOperatorService.Metadata(100));
        assertThat(service.search(auth,request(2,10,null))).isEmpty();
        assertThat(service.decrypt(auth,request(1,10,List.of(100L)))).containsExactly(new PrivateDataOperatorService.Disclosure(100,"private one"));
        assertThatThrownBy(()->service.decrypt(auth,request(1,10,List.of(100L,101L)))).hasMessageContaining("404");
        assertThatThrownBy(()->service.decrypt(auth,request(1,10,Collections.nCopies(21,100L)))).hasMessageContaining("400");
        verify(audit,atLeastOnce()).record(anyString(),eq("operator"),eq("DECRYPT"),eq("POCKET"),eq(1L),eq(10L),anyString(),eq("Support ticket 12345"),eq("FAILED"),eq(0));
    }
    @Test void auditFailurePreventsDisclosureEvenAfterSuccessfulDecrypt() {
        doThrow(new IllegalStateException("audit unavailable")).when(audit).record(anyString(),anyString(),anyString(),anyString(),anyLong(),anyLong(),anyString(),anyString(),eq("SUCCESS"),anyInt());
        assertThatThrownBy(()->service.decrypt(auth("operator","ROLE_DEVELOPER"),request(1,10,List.of(100L)))).hasMessageContaining("503");
        reset(audit);
        doThrow(new IllegalStateException("audit unavailable")).when(audit).record(anyString(),anyString(),anyString(),anyString(),anyLong(),anyLong(),anyString(),anyString(),eq("REQUESTED"),anyInt());
        assertThatThrownBy(()->service.decrypt(auth("operator","ROLE_ADMIN"),request(1,10,List.of(100L)))).isInstanceOf(IllegalStateException.class);
    }
}
