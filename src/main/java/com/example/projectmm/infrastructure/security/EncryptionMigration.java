package com.example.projectmm.infrastructure.security;

import java.sql.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.io.InputStream;

/**
 * Offline maintenance command ONLY. Never registered as a bean/startup runner.
 * --dry-run authenticates all ciphertext and counts plaintext; --verify requires no plaintext.
 * --apply uses small atomic batches, compare-and-set updates, and read-back verification.
 */
public final class EncryptionMigration {
    public record Counts(long plaintext, long encrypted) {}
    private record Row(List<Object> keys, List<String> values, String hash) {}
    public static void main(String[] args) {
        try {
            if(args.length!=1 || !Set.of("--dry-run","--verify","--apply").contains(args[0]))
                throw new IllegalArgumentException("Use --dry-run, --verify, or --apply");
            var env=System.getenv();
            var cipher=new StoredTextCipher(required(env,"POCKETMM_DATA_ENCRYPTION_KEY"),true);
            String url=required(env,"POCKETMM_MIGRATION_DB_URL");
            boolean apply=args[0].equals("--apply");
            if(apply) verifyBackup(env,url);
            try(var connection=DriverManager.getConnection(url,required(env,"POCKETMM_MIGRATION_DB_USER"),
                    required(env,"POCKETMM_MIGRATION_DB_PASSWORD"))) {
                Counts counts=run(connection,cipher,apply);
                System.out.println("plaintext="+counts.plaintext()+" encrypted="+counts.encrypted()+" mode="+args[0]);
                if(args[0].equals("--verify") && counts.plaintext()!=0) throw new IllegalStateException("Plaintext remains");
            }
        } catch(Exception ex) {
            // Driver exceptions can contain SQL values/credentials. Do not print them.
            System.err.println("Migration stopped; no active batch committed. Inspect backup/schema/key and resume after validation.");
            System.exit(1);
        }
    }
    private static String required(Map<String,String> env,String name) {
        String value=env.get(name);
        if(value==null || value.isBlank()) throw new IllegalStateException("Missing "+name);
        return value;
    }
    static void verifyBackup(Map<String,String> env,String url) throws Exception {
        if(!"WRITERS_STOPPED".equals(env.get("POCKETMM_MIGRATION_MAINTENANCE")))
            throw new IllegalStateException("Maintenance required");
        Path backup=Path.of(required(env,"POCKETMM_MIGRATION_BACKUP_FILE"));
        var digest=MessageDigest.getInstance("SHA-256");
        try(InputStream in=Files.newInputStream(backup)) {
            byte[] buffer=new byte[65536];int n;
            while((n=in.read(buffer))!=-1) digest.update(buffer,0,n);
        }
        String hash=HexFormat.of().formatHex(digest.digest());
        var evidence=new Properties();
        try(InputStream in=Files.newInputStream(Path.of(required(env,"POCKETMM_MIGRATION_BACKUP_EVIDENCE")))) { evidence.load(in); }
        if(!hash.equals(evidence.getProperty("backup.sha256"))
                || !"true".equals(evidence.getProperty("restore.verified"))
                || !TextFingerprint.sha256(url).equals(evidence.getProperty("database.url.sha256"))
                || evidence.getProperty("restore.verified.at","").isBlank())
            throw new IllegalStateException("Backup/restore evidence mismatch");
    }
    public static Counts run(Connection connection,StoredTextCipher cipher,boolean apply) throws SQLException {
        long plain=0, encrypted=0;
        boolean originalAuto=connection.getAutoCommit();
        connection.setAutoCommit(false);
        try {
            for(var table:EncryptedColumns.TABLES) {
                int offset=0;
                for(;;) {
                    var rows=new ArrayList<Row>();
                    boolean detail=table.name().equals("protected_memory_detail");
                    String fields=String.join(",",table.keys())+","+String.join(",",table.columns())+(detail?",detail_hash":"");
                    try(var select=connection.prepareStatement("select "+fields+" from "+table.name()+" order by "+
                            String.join(",",table.keys())+" limit 200 offset ?")) {
                        select.setInt(1,offset);
                        try(var rs=select.executeQuery()) {
                            while(rs.next()) {
                                var keys=new ArrayList<Object>();var values=new ArrayList<String>();
                                for(String key:table.keys()) keys.add(rs.getObject(key));
                                for(String column:table.columns()) values.add(rs.getString(column));
                                rows.add(new Row(keys,values,detail?rs.getString("detail_hash"):null));
                            }
                        }
                    }
                    if(rows.isEmpty()) break;
                    for(var row:rows) {
                        for(int i=0;i<table.columns().size();i++) {
                            String old=row.values().get(i);if(old==null)continue;
                            String clear=cipher.decrypt(old); // malformed/unknown enc: values fail closed
                            boolean sealed=old.startsWith("enc:");
                            if(sealed) encrypted++; else plain++;
                            if(detail) {
                                String hash=cipher.fingerprint(clear);
                                if(row.hash()!=null && !hash.equals(row.hash())) throw new IllegalStateException("Detail hash mismatch");
                                if(apply && row.hash()==null) update(connection,table,row,"detail_hash",hash,null);
                            }
                            if(!apply || sealed) continue;
                            String sealedValue=cipher.encrypt(clear);
                            if(!clear.equals(cipher.decrypt(sealedValue))) throw new IllegalStateException("Round trip failed");
                            update(connection,table,row,table.columns().get(i),sealedValue,old);
                            try(var check=connection.prepareStatement("select "+table.columns().get(i)+" from "+table.name()+" where "+where(table))) {
                                bindKeys(check,row,1);
                                try(var rs=check.executeQuery()) {
                                    if(!rs.next() || !clear.equals(cipher.decrypt(rs.getString(1)))) throw new IllegalStateException("Read-back failed");
                                }
                            }
                        }
                    }
                    if(apply) connection.commit(); else connection.rollback();
                    offset+=rows.size();
                }
                System.out.println("checked table="+table.name()); // counts only, never values
            }
            return new Counts(plain,encrypted);
        } catch(SQLException | RuntimeException ex) {
            connection.rollback();throw ex;
        } finally { connection.setAutoCommit(originalAuto); }
    }
    private static String where(EncryptedColumns.Table table) {
        return String.join(" and ",table.keys().stream().map(k->k+"=?").toList());
    }
    private static int bindKeys(PreparedStatement statement,Row row,int start) throws SQLException {
        for(Object key:row.keys()) statement.setObject(start++,key);return start;
    }
    private static void update(Connection connection,EncryptedColumns.Table table,Row row,String column,String value,String old) throws SQLException {
        try(var update=connection.prepareStatement("update "+table.name()+" set "+column+"=? where "+where(table)+
                " and "+column+(old==null?" is null":"=?"))) {
            update.setString(1,value);
            int index=bindKeys(update,row,2);
            if(old!=null)update.setString(index,old);
            if(update.executeUpdate()!=1)throw new IllegalStateException("Concurrent update; stop writers");
        }
    }
}
