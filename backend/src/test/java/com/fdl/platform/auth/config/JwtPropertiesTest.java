package com.fdl.platform.auth.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.validation.autoconfigure.ValidationAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;

class JwtPropertiesTest {

    @Configuration
    @EnableConfigurationProperties(JwtProperties.class)
    static class Config {
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ValidationAutoConfiguration.class))
            .withUserConfiguration(Config.class);

    @Test
    void startupFailsWithoutASecret() {
        runner.run(context -> assertThat(context).hasFailed());
    }

    @Test
    void startupFailsWithABlankSecret() {
        runner.withPropertyValues("fdl.jwt.secret=").run(context -> assertThat(context).hasFailed());
    }

    @Test
    void startupFailsWithASecretShorterThan256Bits() {
        runner.withPropertyValues("fdl.jwt.secret=short-secret")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void aLongEnoughSecretStartsWithDefaults() {
        runner.withPropertyValues("fdl.jwt.secret=" + "x".repeat(32)).run(context -> {
            assertThat(context).hasNotFailed();
            var props = context.getBean(JwtProperties.class);
            assertThat(props.ttl()).isEqualTo(Duration.ofMinutes(60));
            assertThat(props.issuer()).isEqualTo("fdl-platform");
        });
    }
}
