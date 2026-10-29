package com.example.projectmm.infrastructure.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.*;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;
import javax.sql.DataSource;
import static org.assertj.core.api.Assertions.*;

@SpringJUnitConfig(PrivateDataAuditTest.Config.class)
class PrivateDataAuditTest {
    @Configuration @EnableTransactionManagement @Import(PrivateDataAudit.class)
    static class Config {
        @Bean DataSource dataSource(){return new DriverManagerDataSource("jdbc:h2:mem:audit;DB_CLOSE_DELAY=-1","sa","");}
        @Bean JdbcTemplate jdbc(DataSource ds){return new JdbcTemplate(ds);}
        @Bean PlatformTransactionManager tx(DataSource ds){return new DataSourceTransactionManager(ds);}
    }
    @Autowired PrivateDataAudit audit;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager tx;
    @Test void auditSurvivesCallerRollbackAndContainsOnlyMetadata() {
        jdbc.execute("create table private_data_audit(request_id varchar(36),actor varchar(100),action varchar(16),target varchar(32),owner_id bigint,scope_id bigint,target_ids varchar(500),reason varchar(500),outcome varchar(16),result_count int,occurred_at timestamp)");
        new TransactionTemplate(tx).executeWithoutResult(status->{
            audit.record("request","operator","DECRYPT","POCKET",1,2,"[3]","Support ticket","REQUESTED",0);
            status.setRollbackOnly();
        });
        assertThat(jdbc.queryForObject("select count(*) from private_data_audit",Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select outcome from private_data_audit",String.class)).isEqualTo("REQUESTED");
    }
}
