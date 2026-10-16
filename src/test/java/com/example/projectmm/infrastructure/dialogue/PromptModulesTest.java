package com.example.projectmm.infrastructure.dialogue;

import org.junit.jupiter.api.Test;
import java.io.InputStream;
import static org.assertj.core.api.Assertions.*;

class PromptModulesTest {
    @Test void missingResourceFailsClosed() {
        assertThatThrownBy(()->new PromptModules(new ClassLoader(null) {
            @Override public InputStream getResourceAsStream(String name) {return null;}
        })).isInstanceOf(IllegalStateException.class).hasMessageContaining("prompts/SYSTEM.md");
    }
    @Test void duplicateEmptyOrUnknownSectionsAreRejected() {
        String section="<!-- module: a -->\nrule\n<!-- /module -->\n";
        assertThatThrownBy(()->PromptModules.parse(section+section,"fixture")).hasMessageContaining("duplicate");
        assertThatThrownBy(()->PromptModules.parse(section.replace("rule",""),"fixture")).hasMessageContaining("Empty");
        assertThatThrownBy(()->PromptModules.section("SYSTEM","missing")).hasMessageContaining("missing");
    }
    @Test void staticMarkupIsExcludedAndLineEndingsArePortable() {
        var value=PromptModules.parse("# Docs\r\n<!-- module: a -->\r\nrule\r\n<!-- /module -->\r\n","fixture");
        assertThat(value.get("a")).isEqualTo("rule\n");
        PromptModules.validate();
    }
}
