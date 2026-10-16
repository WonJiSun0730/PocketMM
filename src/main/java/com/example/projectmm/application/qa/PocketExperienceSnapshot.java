package com.example.projectmm.application.qa;

import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.ObjectMapper;
import java.sql.*;
import java.time.Instant;
import java.util.*;

/** Whitelisted, single-pocket SQL snapshot. No account credentials or unrelated pockets. */
public record PocketExperienceSnapshot(long sessionId, String capturedAt, String digest,
        String extractorProvider, String extractorModel, String compressorModel, String timeModel, String embeddingModel,
        boolean memoryEnabled, List<Table> tables, List<String> differences) {
    public record Cell(int kind,String value) {}
    public record Table(String name,List<Map<String,Cell>> rows) {}
    public static final Map<String,String> SELECTIONS;
    static {
        var q=new LinkedHashMap<String,String>();
        String s="(SELECT id FROM world_room_sessions WHERE id=?)";
        String room="(SELECT room_id FROM world_room_sessions WHERE id=?)";
        String world="(SELECT world_id FROM world_conversations WHERE id IN "+room+")";
        String characters="(SELECT character_id FROM world_room_session_cast WHERE session_id=? UNION SELECT character_id FROM world_conversation_cast WHERE world_conversation_id IN "+room+" UNION SELECT character_id FROM memories WHERE world_room_session_id=?)";
        String systems="(SELECT id FROM world_systems WHERE world_id IN "+world+")";
        String intro="(SELECT intro_id FROM world_room_sessions WHERE id=?)";
        String messages="(SELECT id FROM world_room_messages WHERE session_id IN "+s+")";
        String variants="(SELECT id FROM world_room_message_variants WHERE message_id IN "+messages+")";
        String memories="(SELECT id FROM memories WHERE world_room_session_id IN "+s+")";
        q.put("worlds","id IN "+world+" OR id IN (SELECT world_id FROM characters WHERE id IN "+characters+")"); q.put("characters","id IN "+characters);
        for(String t:List.of("character_profiles","character_examples","character_states"))q.put(t,"character_id IN "+characters);
        q.put("world_lore_entries","world_id IN "+world);q.put("world_systems","world_id IN "+world);q.put("world_system_states","world_system_id IN "+systems);
        q.put("relationships","from_character_id IN "+characters+" AND to_character_id IN "+characters);
        q.put("relationship_facts","from_character_id IN "+characters+" AND to_character_id IN "+characters);
        q.put("custom_state_definitions","player_profile_id IN (SELECT player_profile_id FROM world_room_sessions WHERE id=?)");
        q.put("player_profiles","id IN (SELECT player_profile_id FROM world_room_sessions WHERE id=?)");
        q.put("world_conversations","id IN "+room);q.put("world_conversation_cast","world_conversation_id IN "+room);
        q.put("world_room_intros","id IN "+intro);
        q.put("world_room_intro_conditions","intro_id IN "+intro);q.put("world_room_intro_inventory","intro_id IN "+intro);
        q.put("world_room_sessions","id=?");
        for(String t:List.of("world_room_session_initial_conditions","world_room_session_initial_inventory","world_room_session_cast","world_room_session_system_states","world_room_player_states","world_room_messages","pocket_activity_reports"))q.put(t,"session_id IN "+s);
        for(String t:List.of("world_room_player_conditions","world_room_player_inventory"))q.put(t,"player_state_id IN (SELECT id FROM world_room_player_states WHERE session_id IN "+s+")");
        q.put("world_room_message_variants","message_id IN "+messages);
        for(String t:List.of("world_room_variant_cast_snapshots","world_room_variant_system_snapshots"))q.put(t,"variant_id IN "+variants);
        q.put("memories","world_room_session_id IN "+s);q.put("memory_embeddings","memory_id IN "+memories);
        for(String t:List.of("protected_memory_detail","memory_access_states"))q.put(t,"memory_id IN "+memories);
        q.put("memory_compression_evidence","memory_id IN "+memories+" AND evidence_memory_id IN "+memories);
        SELECTIONS=Collections.unmodifiableMap(q);
    }
    public static List<Table> readTables(JdbcTemplate jdbc,long id) {
        var result=new ArrayList<Table>();var owners=new TreeSet<Long>();
        for(var entry:SELECTIONS.entrySet()) {
            String query="SELECT * FROM "+entry.getKey()+" WHERE "+entry.getValue();
            int parameters=(int)query.chars().filter(c->c=='?').count();Object[] args=new Object[parameters];Arrays.fill(args,id);
            List<Map<String,Cell>> rows=jdbc.query(query,(rs,n)-> {
                var row=new TreeMap<String,Cell>();var md=rs.getMetaData();
                for(int i=1;i<=md.getColumnCount();i++) {
                    String name=md.getColumnName(i).toLowerCase(Locale.ROOT);int kind=md.getColumnType(i);
                    String value=rs.getString(i);
                    if(value!=null) {
                        if(kind==Types.BOOLEAN||kind==Types.BIT)value=Boolean.toString(rs.getBoolean(i));
                        else if(Set.of(Types.INTEGER,Types.BIGINT,Types.SMALLINT,Types.TINYINT,Types.NUMERIC,Types.DECIMAL).contains(kind))value=rs.getBigDecimal(i).stripTrailingZeros().toPlainString();
                        else if(kind==Types.TIMESTAMP)value=rs.getTimestamp(i).toLocalDateTime().toString();
                    }
                    row.put(name,new Cell(kind,value));
                }
                for(String key:List.of("owner_member_id","member_id"))if(row.containsKey(key)&&row.get(key).value()!=null)owners.add(Long.valueOf(row.get(key).value()));
                return row;
            },args);
            rows.sort(Comparator.comparing(x->new ObjectMapper().writeValueAsString(x)));
            result.add(new Table(entry.getKey(),rows));
        }
        var members=new ArrayList<Map<String,Cell>>();
        for(long owner:owners)members.add(Map.of("member_id",new Cell(Types.BIGINT,""+owner),"login_id",new Cell(Types.VARCHAR,"qa-"+owner),
                "password",new Cell(Types.VARCHAR,"!disabled"),"email",new Cell(Types.VARCHAR,"qa-"+owner+"@example.invalid"),
                "name",new Cell(Types.VARCHAR,"Isolated QA"),"role",new Cell(Types.VARCHAR,"USER")));
        result.addFirst(new Table("member",members));return result;
    }
    public static String digest(List<Table> tables) {
        try {return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(new ObjectMapper().writeValueAsBytes(tables)));}
        catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}
    }
    public String previewDigest() {
        // Presence heartbeats are not setting/history changes and must not invalidate a start form.
        var filtered=new ArrayList<Table>();
        for(var table:tables) {
            var rows=new ArrayList<Map<String,Cell>>();
            for(var row:table.rows()) {
                var copy=new TreeMap<>(row);
                if(table.name().equals("world_room_sessions"))for(String key:List.of("last_presence_at","last_progressed_at","progress_operation_id","progress_operation_until"))copy.remove(key);
                rows.add(copy);
            }
            filtered.add(new Table(table.name(),rows));
        }
        return digest(filtered);
    }
    public void restore(JdbcTemplate jdbc) {
        String url=jdbc.execute((Connection c)->c.getMetaData().getURL());
        if(url==null||!url.startsWith("jdbc:h2:")||!url.contains("pocket-qa-experience"))throw new IllegalStateException("Experience snapshot requires isolated H2");
        if(!digest.equals(digest(tables)))throw new IllegalArgumentException("Snapshot checksum mismatch");
        jdbc.execute((Connection connection)-> {
            try(var statement=connection.createStatement()) {
                for(var table:tables) {
                    if(!table.name().equals("member")&&!SELECTIONS.containsKey(table.name()))throw new IllegalArgumentException("Unknown snapshot table");
                    if(jdbc.queryForObject("SELECT COUNT(*) FROM "+table.name(),Long.class)!=0)throw new IllegalStateException("Restore only into a fresh empty database");
                }
                statement.execute("SET REFERENTIAL_INTEGRITY FALSE");
                try {
                    for(var table:tables) {
                        long max=0;
                        for(var row:table.rows()) {
                            for(String column:row.keySet())if(!column.matches("[a-z][a-z0-9_]*"))throw new IllegalArgumentException("Invalid snapshot column");
                            var columns=new ArrayList<>(row.keySet());
                            String sql="INSERT INTO "+table.name()+" ("+String.join(",",columns)+") VALUES ("+String.join(",",Collections.nCopies(columns.size(),"?"))+")";
                            try(var insert=connection.prepareStatement(sql)) {
                                for(int i=0;i<columns.size();i++) {
                                    var cell=row.get(columns.get(i));Object value=cell.value();
                                    if(value!=null&&cell.kind()==Types.TIMESTAMP)value=Timestamp.valueOf(java.time.LocalDateTime.parse(cell.value()));
                                    insert.setObject(i+1,value);
                                }
                                insert.executeUpdate();
                            }
                            var id=row.get(table.name().equals("member")?"member_id":"id");if(id!=null)max=Math.max(max,Long.parseLong(id.value()));
                        }
                        if(max>0)statement.execute("ALTER TABLE "+table.name()+" ALTER COLUMN "+(table.name().equals("member")?"member_id":"id")+" RESTART WITH "+(max+1));
                    }
                } finally {statement.execute("SET REFERENTIAL_INTEGRITY TRUE");}
                for(var table:tables)statement.execute("ALTER TABLE "+table.name()+" SET REFERENTIAL_INTEGRITY TRUE CHECK");
                // Verify every copied cell using the SOURCE type before any intentional isolation changes.
                for(var table:tables) {
                    var actual=jdbc.query("SELECT * FROM "+table.name(),(rs,n)-> {
                        var row=new TreeMap<String,Cell>();
                        var schema=table.rows().isEmpty()?Map.<String,Cell>of():table.rows().getFirst();
                        for(var entry:schema.entrySet()) {
                            String column=entry.getKey();int kind=entry.getValue().kind();String value=rs.getString(column);
                            if(value!=null) {
                                if(kind==Types.BOOLEAN||kind==Types.BIT)value=Boolean.toString(rs.getBoolean(column));
                                else if(Set.of(Types.INTEGER,Types.BIGINT,Types.SMALLINT,Types.TINYINT,Types.NUMERIC,Types.DECIMAL).contains(kind))value=rs.getBigDecimal(column).stripTrailingZeros().toPlainString();
                                else if(kind==Types.TIMESTAMP)value=rs.getTimestamp(column).toLocalDateTime().toString();
                            }
                            row.put(column,new Cell(kind,value));
                        }
                        return row;
                    });
                    var mapper=new ObjectMapper();
                    var expected=table.rows().stream().map(row->mapper.writeValueAsString(new TreeMap<>(row))).sorted().toList();
                    var restored=actual.stream().map(mapper::writeValueAsString).sorted().toList();
                    if(!expected.equals(restored))throw new IllegalStateException("Snapshot cell values differ: "+table.name());
                }
                // Source data never receives these isolation-only lease changes.
                jdbc.update("UPDATE world_room_sessions SET time_check_enabled=FALSE,progress_operation_id=NULL,progress_operation_until=NULL WHERE id=?",sessionId);
            }
            return null;
        });
    }
}
