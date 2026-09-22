package com.example.projectmm.application.policy;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ContentModePolicy {

    private final ContentMode defaultMode;
    private final boolean adultModeAllowed;

    public ContentModePolicy(
            @Value("${pocketmm.content-mode:NORMAL}") ContentMode defaultMode,
            @Value("${pocketmm.adult-mode-allowed:false}") boolean adultModeAllowed
    ) {
        this.defaultMode = defaultMode;
        this.adultModeAllowed = adultModeAllowed;
    }

    public ContentMode resolve(ContentMode requestedMode) {

        if (requestedMode == ContentMode.ADULT && !adultModeAllowed) {
            return ContentMode.NORMAL;
        }

        if (requestedMode == null) {
            return defaultMode;
        }

        return requestedMode;
    }

    public boolean isAdultModeAllowed() {
        return adultModeAllowed;
    }
}