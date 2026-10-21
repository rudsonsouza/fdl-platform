package com.fdl.platform.auth.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("fdl.cors")
public record CorsProperties(@DefaultValue("http://localhost:5173") List<String> allowedOrigins) {
}
