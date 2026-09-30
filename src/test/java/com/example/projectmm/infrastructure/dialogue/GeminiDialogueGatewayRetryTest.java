package com.example.projectmm.infrastructure.dialogue;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.times;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

class GeminiDialogueGatewayRetryTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void transient_429는_최대_3회까지_재시도한다() {
        Fixture fixture = fixture();

        fixture.server.expect(
                        times(3),
                        method(HttpMethod.POST)
                )
                .andRespond(
                        withStatus(HttpStatus.TOO_MANY_REQUESTS)
                                .contentType(MediaType.APPLICATION_JSON)
                                .body("""
                                        {
                                          "error": {
                                            "message": "temporary rate limit"
                                          }
                                        }
                                        """)
                );

        assertThatThrownBy(
                () -> invokeCallGeminiWithRetry(fixture.gateway)
        ).isInstanceOf(HttpClientErrorException.TooManyRequests.class);

        fixture.server.verify();
    }

    @Test
    void service_unavailable은_최대_3회까지_재시도한다() {
        Fixture fixture = fixture();

        fixture.server.expect(
                        times(3),
                        method(HttpMethod.POST)
                )
                .andRespond(
                        withStatus(HttpStatus.SERVICE_UNAVAILABLE)
                                .contentType(MediaType.APPLICATION_JSON)
                                .body("""
                                        {
                                          "error": {
                                            "message": "temporarily unavailable"
                                          }
                                        }
                                        """)
                );

        assertThatThrownBy(
                () -> invokeCallGeminiWithRetry(fixture.gateway)
        ).isInstanceOf(
                HttpServerErrorException.ServiceUnavailable.class
        );

        fixture.server.verify();
    }

    @Test
    void daily_quota_429는_재시도하지_않는다() {
        Fixture fixture = fixture();

        fixture.server.expect(
                        once(),
                        method(HttpMethod.POST)
                )
                .andRespond(
                        withStatus(HttpStatus.TOO_MANY_REQUESTS)
                                .contentType(MediaType.APPLICATION_JSON)
                                .body("""
                                        {
                                          "error": {
                                            "message":
                                            "GenerateRequestsPerDayPerProjectPerModel"
                                          }
                                        }
                                        """)
                );

        assertThatThrownBy(
                () -> invokeCallGeminiWithRetry(fixture.gateway)
        ).isInstanceOf(HttpClientErrorException.TooManyRequests.class);

        fixture.server.verify();
    }

    private Fixture fixture() {
        RestClient.Builder builder = RestClient.builder()
                .baseUrl(
                        "https://generativelanguage.googleapis.com"
                );

        MockRestServiceServer server =
                MockRestServiceServer.bindTo(builder).build();

        GeminiDialogueGateway gateway =
                new GeminiDialogueGateway(
                        "test-api-key",
                        "test-model",
                        objectMapper,
                        builder.build()
                );

        return new Fixture(gateway, server);
    }

    private Object invokeCallGeminiWithRetry(
            GeminiDialogueGateway gateway
    ) {
        try {
            Method method =
                    GeminiDialogueGateway.class.getDeclaredMethod(
                            "callGeminiWithRetry",
                            Map.class
                    );

            method.setAccessible(true);

            return method.invoke(
                    gateway,
                    Map.of(
                            "contents",
                            java.util.List.of()
                    )
            );

        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();

            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }

            throw new RuntimeException(cause);

        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    private record Fixture(
            GeminiDialogueGateway gateway,
            MockRestServiceServer server
    ) {
    }
}