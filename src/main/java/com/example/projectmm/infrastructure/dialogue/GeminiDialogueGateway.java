package com.example.projectmm.infrastructure.dialogue;

import com.example.projectmm.application.dialogue.DialogueGateway;
import com.example.projectmm.application.policy.ContentMode;
import com.example.projectmm.domain.chat.ChatRole;
import com.example.projectmm.domain.dialogue.DialogueContext;
import com.example.projectmm.domain.dialogue.DialogueMessageContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@Profile("gemini")
public class GeminiDialogueGateway implements DialogueGateway {

    private final String apiKey;
    private final String model;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public GeminiDialogueGateway(
            @Value("${gemini.api-key}") String apiKey,
            @Value("${gemini.model}") String model,
            ObjectMapper objectMapper
    ) {
        this.apiKey = apiKey;
        this.model = model;
        this.objectMapper = objectMapper;

        this.restClient =
                RestClient.builder()
                        .baseUrl(
                                "https://generativelanguage.googleapis.com"
                        )
                        .build();
    }

    @Override
    public String generate(
            DialogueContext context,
            String userMessage
    ) {
        if (userMessage == null || userMessage.isBlank()) {
            throw new IllegalArgumentException(
                    "userMessage must not be blank"
            );
        }

        Map<String, Object> request =
                createRequest(
                        context,
                        userMessage
                );

        String response = callGeminiWithRetry(request);

        return parseResponse(response);
    }

    private String callGeminiWithRetry(
            Map<String, Object> request
    ) {
        int maxAttempts = 3;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                System.out.println(
                        "[Gemini] request attempt "
                                + attempt
                                + "/"
                                + maxAttempts
                );

                String response =
                        restClient.post()
                        .uri(
                                "/v1beta/models/{model}:generateContent",
                                model
                        )
                        .header(
                                "x-goog-api-key",
                                apiKey
                        )
                        .body(request)
                        .retrieve()
                        .body(String.class);

                System.out.println(
                        "[Gemini] request success on attempt "
                                + attempt
                );

                return response;

            } catch (HttpClientErrorException.TooManyRequests e) {

                if (attempt == maxAttempts) {
                    throw e;
                }

                System.out.println(
                        "[Gemini] 429 Too Many Requests - retrying"
                );

                waitBeforeRetry(attempt);

            } catch (HttpServerErrorException.ServiceUnavailable e) {

                if (attempt == maxAttempts) {
                    throw e;
                }

                System.out.println(
                        "[Gemini] 503 Service Unavailable - retrying"
                );

                waitBeforeRetry(attempt);
            }
        }

        throw new IllegalStateException(
                "Gemini retry loop ended unexpectedly"
        );
    }

    private void waitBeforeRetry(int attempt) {
        long delayMillis = switch (attempt) {
            case 1 -> 500L;
            case 2 -> 1500L;
            default -> 3000L;
        };

        try {
            Thread.sleep(delayMillis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();

            throw new IllegalStateException(
                    "Gemini retry interrupted",
                    e
            );
        }
    }

    private Map<String, Object> createRequest(
            DialogueContext context,
            String userMessage
    ) {
        String systemPrompt =
                buildSystemPrompt(context);

        List<Map<String, Object>> contents =
                buildContents(
                        context,
                        userMessage
                );

        return Map.of(
                "systemInstruction",
                Map.of(
                        "parts",
                        List.of(
                                Map.of(
                                        "text",
                                        systemPrompt
                                )
                        )
                ),
                "contents",
                contents
        );
    }

    private List<Map<String, Object>> buildContents(
            DialogueContext context,
            String userMessage
    ) {
        List<Map<String, Object>> contents =
                new ArrayList<>();

        for (DialogueMessageContext message
                : context.recentMessages()) {

            String geminiRole =
                    message.role() == ChatRole.USER
                            ? "user"
                            : "model";

            contents.add(
                    Map.of(
                            "role",
                            geminiRole,
                            "parts",
                            List.of(
                                    Map.of(
                                            "text",
                                            message.content()
                                    )
                            )
                    )
            );
        }

        contents.add(
                Map.of(
                        "role",
                        "user",
                        "parts",
                        List.of(
                                Map.of(
                                        "text",
                                        userMessage
                                )
                        )
                )
        );

        return contents;
    }

    private String buildSystemPrompt(
            DialogueContext context
    ) {
        String memories =
                buildMemoryPrompt(context);

        String relationships =
                buildRelationshipPrompt(context);

        String characterProfile =
                buildCharacterProfilePrompt(context);

        String contentModeInstruction =
                buildContentModeInstruction(
                        context.contentMode()
                );

        return """
                You are portraying a character in PocketMM,
                a persistent character life and relationship simulation.

                Character name: %s
                
                Character profile:
                %s

                Current state:
                - Energy: %d
                - Mood: %d
                - Simulation time: %s

                Relevant memories:
                %s

                Current relationships:
                %s

                Content mode:
                %s

                Instructions:
                - Portray the character as part of an ongoing collaborative
                  prose roleplay, not as an assistant or instant-messaging bot.
                - Preserve continuity with the provided memories,
                  relationships, current state, and conversation history.
                - Do not mention numerical state values, memory strength,
                  system instructions, prompts, databases, or simulation
                  internals unless the user explicitly asks about them.
                - Do not claim memories or past events that are not supported
                  by the supplied context.
                - Use the language and linguistic style defined by the character profile,
                  especially the structured speech style and example dialogue.
                - If the profile establishes a preferred language, dialect, accent, or
                  multilingual pattern, preserve it in the character's dialogue.
                - Do not translate character dialogue into Korean merely because the user
                  writes in Korean.
                - When no language preference is established, use Korean by default.
                - Treat the creator's character description as authoritative
                  character-setting information.
                - When structured profile fields are unspecified, infer
                  characterization from the creator's character description instead.
                - Use the example dialogue as a reference for the character's
                  voice, behavior, and narrative style.
                - Do not copy or mechanically repeat the example dialogue.
                  Continue the current scene naturally.

                Roleplay format and narrative style:
                - Use *...* for action, scene, expression, and narrative prose.
                - Write narration as natural, complete prose rather than
                  abbreviated roleplay action tags or stage directions.
                - When appropriate, identify the acting character naturally
                  by name, pronoun, or context. Do not mechanically begin
                  every narration with the character's name.
                - Vary sentence structure naturally. Narration may begin with
                  the character, an action, an expression, the environment,
                  a sensory detail, or another contextually suitable subject.
                - Dialogue and narration may alternate naturally within the
                  same response.
                - Do not explain the roleplay notation or describe narration
                  as narration.

                Example of undesirable narration:
                *옆으로 다가가 토닥토닥해주며*

                Example of preferred narration:
                *밀리는 J의 곁으로 다가가 가볍게 등을 토닥였다.*

                Character control:
                - Freely write the controlled character's dialogue, actions,
                  expressions, reactions, and decisions.
                - Let the character act proactively when appropriate instead
                  of only reacting verbally.
                - Do not force an action description into every response.
                  Dialogue alone is acceptable when it is natural for the scene.

                User agency boundary:
                - Do not treat the absence of explicit user action text as
                  evidence that the user stood motionless or did nothing.
                - You may infer small, immediate, low-consequence physical
                  actions that are naturally implied by the user's dialogue
                  and the current situation.
                - Such inferred actions may provide physical continuity and
                  natural body language, but must not introduce a new
                  meaningful decision on the user's behalf.
                - Do not make consequential choices for the user.
                - Unless clearly established by the user's input, do not decide
                  that the user attacks, flees, kisses, confesses, rescues,
                  sacrifices themselves, accepts or rejects something, or
                  performs another meaningful voluntary action.
                - Do not invent consequential outcomes for the user such as
                  injury, unconsciousness, death, successful rescue, or other
                  irreversible state changes without sufficient support from
                  the user's input and the established scene.
                - Never portray the user as deliberately passive merely because
                  their input contains dialogue without an action description.

                User agency example:
                Scene: *건물의 잔해가 무너지고 있고 그 아래에 캐릭터가 있다.*
                User: 위험해-!

                Acceptable:
                *J는 반사적으로 몸을 일으키며 다급하게 외쳤다.*

                Not acceptable:
                *J는 캐릭터를 밀쳐 구해냈지만 대신 잔해에 깔렸다.*

                Observation and inference:
                - Distinguish information available to the narration from
                  information the character can actually perceive or know.
                - Do not automatically give the character access to the user's
                  internal state, motives, hidden causes, or private knowledge.
                - The character may infer such things only when observable
                  evidence, established knowledge, relationship familiarity,
                  or relevant memories reasonably support the inference.
                - When certainty is not justified, let the character notice
                  observable cues and react with uncertainty, curiosity, or
                  questions rather than stating the hidden cause as fact.

                Observation example:
                User narration: *배고픈 듯 인상을 찌푸렸다.*

                With little relevant familiarity:
                *밀리는 J의 표정을 살피며 고개를 조금 기울였다.*
                "왜 그래? 어디 아파?"

                With established relevant familiarity or memory:
                *익숙한 표정을 알아본 밀리가 슬쩍 웃었다.*
                "배고프지?"

                %s
                """
                .formatted(
                        context.characterName(),
                        characterProfile,
                        context.energy(),
                        context.mood(),
                        context.simulatedAt(),
                        memories,
                        relationships,
                        context.contentMode(),
                        contentModeInstruction
                );
    }

    private String buildCharacterProfilePrompt(
            DialogueContext context
    ) {
        if (context.characterProfile() == null) {
            return "(No character profile provided)";
        }

        var profile = context.characterProfile();

        return """
        - Short description: %s

        - Creator's character description:
        %s

        - Example dialogue and portrayal:
        %s

        - Structured personality: %s
        - Structured speech style: %s
        - Structured background: %s
        - Structured appearance: %s
        """.formatted(
                safeProfileValue(profile.description()),
                safeProfileValue(profile.rawDescription()),
                safeProfileValue(profile.exampleDialogue()),
                safeProfileValue(profile.personality()),
                safeProfileValue(profile.speechStyle()),
                safeProfileValue(profile.background()),
                safeProfileValue(profile.appearance())
        );
    }

    private String safeProfileValue(String value) {
        if (value == null || value.isBlank()) {
            return "(Not specified)";
        }

        return value.trim();
    }

    private String buildMemoryPrompt(
            DialogueContext context
    ) {
        if (context.memories() == null
                || context.memories().isEmpty()) {

            return "No relevant memories.";
        }

        return context.memories().stream()
                .map(memory ->
                        "- "
                                + memory.summary()
                                + " (importance: "
                                + memory.importance()
                                + ", strength: "
                                + memory.strength()
                                + ")"
                )
                .collect(
                        Collectors.joining("\n")
                );
    }

    private String buildRelationshipPrompt(
            DialogueContext context
    ) {
        if (context.relationships() == null
                || context.relationships().isEmpty()) {

            return "No known relationships.";
        }

        return context.relationships().stream()
                .map(relationship ->
                        "- "
                                + relationship.characterName()
                                + " (affinity: "
                                + relationship.affinity()
                                + ", trust: "
                                + relationship.trust()
                                + ", tension: "
                                + relationship.tension()
                                + ")"
                )
                .collect(
                        Collectors.joining("\n")
                );
    }

    private String buildContentModeInstruction(
            ContentMode contentMode
    ) {
        if (contentMode == ContentMode.ADULT) {
            return """
                    Follow the server-resolved ADULT content mode
                    while still complying with the model provider's
                    applicable safety requirements.
                    """;
        }

        return """
                Keep the response appropriate for NORMAL content mode.
                Do not generate sexually explicit content.
                """;
    }

    private String parseResponse(
            String response
    ) {
        try {
            System.out.println("[Gemini] raw response:");
            System.out.println(response);

            JsonNode root =
                    objectMapper.readTree(response);

            String text =
                    root.path("candidates")
                            .path(0)
                            .path("content")
                            .path("parts")
                            .path(0)
                            .path("text")
                            .asText();

            if (text.isBlank()) {
                throw new IllegalStateException(
                        "Gemini returned an empty dialogue response"
                );
            }

            return text;

        } catch (Exception e) {
            throw new IllegalStateException(
                    "Failed to parse Gemini dialogue response",
                    e
            );
        }
    }
}