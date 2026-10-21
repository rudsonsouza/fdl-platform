package com.fdl.platform.auth.service;

import com.fdl.platform.auth.config.JwtProperties;
import com.fdl.platform.user.domain.User;
import java.time.Clock;
import java.time.Instant;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

/** Emite o token de acesso (JWT HS256) com {@code sub}, {@code email}, {@code name}, {@code iss}, {@code iat} e {@code exp}. */
@Service
public class JwtTokenService {

    public record IssuedToken(String value, long expiresInSeconds) {
    }

    private final JwtEncoder encoder;
    private final JwtProperties properties;
    private final Clock clock;

    public JwtTokenService(JwtEncoder encoder, JwtProperties properties, Clock clock) {
        this.encoder = encoder;
        this.properties = properties;
        this.clock = clock;
    }

    public IssuedToken issue(User user) {
        Instant now = clock.instant();
        var claims = JwtClaimsSet.builder()
                .issuer(properties.issuer())
                .subject(user.getId().toString())
                .claim("email", user.getEmail())
                .claim("name", user.getName())
                .issuedAt(now)
                .expiresAt(now.plus(properties.ttl()))
                .build();
        var header = JwsHeader.with(MacAlgorithm.HS256).build();
        var token = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new IssuedToken(token, properties.ttl().toSeconds());
    }
}
