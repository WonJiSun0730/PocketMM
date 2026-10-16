package com.example.projectmm.infrastructure.dialogue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.client.MockClientHttpResponse;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.net.URLClassLoader;
import java.util.*;
import java.util.jar.*;
import static org.assertj.core.api.Assertions.*;

class NarratorIntegrationTest {
    final ObjectMapper mapper = new ObjectMapper();
    @TempDir Path temp;

    @Test void bothProvidersSendOneIdenticalNarratorWithRealBuilderAndNoAdditionalCalls() {
        var geminiRequests = new ArrayList<JsonNode>();
        var openAiRequests = new ArrayList<JsonNode>();
        String payload = mapper.writeValueAsString(Map.of("reply", "테스트 응답", "elapsedSeconds", 0,
                "enterCharacterIds", List.of(), "leaveCharacterIds", List.of(), "systemChanges", List.of()));
        String geminiResponse = mapper.writeValueAsString(Map.of("candidates", List.of(Map.of("finishReason", "STOP", "content",
                Map.of("parts", List.of(Map.of("text", payload)))))));
        String openAiResponse = mapper.writeValueAsString(Map.of("output", List.of(Map.of("type", "message",
                "content", List.of(Map.of("type", "output_text", "text", payload))))));
        var gemini = new GeminiDialogueGateway("never-sent", "fixture", mapper, client(geminiRequests, geminiResponse));
        var openai = new OpenAiDialogueGateway("never-sent", "gpt-5.6-luna", mapper, gemini, new AiUsageTracker());
        ReflectionTestUtils.setField(openai, "restClient", client(openAiRequests, openAiResponse));
        for (boolean strict : new boolean[]{false, true}) {
            var context = PromptMigrationEquivalenceTest.world(strict);
            assertThat(gemini.generateWorldRoomTurn(context, "hello").reply()).isEqualTo("테스트 응답");
            assertThat(openai.generateWorldRoomTurn(context, "hello").reply()).isEqualTo("테스트 응답");
            var parts = gemini.buildWorldRoomPromptParts(context);
            assertThat(parts.stable()).containsOnlyOnce(PromptModules.narrator())
                    .contains("RAW_IDENTITY", "SOUL_PERSONALITY", "VOICE_EXAMPLE", "WORLD_FACT");
            assertThat(parts.dynamic()).doesNotContain("[NARRATOR:")
                    .contains("MEMORY_SUMMARY", "MEMORY_EVIDENCE", "STORED_LOCATION");
            var g = geminiRequests.getLast();
            var o = openAiRequests.getLast();
            String gText = g.path("systemInstruction").path("parts").path(0).path("text").asText();
            String oText = o.path("input").path(0).path("content").path(0).path("text").asText();
            assertThat(gText).containsOnlyOnce(PromptModules.narrator());
            assertThat(oText).containsOnlyOnce(PromptModules.narrator()).startsWith(parts.stable());
            assertThat(o.path("input").path(1).path("content").asText()).isEqualTo(parts.dynamic());
            assertThat(o.path("input").path(0).path("content").path(0)
                    .path("prompt_cache_breakpoint").path("mode").asText()).isEqualTo("explicit");
            assertThat(o.path("text").path("format").path("strict").asBoolean()).isTrue();
            assertThat(o.path("text").path("format").path("schema").path("properties").has("elapsedSeconds")).isTrue();
            assertThat(gText).contains("Return JSON only:", "\"elapsedSeconds\": 0", "Player-state delta rules:");
        }
        assertThat(geminiRequests).hasSize(2);
        assertThat(openAiRequests).hasSize(2);
        assertThat(PromptModules.narrator()).contains("플레이어 통제 설정", "IDENTITY·SOUL", "우선한다");
        assertThat(PromptMigrationEquivalenceTest.prompts().get("legacy")).containsOnlyOnce(PromptModules.narrator());
    }

    private RestClient client(List<JsonNode> requests, String response) {
        return RestClient.builder().baseUrl("https://never-contact.invalid").requestInterceptor((request, body, execution) -> {
            requests.add(mapper.readTree(new String(body, StandardCharsets.UTF_8)));
            var result = new MockClientHttpResponse(response.getBytes(StandardCharsets.UTF_8), HttpStatus.OK);
            result.getHeaders().setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
            return result; // Deliberately never execute the network transport.
        }).build();
    }

    @Test void allModulesLoadFromPackagedJarWithoutFilesystemFallback() throws Exception {
        Path jar = temp.resolve("prompts.jar");
        try (var out = new JarOutputStream(Files.newOutputStream(jar))) {
            for (String name : List.of("SYSTEM", "SCENE", "NARRATOR")) {
                String path = "prompts/" + name + ".md";
                out.putNextEntry(new JarEntry(path));
                try (var input = getClass().getClassLoader().getResourceAsStream(path)) {
                    assertThat(input).isNotNull();
                    input.transferTo(out);
                }
                out.closeEntry();
            }
        }
        try (var loader = new URLClassLoader(new java.net.URL[]{jar.toUri().toURL()}, null)) {
            var packaged = new PromptModules(loader);
            assertThat(packaged.required("NARRATOR", "narrator.expression"))
                    .isEqualTo(PromptModules.section("NARRATOR", "narrator.expression"));
        }
    }
}
