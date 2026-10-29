package com.example.projectmm.infrastructure.security;

import org.junit.jupiter.api.Test;
import java.sql.*;
import java.util.*;
import java.nio.file.*;
import static org.assertj.core.api.Assertions.*;

class EncryptionMigrationTest {
    @Test void dryRunThenApplyThenVerifyIsResumableAndLeavesQaAlone() throws Exception {
        try(var db=database()) {
            var cipher=new StoredTextCipher(StoredTextCipherTest.key(),true);
            insert(db,"chat_messages","id,content","1,'private user message'");
            insert(db,"protected_memory_detail","id,detail_text","1,'memory detail'");
            db.createStatement().execute("create table qa_records(content varchar(100))");
            db.createStatement().execute("insert into qa_records values ('QA plaintext')");
            assertThat(EncryptionMigration.run(db,cipher,false).plaintext()).isEqualTo(2);
            assertThat(value(db,"chat_messages","content")).isEqualTo("private user message");
            assertThat(EncryptionMigration.run(db,cipher,true).plaintext()).isEqualTo(2);
            assertThat(value(db,"chat_messages","content")).startsWith("enc:v2:");
            assertThat(cipher.decrypt(value(db,"protected_memory_detail","detail_text"))).isEqualTo("memory detail");
            assertThat(value(db,"protected_memory_detail","detail_hash")).isEqualTo(cipher.fingerprint("memory detail"));
            assertThat(EncryptionMigration.run(db,cipher,true).plaintext()).isZero();
            assertThat(EncryptionMigration.run(db,cipher,false).encrypted()).isEqualTo(2);
            assertThat(value(db,"qa_records","content")).isEqualTo("QA plaintext");
        }
    }
    @Test void corruptedPayloadRollsBackCurrentBatch() throws Exception {
        try(var db=database()) {
            insert(db,"chat_messages","id,content","1,'clear'");
            insert(db,"chat_messages","id,content","2,'enc:v2:broken'");
            assertThatThrownBy(()->EncryptionMigration.run(db,new StoredTextCipher(StoredTextCipherTest.key(),true),true)).isInstanceOf(IllegalStateException.class);
            assertThat(value(db,"chat_messages","content")).isEqualTo("clear");
        }
    }
    @Test void applyRequiresVerifiedBackupForExactDatabase() throws Exception {
        assertThatThrownBy(()->EncryptionMigration.verifyBackup(Map.of(),"jdbc:target")).isInstanceOf(IllegalStateException.class);
        Path backup=Files.createTempFile("backup",".sql"),evidence=Files.createTempFile("restore",".properties");
        try {
            Files.writeString(backup,"backup test data");
            var env=new HashMap<String,String>();env.put("POCKETMM_MIGRATION_MAINTENANCE","WRITERS_STOPPED");
            env.put("POCKETMM_MIGRATION_BACKUP_FILE",backup.toString());env.put("POCKETMM_MIGRATION_BACKUP_EVIDENCE",evidence.toString());
            Files.writeString(evidence,"backup.sha256="+TextFingerprint.sha256("backup test data")+"\nrestore.verified=true\nrestore.verified.at=2026-10-29\ndatabase.url.sha256="+TextFingerprint.sha256("jdbc:target"));
            EncryptionMigration.verifyBackup(env,"jdbc:target");
            assertThatThrownBy(()->EncryptionMigration.verifyBackup(env,"jdbc:other")).isInstanceOf(IllegalStateException.class);
            Files.writeString(backup,"tampered");
            assertThatThrownBy(()->EncryptionMigration.verifyBackup(env,"jdbc:target")).isInstanceOf(IllegalStateException.class);
        } finally {Files.deleteIfExists(backup);Files.deleteIfExists(evidence);}
    }
    static Connection database() throws SQLException {
        var db=DriverManager.getConnection("jdbc:h2:mem:"+UUID.randomUUID(),"sa","");
        for(var t:EncryptedColumns.TABLES) {
            var columns=new ArrayList<String>();for(String k:t.keys())columns.add(k+" bigint");
            for(String c:t.columns())columns.add(c+" clob");
            if(t.name().equals("protected_memory_detail"))columns.add("detail_hash varchar(64)");
            db.createStatement().execute("create table "+t.name()+" ("+String.join(",",columns)+")");
        }
        return db;
    }
    static void insert(Connection db,String table,String columns,String values) throws SQLException {
        db.createStatement().execute("insert into "+table+" ("+columns+") values ("+values+")");
    }
    static String value(Connection db,String table,String column) throws SQLException {
        try(var r=db.createStatement().executeQuery("select "+column+" from "+table)){r.next();return r.getString(1);}
    }
}
