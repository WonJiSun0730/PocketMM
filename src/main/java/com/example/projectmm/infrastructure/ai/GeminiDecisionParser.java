package com.example.projectmm.infrastructure.ai;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

public class GeminiDecisionParser {

    private final ObjectMapper objectMapper;

    public GeminiDecisionParser(
            ObjectMapper objectMapper
    ) {
        this.objectMapper = objectMapper;
    }

    public GeminiDecisionResponse parse(
            String response
    ) {
        if (response == null || response.isBlank()) {
            throw new GeminiDecisionParseException(
                    GeminiDecisionParseException.Reason.EMPTY_RESPONSE,
                    "Gemini returned an empty response"
            );
        }

        JsonNode root;

        try {
            root = objectMapper.readTree(response);
        } catch (Exception e) {
            throw new GeminiDecisionParseException(
                    GeminiDecisionParseException.Reason.MALFORMED_JSON,
                    "Failed to parse Gemini response envelope",
                    e
            );
        }

        JsonNode textNode =
                root.path("candidates")
                        .path(0)
                        .path("content")
                        .path("parts")
                        .path(0)
                        .path("text");

        if (!textNode.isString()
                || textNode.asText().isBlank()) {

            throw new GeminiDecisionParseException(
                    GeminiDecisionParseException.Reason.MISSING_CONTENT,
                    "Gemini response does not contain decision content"
            );
        }

        String json =
                textNode.asText();

        JsonNode decisionNode;

        try {
            decisionNode =
                    objectMapper.readTree(json);
        } catch (Exception e) {
            throw new GeminiDecisionParseException(
                    GeminiDecisionParseException.Reason.MALFORMED_JSON,
                    "Gemini decision content is malformed JSON",
                    e
            );
        }

        if (decisionNode == null
                || !decisionNode.isObject()) {

            throw new GeminiDecisionParseException(
                    GeminiDecisionParseException.Reason.INVALID_STRUCTURE,
                    "Gemini decision content must be a JSON object"
            );
        }

        try {
            return objectMapper.treeToValue(
                    decisionNode,
                    GeminiDecisionResponse.class
            );
        } catch (Exception e) {
            throw new GeminiDecisionParseException(
                    GeminiDecisionParseException.Reason.INVALID_STRUCTURE,
                    "Gemini decision content has an invalid structure",
                    e
            );
        }
    }
}