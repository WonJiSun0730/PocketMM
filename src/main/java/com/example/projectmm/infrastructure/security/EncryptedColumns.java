package com.example.projectmm.infrastructure.security;

import java.util.List;

/** Single allowlist shared by migration and coverage tests. QA and error diagnostics are excluded. */
public final class EncryptedColumns {
    public record Table(String name, List<String> keys, List<String> columns) {}
    private static Table table(String name, String keys, String columns) {
        return new Table(name,List.of(keys.split(",")),List.of(columns.split(",")));
    }
    public static final List<Table> TABLES = List.of(
        table("chat_messages","id","content"),
        table("world_room_messages","id","content"),
        table("world_room_message_variants","id","content,before_player_state_json,after_player_state_json,user_scene_setup_json,before_scene_facts_json,after_scene_facts_json"),
        table("memories","id","summary,evidence_quote,fact_subject,fact_relation,fact_value,semantic_subject,semantic_relation,semantic_object"),
        table("protected_memory_detail","id","detail_text"),
        table("memory_embeddings","memory_id","embedding"),
        table("pocket_activity_reports","id","activities_json,narrative_context_json,narrative_text"),
        table("world_room_sessions","id","intro_title_snapshot,intro_content_snapshot,initial_location,user_note,pocket_play_guide"),
        table("world_room_session_initial_conditions","session_id,sort_order","condition_text"),
        table("world_room_session_initial_inventory","session_id,sort_order","item_text"),
        table("world_room_player_states","id","location"),
        table("world_room_player_conditions","player_state_id,sort_order","condition_text"),
        table("world_room_player_inventory","player_state_id,sort_order","item_text")
    );
    private EncryptedColumns() {}
}
