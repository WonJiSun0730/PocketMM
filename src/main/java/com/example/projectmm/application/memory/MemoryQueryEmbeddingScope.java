package com.example.projectmm.application.memory;

import java.util.*;
import java.util.function.Supplier;

/** Successful query vectors only, scoped to one context build; never caches recall results. */
public final class MemoryQueryEmbeddingScope {
    private record Key(String namespace,String query) {}
    private static final ThreadLocal<IdentityHashMap<MemoryEmbeddingProvider,Map<Key,List<Double>>>> CURRENT=new ThreadLocal<>();
    private MemoryQueryEmbeddingScope() {}
    public static <T> T within(Supplier<T> work) {
        var previous=CURRENT.get();CURRENT.set(new IdentityHashMap<>());
        try {return work.get();} finally {if(previous==null)CURRENT.remove();else CURRENT.set(previous);}
    }
    static List<Double> embed(MemoryEmbeddingProvider provider,String query) {
        var scope=CURRENT.get();
        if(scope==null)return provider.embed(query);
        var cache=scope.computeIfAbsent(provider,ignored->new HashMap<>());
        var key=new Key(provider.cacheNamespace(),query);
        var cached=cache.get(key);if(cached!=null)return cached;
        var vector=provider.embed(query);
        if(vector==null || vector.isEmpty())return vector;
        var immutable=List.copyOf(vector);cache.put(key,immutable);return immutable;
    }
}
