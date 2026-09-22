package com.example.projectmm.application.policy;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ContentModePolicyTest {

    @Test
    void adult가_허용된_환경에서는_adult를_유지한다() {
        ContentModePolicy policy =
                new ContentModePolicy(ContentMode.NORMAL, true);

        ContentMode result =
                policy.resolve(ContentMode.ADULT);

        assertThat(result).isEqualTo(ContentMode.ADULT);
    }

    @Test
    void adult가_금지된_환경에서는_normal로_강제한다() {
        ContentModePolicy policy =
                new ContentModePolicy(ContentMode.NORMAL, false);

        ContentMode result =
                policy.resolve(ContentMode.ADULT);

        assertThat(result).isEqualTo(ContentMode.NORMAL);
    }

    @Test
    void 요청_모드가_없으면_기본_모드를_사용한다() {
        ContentModePolicy policy =
                new ContentModePolicy(ContentMode.NORMAL, true);

        ContentMode result =
                policy.resolve(null);

        assertThat(result).isEqualTo(ContentMode.NORMAL);
    }
}