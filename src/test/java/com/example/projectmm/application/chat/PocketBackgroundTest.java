package com.example.projectmm.application.chat;

import com.example.projectmm.infrastructure.ai.GeminiBackgroundGenerator;
import com.example.projectmm.infrastructure.scene.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.io.ByteArrayOutputStream;
import java.nio.file.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class PocketBackgroundTest {
    @TempDir Path directory;
    static byte[] image(boolean magenta, boolean alpha) throws Exception {
        var image = new BufferedImage(240, 160, BufferedImage.TYPE_INT_ARGB);
        for (int y=0;y<160;y++) for(int x=0;x<240;x++)
            image.setRGB(x,y,x<60 ? 0xff6d8059 : alpha ? 0 : magenta ? 0xffff00ff : 0xffded4bc);
        var out = new ByteArrayOutputStream(); ImageIO.write(image,"png",out); return out.toByteArray();
    }
    @Test void placeKeywordsReuseAliasesWithoutCrossingIncompatibleScenes() {
        assertThat(PocketBackgroundMatcher.score("심리상담센터 상담실", "상담실", "")).isPositive();
        assertThat(PocketBackgroundMatcher.score("counseling room", "상담실", "")).isPositive();
        assertThat(PocketBackgroundMatcher.score("집 침실", "상담실", "")).isZero();
        assertThat(PocketBackgroundMatcher.score("집으로 돌아가는 길", "집", "")).isZero();
        assertThat(PocketBackgroundMatcher.score("야외 카페", "실내 카페", "")).isZero();
        assertThat(PocketBackgroundMatcher.score("비밀기지", "제2시설", "비밀기지, 안전가옥")).isPositive();
        assertThat(PocketBackgroundMatcher.score("비밀기지 내부", "제2시설", "비밀기지, 안전가옥")).isPositive();
        assertThat(PocketBackgroundMatcher.score("hq lobby", "제2시설", "hq")).isPositive();
        assertThat(PocketBackgroundMatcher.score("hqfoo", "제2시설", "hq")).isZero();
        assertThat(PocketBackgroundMatcher.score("공원", "사무실", "공원처럼 넓은")).isZero();
    }
    @Test void missingImageConnectionAndProviderRejectionAreReportedSpecifically() throws Exception {
        var generator=mock(GeminiBackgroundGenerator.class);
        var service=new PocketBackgroundService(mock(WorldRoomSessionService.class),new PocketBackgroundStore(directory.toString(),new JsonMapper()),generator,true);
        var context=new PocketBackgroundService.Context(9,"1-2","World");
        try {
            assertThat(service.resolve(context,"공원").error()).contains("Gemini 연결 설정");
            when(generator.available()).thenReturn(true);
            when(generator.generate(anyString(),isNull(),eq(false))).thenThrow(new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_GATEWAY,"이미지 생성 사용량 한도나 요청 제한에 걸렸어요. (Gemini 429)"));
            service.request(context,"공원");
            for(int i=0;i<100 && service.resolve(context,"공원").status().equals("GENERATING");i++) Thread.sleep(20);
            assertThat(service.resolve(context,"공원").error()).contains("Gemini 429");
            assertThat(service.resolve(context,"공원").asset()).isNull();
        } finally { service.close(); }
    }
    @Test void alphaOverlayClearsChromaAndHolesAndRejectsOpaqueOrEmptyScenery() throws Exception {
        byte[] prepared = PocketBackgroundImages.prepare(image(true,false),true,true);
        var png = ImageIO.read(new java.io.ByteArrayInputStream(prepared));
        assertThat(png.getWidth()).isEqualTo(240); assertThat(png.getHeight()).isEqualTo(160);
        assertThat(png.getRGB(100,100) >>> 24).isZero(); assertThat(png.getRGB(10,10) >>> 24).isEqualTo(255);
        assertThatThrownBy(() -> PocketBackgroundImages.prepare(image(false,false),true,false)).hasMessageContaining("투명");
        assertThat(PocketBackgroundImages.prepare(image(false,true),true,false)).isNotEmpty();
        assertThatThrownBy(() -> PocketBackgroundImages.prepare(new byte[]{1,2,3},false,false)).hasMessageContaining("이미지");
    }
    @Test void generatedDarkPurpleSpillIsRemovedWithoutChangingUploadedArt() throws Exception {
        var source=ImageIO.read(new java.io.ByteArrayInputStream(image(true,false)));
        source.setRGB(59,80,0xff713c79);
        var out=new java.io.ByteArrayOutputStream();ImageIO.write(source,"png",out);
        var generated=ImageIO.read(new java.io.ByteArrayInputStream(PocketBackgroundImages.prepare(out.toByteArray(),true,true)));
        assertThat(generated.getRGB(59,80) >>> 24).isZero();
        // Uploaded alpha artwork is not chroma-keyed, including deliberate purple objects.
        source=ImageIO.read(new java.io.ByteArrayInputStream(image(false,true)));
        source.setRGB(59,80,0xff713c79);out.reset();ImageIO.write(source,"png",out);
        var uploaded=ImageIO.read(new java.io.ByteArrayInputStream(PocketBackgroundImages.prepare(out.toByteArray(),true,false)));
        assertThat(uploaded.getRGB(59,80)).isEqualTo(0xff713c79);
    }
    @Test void storedImagesSurviveRestartAreScopedAndExactUploadsWin() throws Exception {
        var store = new PocketBackgroundStore(directory.toString(),new JsonMapper());
        String a="a".repeat(32), b="b".repeat(32);
        store.stageBackground("1-2",a,image(false,false)); store.complete("1-2",a,"상담실","","AI",image(false,true));
        store.stageBackground("1-2",b,image(false,false)); store.complete("1-2",b,"상담실","","UPLOAD",image(false,true));
        store.configure("1-2",true);
        var restarted = new PocketBackgroundStore(directory.toString(),new JsonMapper());
        assertThat(restarted.match("1-2","심리상담센터 상담실").orElseThrow().id()).isEqualTo(b);
        assertThat(restarted.catalog("1-2").automatic()).isTrue();
        assertThat(restarted.match("1-3","상담실")).isEmpty();
        assertThatThrownBy(()->restarted.file("1-2","../escape","background")).isInstanceOf(IllegalArgumentException.class);
        Files.delete(restarted.file("1-2",b,"scenery"));
        assertThat(restarted.match("1-2","상담실").orElseThrow().id()).isEqualTo(a);
    }
    @Test void sceneStoryTimeSeparatesMorningDayNightRegardlessOfPlaceNameOrCreationTime() throws Exception {
        var store=new PocketBackgroundStore(directory.toString(),new JsonMapper());
        String night="1".repeat(32), day="2".repeat(32), morning="3".repeat(32);
        for(String id:java.util.List.of(night,day,morning)) store.stageBackground("1-2",id,image(false,false));
        store.complete("1-2",night,"공원","","AI",image(false,true),"2026-10-13T22:00:00");
        store.complete("1-2",day,"공원","","AI",image(false,true),"2026-10-13T14:00:00");
        store.complete("1-2",morning,"공원","","AI",image(false,true),"2026-10-14T08:00:00");
        assertThat(store.match("1-2","공원","night").orElseThrow().id()).isEqualTo(night);
        assertThat(store.match("1-2","공원","day").orElseThrow().id()).isEqualTo(day);
        assertThat(store.match("1-2","공원","morning").orElseThrow().id()).isEqualTo(morning);
        assertThat(PocketBackgroundStore.daypart("2026-10-13T06:00:00")).isEqualTo("morning");
        assertThat(PocketBackgroundStore.daypart("2026-10-13T10:00:00")).isEqualTo("day");
        assertThat(PocketBackgroundStore.daypart("2026-10-13T20:00:00")).isEqualTo("night");
        var restarted=new PocketBackgroundStore(directory.toString(),new JsonMapper());
        assertThat(restarted.match("1-2","공원","day").orElseThrow().simulatedAt()).contains("14:00");
    }
    @Test void uploadedPairIsStoredWithoutAnAiCallAndInvalidSettingsDoNotWrite() throws Exception {
        var generator=mock(GeminiBackgroundGenerator.class);
        var store=new PocketBackgroundStore(directory.toString(),new JsonMapper());
        var service=new PocketBackgroundService(mock(WorldRoomSessionService.class),store,generator,true);
        var context=new PocketBackgroundService.Context(9,"1-2","World");
        try {
            assertThatThrownBy(()->service.configure(context,true," ")).hasMessageContaining("장소");
            assertThat(store.catalog("1-2").automatic()).isTrue();
            var result=service.upload(context,"제2시설","비밀기지",image(false,false),image(false,true));
            assertThat(result.asset().source()).isEqualTo("UPLOAD");
            assertThat(service.resolve(context,"비밀기지").reused()).isTrue();
            assertThat(service.image(context,result.asset().id(),"scenery")).exists();
            assertThatThrownBy(()->service.image(new PocketBackgroundService.Context(10,"1-3","World"),result.asset().id(),"background"))
                    .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
            verify(generator,never()).generate(anyString(),nullable(byte[].class),anyBoolean());
        } finally { service.close(); }
    }
    @Test void concurrentSimilarRequestsShareTwoCallsAndCachedScenesNeverGenerateAgain() throws Exception {
        var generator=mock(GeminiBackgroundGenerator.class); when(generator.available()).thenReturn(true);
        var entered=new CountDownLatch(1); var release=new CountDownLatch(1);
        when(generator.generate(anyString(),isNull(),eq(false))).thenAnswer(call->{entered.countDown(); release.await(5,TimeUnit.SECONDS); return image(false,false);});
        when(generator.generate(anyString(),notNull(),eq(true))).thenReturn(image(true,false));
        var service=new PocketBackgroundService(mock(WorldRoomSessionService.class),new PocketBackgroundStore(directory.toString(),new JsonMapper()),generator,true);
        var context=new PocketBackgroundService.Context(9,"1-2","World");
        try {
            assertThat(service.request(context,"상담실").status()).isEqualTo("GENERATING");
            assertThat(entered.await(5,TimeUnit.SECONDS)).isTrue();
            assertThat(service.request(context,"심리상담센터 상담실").status()).isEqualTo("GENERATING");
            // Disabling automatic generation is independent from the active filesystem generation lock.
            assertThat(service.configure(context,false,"상담실").automatic()).isFalse();
            release.countDown();
            awaitReady(service,context,"상담실");
            assertThat(service.request(context,"심리상담센터 상담실").reused()).isTrue();
            verify(generator,times(2)).generate(anyString(),nullable(byte[].class),anyBoolean());
        } finally { release.countDown(); service.close(); }
    }
    @Test void failedSceneryKeepsBackgroundForRetryAndNeverPublishesPartialAsset() throws Exception {
        var generator=mock(GeminiBackgroundGenerator.class); when(generator.available()).thenReturn(true);
        when(generator.generate(anyString(),isNull(),eq(false))).thenReturn(image(false,false));
        when(generator.generate(anyString(),notNull(),eq(true))).thenThrow(new IllegalStateException("SECRET KEY BODY")).thenReturn(image(true,false));
        var service=new PocketBackgroundService(mock(WorldRoomSessionService.class),new PocketBackgroundStore(directory.toString(),new JsonMapper()),generator,true);
        var context=new PocketBackgroundService.Context(9,"1-2","World");
        try {
            service.request(context,"집");
            for(int i=0;i<100 && service.resolve(context,"집").status().equals("GENERATING");i++) Thread.sleep(20);
            assertThat(service.resolve(context,"집").status()).isEqualTo("FAILED");
            assertThat(service.resolve(context,"집").asset()).isNull();
            assertThat(service.resolve(context,"집").error()).doesNotContain("SECRET");
            service.request(context,"집"); awaitReady(service,context,"집");
            verify(generator,times(1)).generate(anyString(),isNull(),eq(false));
            verify(generator,times(2)).generate(anyString(),notNull(),eq(true));
        } finally { service.close(); }
    }
    static void awaitReady(PocketBackgroundService service,PocketBackgroundService.Context context,String location) throws Exception {
        for(int i=0;i<200 && service.resolve(context,location).status().equals("GENERATING");i++) Thread.sleep(20);
        assertThat(service.resolve(context,location).status()).isEqualTo("READY");
    }
    @Test void newAndLegacyDefaultOffCatalogsGenerateAutomaticallyButExplicitOffSurvivesRestart() throws Exception {
        var mapper=new JsonMapper();var store=new PocketBackgroundStore(directory.toString(),mapper);
        assertThat(store.catalog("1-2").automatic()).isTrue();
        Files.createDirectories(directory.resolve("1-2"));
        Files.write(directory.resolve("1-2/index.json"),mapper.writeValueAsBytes(new PocketBackgroundStore.Catalog(false,java.util.List.of())));
        assertThat(store.catalog("1-2").automatic()).isTrue();
        store.configure("1-2",false);
        assertThat(new PocketBackgroundStore(directory.toString(),mapper).catalog("1-2").automatic()).isFalse();
    }
}
