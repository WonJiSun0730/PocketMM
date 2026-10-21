package com.example.projectmm.qa;
import org.springframework.jdbc.core.JdbcTemplate;
import com.example.projectmm.domain.dialogue.DialogueMessageContext;
import com.example.projectmm.domain.chat.ChatRole;
import java.util.*;

/** Player-visible clone data only. Does not invoke recall, compression, or change memory access state. */
final class ExperiencePlayerContext {
    record View(Map<String,Object> data,List<DialogueMessageContext> history) {}
    static View read(JdbcTemplate jdbc,long id) {
        var session=jdbc.queryForMap("SELECT * FROM world_room_sessions WHERE id=?",id);
        // JDBC maps are case insensitive, but serialize stable lower-case names for the simulator.
        var data=new LinkedHashMap<String,Object>();
        data.put("world",jdbc.queryForList("SELECT w.name,w.description,w.play_guide FROM worlds w JOIN world_conversations r ON r.world_id=w.id WHERE r.id=?",session.get("room_id")));
        data.put("lore",jdbc.queryForList("SELECT category,title,content FROM world_lore_entries WHERE world_id IN (SELECT world_id FROM world_conversations WHERE id=?) ORDER BY category,sort_order",session.get("room_id")));
        data.put("systems",jdbc.queryForList("SELECT w.name,w.change_condition,w.min_value,w.max_value,s.current_value FROM world_room_session_system_states s JOIN world_systems w ON w.id=s.world_system_id WHERE s.session_id=? ORDER BY w.sort_order",id));
        data.put("player",jdbc.queryForList("SELECT name,description FROM player_profiles WHERE id=?",session.get("player_profile_id")));
        for(String key:List.of("simulated_at","intro_title_snapshot","intro_content_snapshot","user_note","pocket_play_guide","no_control_player"))data.put(key,session.get(key));
        data.put("cast",jdbc.queryForList("SELECT c.name,s.active FROM world_room_session_cast s JOIN characters c ON c.id=s.character_id WHERE s.session_id=? ORDER BY s.id",id));
        data.put("playerState",jdbc.queryForList("SELECT location FROM world_room_player_states WHERE session_id=?",id));
        data.put("conditions",jdbc.queryForList("SELECT condition_text FROM world_room_player_conditions WHERE player_state_id IN (SELECT id FROM world_room_player_states WHERE session_id=?)",id));
        data.put("inventory",jdbc.queryForList("SELECT item_text FROM world_room_player_inventory WHERE player_state_id IN (SELECT id FROM world_room_player_states WHERE session_id=?)",id));
        var history=jdbc.query("SELECT role,content,created_at FROM world_room_messages WHERE session_id=? ORDER BY id ASC",(rs,n)->new DialogueMessageContext(ChatRole.valueOf(rs.getString(1)),rs.getString(2),rs.getTimestamp(3).toLocalDateTime()),id);
        return new View(data,history);
    }
}
