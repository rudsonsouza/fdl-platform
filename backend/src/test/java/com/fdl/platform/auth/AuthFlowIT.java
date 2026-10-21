package com.fdl.platform.auth;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fdl.platform.user.repository.UserRepository;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.json.JsonMapper;

/** Fluxo real: cadastro, login, token no cabeçalho, erros de autenticação, logs e OpenAPI. */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT, properties = {
        // Logs no nível mais verboso: se algum segredo vazar, é aqui que aparece.
        "logging.level.com.fdl.platform=DEBUG",
        "logging.level.org.springframework.security=DEBUG",
        "logging.level.org.springframework.web=DEBUG"})
@ActiveProfiles("test")
class AuthFlowIT {

    @TempDir
    static Path dbDir;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:sqlite:" + dbDir.resolve("fdl-auth-it.db"));
    }

    @LocalServerPort
    int port;

    @Autowired
    JsonMapper json;

    @Autowired
    UserRepository users;

    @Autowired
    JwtEncoder encoder;

    @Autowired
    Clock clock;

    private final HttpClient client = HttpClient.newHttpClient();
    private final ListAppender<ILoggingEvent> logs = new ListAppender<>();
    private final Logger root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);

    @BeforeEach
    void captureLogs() {
        logs.start();
        root.addAppender(logs);
    }

    @AfterEach
    void releaseLogs() {
        root.detachAppender(logs);
    }

    @Test
    void registerLoginAndCallAProtectedEndpoint() throws Exception {
        var email = "flow-" + System.nanoTime() + "@exemplo.com";

        var registered = post("/api/v1/auth/register", register("Ana Souza", email.toUpperCase(), "segredo123"));
        assertThat(registered.statusCode()).isEqualTo(201);
        assertThat(registered.body()).doesNotContain("segredo123").doesNotContain("password");
        var id = json.readTree(registered.body()).get("id").asString();
        assertThat(json.readTree(registered.body()).get("email").asString()).isEqualTo(email);

        // a senha persistida é um hash BCrypt, não o texto
        var stored = users.findByEmailIgnoreCase(email).orElseThrow().getPasswordHash();
        assertThat(stored).isNotEqualTo("segredo123").startsWith("$2");

        var duplicate = post("/api/v1/auth/register", register("Outra", email, "segredo123"));
        assertThat(duplicate.statusCode()).isEqualTo(409);
        assertThat(json.readTree(duplicate.body()).get("code").asString()).isEqualTo("EMAIL_ALREADY_REGISTERED");

        var login = post("/api/v1/auth/login", "{\"email\":\"" + email + "\",\"password\":\"segredo123\"}");
        assertThat(login.statusCode()).isEqualTo(200);
        var body = json.readTree(login.body());
        assertThat(body.get("tokenType").asString()).isEqualTo("Bearer");
        assertThat(body.get("expiresIn").asLong()).isEqualTo(3600);

        var me = get("/api/v1/auth/me", body.get("accessToken").asString());
        assertThat(me.statusCode()).isEqualTo(200);
        assertThat(json.readTree(me.body()).get("id").asString()).isEqualTo(id);
    }

    @Test
    void wrongPasswordAndUnknownEmailGetTheSameResponse() throws Exception {
        var email = "same-" + System.nanoTime() + "@exemplo.com";
        post("/api/v1/auth/register", register("Ana Souza", email, "segredo123"));

        var wrong = post("/api/v1/auth/login", "{\"email\":\"" + email + "\",\"password\":\"errada123\"}");
        var unknown = post("/api/v1/auth/login", "{\"email\":\"ninguem@exemplo.com\",\"password\":\"errada123\"}");

        assertThat(wrong.statusCode()).isEqualTo(401);
        assertThat(unknown.statusCode()).isEqualTo(401);
        var a = json.readTree(wrong.body());
        var b = json.readTree(unknown.body());
        assertThat(a.get("code").asString()).isEqualTo("INVALID_CREDENTIALS").isEqualTo(b.get("code").asString());
        assertThat(a.get("detail")).isEqualTo(b.get("detail"));
    }

    @Test
    void protectedEndpointsRejectMissingExpiredAndTamperedTokens() throws Exception {
        var valid = token("00000000-0000-0000-0000-000000000001", Duration.ofMinutes(5));
        var expired = token("00000000-0000-0000-0000-000000000001", Duration.ofMinutes(-5));
        var tampered = valid.substring(0, valid.length() - 4) + (valid.endsWith("AAAA") ? "BBBB" : "AAAA");

        assertUnauthenticated(get("/api/v1/orders", null));
        assertUnauthenticated(get("/api/v1/orders", expired));
        assertUnauthenticated(get("/api/v1/orders", tampered));
        assertUnauthenticated(get("/api/v1/orders", "not-a-jwt"));
        // token válido mas de usuário inexistente: o /me falha na autenticação, sem 500
        assertUnauthenticated(get("/api/v1/auth/me", valid));
    }

    @Test
    void publicEndpointsDoNotNeedAToken() throws Exception {
        assertThat(get("/actuator/health", null).statusCode()).isEqualTo(200);
        assertThat(post("/api/v1/auth/login", "{}").statusCode()).isEqualTo(400);
        assertThat(post("/api/v1/auth/register", "{}").statusCode()).isEqualTo(400);
    }

    @Test
    void corsPreflightFromTheConfiguredOriginIsAllowed() throws Exception {
        var request = HttpRequest.newBuilder(uri("/api/v1/auth/login"))
                .method("OPTIONS", BodyPublishers.noBody())
                .header("Origin", "http://localhost:5173")
                .header("Access-Control-Request-Method", "POST")
                .header("Access-Control-Request-Headers", "authorization,content-type")
                .build();

        var response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Access-Control-Allow-Origin")).hasValue("http://localhost:5173");
    }

    @Test
    void corsPreflightFromAnotherOriginIsRejected() throws Exception {
        var request = HttpRequest.newBuilder(uri("/api/v1/auth/login"))
                .method("OPTIONS", BodyPublishers.noBody())
                .header("Origin", "http://evil.example")
                .header("Access-Control-Request-Method", "POST")
                .build();

        var response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(403);
    }

    @Test
    void failedLoginLeaksNeitherPasswordNorTokenToTheLogs() throws Exception {
        var email = "logs-" + System.nanoTime() + "@exemplo.com";
        var registered = post("/api/v1/auth/register", register("Ana Souza", email, "segredo123"));
        var token = json.readTree(post("/api/v1/auth/login",
                "{\"email\":\"" + email + "\",\"password\":\"segredo123\"}").body()).get("accessToken").asString();

        var failed = post("/api/v1/auth/login", "{\"email\":\"" + email + "\",\"password\":\"SenhaErrada987\"}");
        get("/api/v1/auth/me", token + "x");

        assertThat(failed.statusCode()).isEqualTo(401);
        assertThat(failed.body()).doesNotContain("SenhaErrada987");
        assertThat(registered.statusCode()).isEqualTo(201);
        var captured = logs.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
        assertThat(captured).isNotEmpty();
        assertThat(String.join("\n", captured))
                .doesNotContain("SenhaErrada987")
                .doesNotContain("segredo123")
                .doesNotContain(token);
    }

    @Test
    void openApiDocumentsTheAuthEndpointsAndTheProblemSchema() throws Exception {
        var response = get("/v3/api-docs", null);

        assertThat(response.statusCode()).isEqualTo(200);
        var doc = json.readTree(response.body());
        var paths = doc.get("paths");
        assertThat(paths.has("/api/v1/auth/register")).isTrue();
        assertThat(paths.has("/api/v1/auth/login")).isTrue();
        assertThat(paths.has("/api/v1/auth/me")).isTrue();
        assertThat(paths.get("/api/v1/auth/register").get("post").get("responses").has("409")).isTrue();
        assertThat(paths.get("/api/v1/auth/login").get("post").get("responses").has("401")).isTrue();
        assertThat(paths.get("/api/v1/auth/me").get("get").has("security")).isTrue();
        assertThat(doc.get("components").get("schemas").has("ProblemDetail")).isTrue();
        assertThat(doc.get("components").get("securitySchemes").has("bearerAuth")).isTrue();
    }

    // ---- helpers ----

    private void assertUnauthenticated(HttpResponse<String> response) {
        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(response.headers().firstValue("Content-Type").orElse("")).contains("application/problem+json");
        assertThat(response.headers().firstValue("WWW-Authenticate").orElse("")).startsWith("Bearer");
        assertThat(json.readTree(response.body()).get("code").asString()).isEqualTo("UNAUTHENTICATED");
    }

    private String token(String subject, Duration validFor) {
        var now = clock.instant();
        var claims = org.springframework.security.oauth2.jwt.JwtClaimsSet.builder()
                .issuer("fdl-platform")
                .subject(subject)
                .issuedAt(now.minus(Duration.ofHours(2)))
                .expiresAt(now.plus(validFor))
                .build();
        var header = org.springframework.security.oauth2.jwt.JwsHeader
                .with(org.springframework.security.oauth2.jose.jws.MacAlgorithm.HS256).build();
        return encoder.encode(org.springframework.security.oauth2.jwt.JwtEncoderParameters.from(header, claims))
                .getTokenValue();
    }

    private static String register(String name, String email, String password) {
        return "{\"name\":\"" + name + "\",\"email\":\"" + email + "\",\"password\":\"" + password + "\"}";
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }

    private HttpResponse<String> post(String path, String body) throws Exception {
        var request = HttpRequest.newBuilder(uri(path)).header("Content-Type", "application/json")
                .POST(BodyPublishers.ofString(body)).build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> get(String path, String bearer) throws Exception {
        var builder = HttpRequest.newBuilder(uri(path)).GET();
        if (bearer != null) {
            builder.header("Authorization", "Bearer " + bearer);
        }
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }
}
