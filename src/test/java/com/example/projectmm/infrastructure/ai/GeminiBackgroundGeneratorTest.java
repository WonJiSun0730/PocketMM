package com.example.projectmm.infrastructure.ai;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.json.JsonMapper;
import java.net.http.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.io.*;
import java.util.Base64;
import java.util.concurrent.*;
import java.util.concurrent.Flow;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class GeminiBackgroundGeneratorTest {
    @Test void requestUsesImageModelRatioResolutionAndAReferenceForTheOverlay() throws Exception {
        var http=mock(HttpClient.class);
        HttpResponse<InputStream> response=mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        String encoded=Base64.getEncoder().encodeToString(new byte[]{4,5,6});
        when(response.body()).thenReturn(new ByteArrayInputStream(("{\"candidates\":[{\"content\":{\"parts\":[{\"thought\":true,\"inlineData\":{\"mimeType\":\"image/png\",\"data\":\"AQID\"}},{\"inlineData\":{\"mimeType\":\"image/png\",\"data\":\""+encoded+"\"}}]}}]}").getBytes(StandardCharsets.UTF_8)));
        when(http.send(any(HttpRequest.class),any(HttpResponse.BodyHandler.class))).thenReturn(response);
        var generator=new GeminiBackgroundGenerator("test-only-key","gemini-3.1-flash-lite-image",new JsonMapper(),http);
        assertThat(generator.generate("World: modern park",new byte[]{1,2},true)).containsExactly(4,5,6);
        var request=ArgumentCaptor.forClass(HttpRequest.class);verify(http).send(request.capture(),any(HttpResponse.BodyHandler.class));
        assertThat(request.getValue().uri().toString()).endsWith("gemini-3.1-flash-lite-image:generateContent");
        var out=new ByteArrayOutputStream();var complete=new CompletableFuture<Void>();
        request.getValue().bodyPublisher().orElseThrow().subscribe(new Flow.Subscriber<ByteBuffer>() {
            public void onSubscribe(Flow.Subscription subscription) { subscription.request(Long.MAX_VALUE); }
            public void onNext(ByteBuffer buffer) { byte[] bytes=new byte[buffer.remaining()];buffer.get(bytes);out.writeBytes(bytes); }
            public void onError(Throwable error) { complete.completeExceptionally(error); }
            public void onComplete() { complete.complete(null); }
        });complete.get(2,TimeUnit.SECONDS);
        var json=new JsonMapper().readTree(out.toByteArray());
        var config=json.path("generationConfig").path("responseFormat").path("image");
        assertThat(config.path("aspectRatio").asText()).isEqualTo("3:2");
        assertThat(config.path("imageSize").asText()).isEqualTo("1K");
        assertThat(json.path("contents").get(0).path("parts").get(0).path("text").asText()).contains("RGB(255,0,255)","behind the characters");
        assertThat(json.path("contents").get(0).path("parts").get(1).path("inlineData").path("data").asText()).isEqualTo("AQI=");
    }
    @Test void providerErrorsAndEmptyImageResponseFailWithoutAutomaticPaidRetries() throws Exception {
        var http=mock(HttpClient.class);HttpResponse<InputStream> response=mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(429);
        when(response.body()).thenAnswer(call->new ByteArrayInputStream("private-body".getBytes()));
        when(http.send(any(HttpRequest.class),any(HttpResponse.BodyHandler.class))).thenReturn(response);
        var generator=new GeminiBackgroundGenerator("test-only-key","gemini-3.1-flash-lite-image",new JsonMapper(),http);
        assertThatThrownBy(()->generator.generate("park",null,false)).hasMessageContaining("429").hasMessageNotContaining("private-body");
        verify(http,times(1)).send(any(HttpRequest.class),any(HttpResponse.BodyHandler.class));
        when(response.statusCode()).thenReturn(200);when(response.body()).thenReturn(new ByteArrayInputStream("{\"candidates\":[]}".getBytes()));
        assertThatThrownBy(()->generator.generate("park",null,false)).hasMessageContaining("No generated image");
        assertThat(new GeminiBackgroundGenerator("","model",new JsonMapper(),http).available()).isFalse();
    }
}
