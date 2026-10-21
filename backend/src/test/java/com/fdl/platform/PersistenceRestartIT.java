package com.fdl.platform;

import static org.assertj.core.api.Assertions.assertThat;

import com.fdl.platform.support.OrderJson;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;
import tools.jackson.databind.json.JsonMapper;

/** Os dados precisam sobreviver a um reinício da aplicação apontando para o mesmo arquivo SQLite. */
class PersistenceRestartIT {

    @TempDir
    Path dbDir;

    private final HttpClient client = HttpClient.newHttpClient();
    private final JsonMapper json = JsonMapper.builder().build();

    @Test
    void ordersAndUsersSurviveARestartOnTheSameDatabaseFile() throws Exception {
        String orderId;
        String email = "restart@exemplo.com";

        var first = start();
        try {
            var port = port(first);
            assertThat(send(port, "POST", "/api/v1/auth/register", null,
                    "{\"name\":\"Ana Souza\",\"email\":\"" + email + "\",\"password\":\"segredo123\"}")
                    .statusCode()).isEqualTo(201);
            var token = login(port, email);
            var created = send(port, "POST", "/api/v1/orders", token, OrderJson.valid());
            assertThat(created.statusCode()).isEqualTo(201);
            orderId = json.readTree(created.body()).get("id").asString();
            send(port, "PATCH", "/api/v1/orders/" + orderId + "/status", token, "{\"status\":\"EM_PREPARO\"}");
        } finally {
            first.close();
        }

        var second = start();
        try {
            var port = port(second);
            var token = login(port, email); // o usuário e o hash da senha persistiram
            var response = send(port, "GET", "/api/v1/orders/" + orderId, token, null);

            assertThat(response.statusCode()).isEqualTo(200);
            var order = json.readTree(response.body());
            assertThat(order.get("id").asString()).isEqualTo(orderId);
            assertThat(order.get("status").asString()).isEqualTo("EM_PREPARO");
            assertThat(order.get("totalCents").asLong()).isEqualTo(7890);
            assertThat(order.get("items").size()).isEqualTo(2);
            assertThat(order.get("history").size()).isEqualTo(2);
        } finally {
            second.close();
        }
    }

    private ConfigurableApplicationContext start() {
        return new SpringApplication(FdlApplication.class) {
            {
                setWebApplicationType(WebApplicationType.SERVLET);
            }
        }.run("--spring.profiles.active=test", "--server.port=0",
                "--spring.datasource.url=jdbc:sqlite:" + dbDir.resolve("fdl-restart-it.db"));
    }

    private static int port(ConfigurableApplicationContext context) {
        return Integer.parseInt(context.getBean(Environment.class).getProperty("local.server.port"));
    }

    private String login(int port, String email) throws Exception {
        var response = send(port, "POST", "/api/v1/auth/login", null,
                "{\"email\":\"" + email + "\",\"password\":\"segredo123\"}");
        assertThat(response.statusCode()).isEqualTo(200);
        return json.readTree(response.body()).get("accessToken").asString();
    }

    private HttpResponse<String> send(int port, String method, String path, String bearer, String body)
            throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .method(method, body == null ? BodyPublishers.noBody() : BodyPublishers.ofString(body));
        if (body != null) {
            builder.header("Content-Type", "application/json");
        }
        if (bearer != null) {
            builder.header("Authorization", "Bearer " + bearer);
        }
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }
}
