package com.fdl.platform.auth.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("fdl.security")
public record SecurityProperties(@Min(4) @Max(15) @DefaultValue("12") int bcryptStrength) {
}
