package com.example.projectmm.qa;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.sql.DriverManager;
import static org.assertj.core.api.Assertions.*;

class QaH2LifecycleTest {
    @TempDir Path temporary;
    @Test void isolatedFileDatabaseClosesAndReopensWithoutLosingCommittedRows() throws Exception {
        String url="jdbc:h2:file:"+temporary.resolve("new-mock-db").toString().replace('\\','/')+";DB_CLOSE_ON_EXIT=FALSE;MAX_COMPACT_TIME=0";
        try(var connection=DriverManager.getConnection(url,"sa","");var sql=connection.createStatement()) {
            sql.execute("CREATE TABLE evidence(id INT PRIMARY KEY)");sql.execute("INSERT INTO evidence VALUES(1)");
        }
        try(var connection=DriverManager.getConnection(url,"sa","");var sql=connection.createStatement();var rows=sql.executeQuery("SELECT COUNT(*) FROM evidence")) {
            assertThat(rows.next()).isTrue();assertThat(rows.getInt(1)).isEqualTo(1);
        }
    }
}
