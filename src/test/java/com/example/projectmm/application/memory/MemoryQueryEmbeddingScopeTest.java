package com.example.projectmm.application.memory;

import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.*;

class MemoryQueryEmbeddingScopeTest {
    @Test void sharesOnlyExactQueryProviderAndNamespaceInsideOneBuild() {
        var calls=new AtomicInteger();var ns=new String[]{"model-a"};
        MemoryEmbeddingProvider provider=new MemoryEmbeddingProvider(){
            public String cacheNamespace(){return ns[0];}
            public List<Double> embed(String text){calls.incrementAndGet();return List.of(1.0,0.0);}
        };
        MemoryEmbeddingProvider other=text->{calls.incrementAndGet();return List.of(0.0,1.0);};
        MemoryQueryEmbeddingScope.within(()->{
            var first=MemoryQueryEmbeddingScope.embed(provider,"same");
            assertThat(MemoryQueryEmbeddingScope.embed(provider,"same")).isSameAs(first);
            assertThatThrownBy(()->first.add(2.0)).isInstanceOf(UnsupportedOperationException.class);
            MemoryQueryEmbeddingScope.embed(provider,"same ");
            ns[0]="model-b";MemoryQueryEmbeddingScope.embed(provider,"same");
            MemoryQueryEmbeddingScope.embed(other,"same");return null;
        });
        assertThat(calls).hasValue(4);
        MemoryQueryEmbeddingScope.within(()->MemoryQueryEmbeddingScope.embed(provider,"same"));
        assertThat(calls).hasValue(5);
    }
    @Test void failureClearsScopeAndDoesNotHideExceptionsOrReuseVectorsAcrossRequests() {
        var calls=new AtomicInteger();MemoryEmbeddingProvider provider=text->{calls.incrementAndGet();return List.of(1.0);};
        assertThatThrownBy(()->MemoryQueryEmbeddingScope.within(()->{
            MemoryQueryEmbeddingScope.embed(provider,"q");throw new IllegalStateException("failure");
        })).hasMessage("failure");
        MemoryQueryEmbeddingScope.embed(provider,"q");MemoryQueryEmbeddingScope.embed(provider,"q");
        assertThat(calls).hasValue(3);
    }
    @Test void nestedBuildsAreIsolatedAndRestoreTheOuterScope() {
        var calls=new AtomicInteger();MemoryEmbeddingProvider provider=text->List.of((double)calls.incrementAndGet());
        MemoryQueryEmbeddingScope.within(()->{
            assertThat(MemoryQueryEmbeddingScope.embed(provider,"q")).containsExactly(1.0);
            MemoryQueryEmbeddingScope.within(()->{
                assertThat(MemoryQueryEmbeddingScope.embed(provider,"q")).containsExactly(2.0);return null;
            });
            assertThat(MemoryQueryEmbeddingScope.embed(provider,"q")).containsExactly(1.0);return null;
        });
        assertThat(calls).hasValue(2);
    }
}
