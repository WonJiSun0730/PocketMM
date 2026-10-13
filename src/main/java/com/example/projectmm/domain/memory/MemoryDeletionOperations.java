package com.example.projectmm.domain.memory;

import java.util.Collection;

public interface MemoryDeletionOperations {
    void deleteWithDependencies(Collection<Long> memoryIds);
}
