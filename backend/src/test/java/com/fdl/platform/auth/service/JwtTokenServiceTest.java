package com.fdl.platform.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fdl.platform.auth.config.JwtConfig;
import com.fdl.platform.auth.config.JwtProperties;
import com.fdl.platform.support.Users;
import java.time.Clock;
import java.time.Duration;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.security.oauth2.jwt.BadJwtException;

class JwtTokenServiceTest {

    private static final String SECRET = "unit-test-secret-unit-test-secret-0123456789";

    private final JwtProperties properties = new JwtProperties(SECRET, Duration.ofMinutes(60), "fdl-platform");
    private final JwtConfig config = new JwtConfig();

    private JwtTokenService service(JwtProperties props, Clock clock) {
        return new JwtTokenService(config.jwtEncoder(props), props, clock);
    }

    private JwtDecoder decoder(JwtProperties props, Clock clock) {
        return config.jwtDecoder(props, clock);
    }

    @Test
    void issuedTokenCarriesTheExpectedClaims() {
        var user = Users.ana();

        var issued = service(properties, Users.CLOCK).issue(user);
        var jwt = decoder(properties, Users.CLOCK).decode(issued.value());

        assertThat(jwt.getSubject()).isEqualTo(user.getId().toString());
        assertThat(jwt.getClaimAsString("email")).isEqualTo("ana@exemplo.com");
        assertThat(jwt.getClaimAsString("name")).isEqualTo("Ana Souza");
        assertThat(jwt.getClaimAsString("iss")).isEqualTo("fdl-platform");
        assertThat(jwt.getIssuedAt()).isEqualTo(Users.CLOCK.instant());
        assertThat(jwt.getExpiresAt()).isEqualTo(Users.CLOCK.instant().plus(Duration.ofMinutes(60)));
        assertThat(jwt.getHeaders()).containsEntry("alg", "HS256");
        assertThat(issued.expiresInSeconds()).isEqualTo(3600);
    }

    @Test
    void ttlIsConfigurable() {
        var props = new JwtProperties(SECRET, Duration.ofSeconds(90), "fdl-platform");

        assertThat(service(props, Users.CLOCK).issue(Users.ana()).expiresInSeconds()).isEqualTo(90);
    }

    @Test
    void tokenIsRejectedAfterItExpires() {
        var token = service(properties, Users.CLOCK).issue(Users.ana()).value();
        var later = Clock.fixed(Users.CLOCK.instant().plus(Duration.ofMinutes(61)), ZoneOffset.UTC);

        assertThatThrownBy(() -> decoder(properties, later).decode(token))
                .isInstanceOf(JwtValidationException.class)
                .hasMessageContaining("expired");
    }

    @Test
    void tokenIsRejectedOneSecondAfterExpiry() {
        var token = service(properties, Users.CLOCK).issue(Users.ana()).value();
        var justAfter = Clock.fixed(Users.CLOCK.instant().plus(Duration.ofMinutes(60)).plusSeconds(1), ZoneOffset.UTC);

        assertThatThrownBy(() -> decoder(properties, justAfter).decode(token))
                .isInstanceOf(JwtValidationException.class);
    }

    @Test
    void tokenSignedWithAnotherSecretIsRejected() {
        var other = new JwtProperties("another-secret-another-secret-0123456789ab", Duration.ofMinutes(60),
                "fdl-platform");
        var token = service(other, Users.CLOCK).issue(Users.ana()).value();

        assertThatThrownBy(() -> decoder(properties, Users.CLOCK).decode(token)).isInstanceOf(BadJwtException.class);
    }

    @Test
    void tamperedPayloadIsRejected() {
        var token = service(properties, Users.CLOCK).issue(Users.ana()).value();
        var parts = token.split("\\.");
        var forgedPayload = java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"sub\":\"00000000-0000-0000-0000-000000000000\"}".getBytes());
        var forged = parts[0] + "." + forgedPayload + "." + parts[2];

        assertThatThrownBy(() -> decoder(properties, Users.CLOCK).decode(forged)).isInstanceOf(BadJwtException.class);
    }

    @Test
    void tokenFromAnotherIssuerIsRejected() {
        var other = new JwtProperties(SECRET, Duration.ofMinutes(60), "someone-else");
        var token = service(other, Users.CLOCK).issue(Users.ana()).value();

        assertThatThrownBy(() -> decoder(properties, Users.CLOCK).decode(token))
                .isInstanceOf(JwtValidationException.class);
    }
}
