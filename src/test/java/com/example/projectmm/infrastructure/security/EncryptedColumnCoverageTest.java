package com.example.projectmm.infrastructure.security;

import jakarta.persistence.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class EncryptedColumnCoverageTest {
    @Test void migrationManifestMatchesEveryEncryptedJpaColumnAndExcludesQaSnapshots() throws Exception {
        var actual=new TreeSet<String>();
        for(String name:List.of("domain.chat.ChatMessage","domain.chat.WorldRoomMessage","domain.chat.WorldRoomMessageVariant",
                "domain.chat.WorldRoomSession","domain.chat.WorldRoomPlayerState","domain.chat.PocketActivityReport",
                "domain.memory.Memory","domain.memory.MemoryFact","domain.memory.SemanticMemory",
                "domain.memory.ProtectedMemoryDetail","infrastructure.memory.MemoryEmbeddingEntity","domain.chat.BugReport")) {
            Class<?> type=Class.forName("com.example.projectmm."+name);
            for(var field:type.getDeclaredFields()) {
                if(field.getAnnotation(Convert.class)==null)continue;
                var collection=field.getAnnotation(CollectionTable.class);
                String table=collection!=null?collection.name():type.isAnnotationPresent(Embeddable.class)?"memories":type.getAnnotation(Table.class).name();
                var column=field.getAnnotation(Column.class);
                String columnName=column.name().isEmpty()?field.getName().replaceAll("([a-z0-9])([A-Z])","$1_$2").toLowerCase(Locale.ROOT):column.name();
                actual.add(table+"."+columnName);
                assertThat(column.columnDefinition()).isEqualTo("LONGTEXT");
            }
        }
        var expected=new TreeSet<String>();
        for(var table:EncryptedColumns.TABLES)for(String column:table.columns())expected.add(table.name()+"."+column);
        assertThat(actual).isEqualTo(expected);
        var variant=com.example.projectmm.domain.chat.WorldRoomMessageVariant.class;
        for(String qa:List.of("evaluationContextJson","testDiagnosticsJson","testMemoryBeforeJson","testMemoryAfterJson"))
            assertThat(variant.getDeclaredField(qa).getAnnotation(Convert.class)).isNull();
    }
    @Test void everyManifestColumnMigratesAndAuthenticatesIncludingCollectionRows() throws Exception {
        try(var db=EncryptionMigrationTest.database()) {
            int count=0;
            for(var table:EncryptedColumns.TABLES) {
                var fields=new ArrayList<String>(table.keys());fields.addAll(table.columns());
                try(var insert=db.prepareStatement("insert into "+table.name()+" ("+String.join(",",fields)+") values ("+
                        String.join(",",Collections.nCopies(fields.size(),"?"))+")")) {
                    int index=1;for(String key:table.keys())insert.setLong(index++,1);
                    for(String column:table.columns()){insert.setString(index++,"private-"+column);count++;}
                    insert.executeUpdate();
                }
            }
            var cipher=new StoredTextCipher(StoredTextCipherTest.key(),true);
            assertThat(EncryptionMigration.run(db,cipher,true).plaintext()).isEqualTo(count);
            assertThat(EncryptionMigration.run(db,cipher,false).encrypted()).isEqualTo(count);
        }
    }
}
