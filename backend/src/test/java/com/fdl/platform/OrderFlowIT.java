package com.fdl.platform;

import static org.assertj.core.api.Assertions.assertThat;

import com.fdl.platform.support.OrderJson;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.json.JsonMapper;

/** Jornada completa de um operador pela API real: do cadastro à entrega e à tentativa de cancelar depois dela. */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class OrderFlowIT {

    @TempDir
    static Path dbDir;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:sqlite:" + dbDir.resolve("fdl-flow-it.db"));
    }

    @LocalServerPort
    int port;

    @Autowired
    JsonMapper json;

    private final HttpClient client = HttpClient.newHttpClient();

    @Test
    void operatorRegistersLogsInAndTakesAnOrderFromReceivedToDelivered() throws Exception {
        // cadastro e login
        var registered = send("POST", "/api/v1/auth/register", null,
                "{\"name\":\"Ana Souza\",\"email\":\"ana.fluxo@exemplo.com\",\"password\":\"segredo123\"}");
        assertThat(registered.statusCode()).isEqualTo(201);
        var login = send("POST", "/api/v1/auth/login", null,
                "{\"email\":\"ANA.FLUXO@exemplo.com\",\"password\":\"segredo123\"}");
        assertThat(login.statusCode()).isEqualTo(200);
        var token = json.readTree(login.body()).get("accessToken").asString();

        // criar
        var created = send("POST", "/api/v1/orders", token, OrderJson.valid());
        assertThat(created.statusCode()).isEqualTo(201);
        var id = json.readTree(created.body()).get("id").asString();
        assertThat(json.readTree(created.body()).get("status").asString()).isEqualTo("RECEBIDO");

        // listar: o pedido aparece com o status atual
        var list = json.readTree(send("GET", "/api/v1/orders", token, null).body());
        var found = false;
        for (int i = 0; i < list.get("content").size(); i++) {
            var row = list.get("content").get(i);
            if (row.get("id").asString().equals(id)) {
                found = true;
                assertThat(row.get("status").asString()).isEqualTo("RECEBIDO");
                assertThat(row.get("customerName").asString()).isEqualTo("Maria Silva");
                assertThat(row.get("totalCents").asLong()).isEqualTo(7890);
                // 1 pizza + 2 refrigerantes: a listagem soma as quantidades sem carregar os itens
                assertThat(row.get("totalItems").asInt()).isEqualTo(3);
            }
        }
        assertThat(found).isTrue();

        // buscar
        var detail = json.readTree(send("GET", "/api/v1/orders/" + id, token, null).body());
        assertThat(detail.get("allowedTransitions").size()).isEqualTo(2);
        assertThat(detail.get("history").size()).isEqualTo(1);

        // avançar até a entrega
        for (var next : List.of("EM_PREPARO", "SAIU_PARA_ENTREGA", "ENTREGUE")) {
            var step = send("PATCH", "/api/v1/orders/" + id + "/status", token, "{\"status\":\"" + next + "\"}");
            assertThat(step.statusCode()).isEqualTo(200);
            assertThat(json.readTree(step.body()).get("status").asString()).isEqualTo(next);
        }

        // cancelar depois de entregue: 409, sem transições possíveis
        var cancel = send("PATCH", "/api/v1/orders/" + id + "/status", token, "{\"status\":\"CANCELADO\"}");
        assertThat(cancel.statusCode()).isEqualTo(409);
        var problem = json.readTree(cancel.body());
        assertThat(problem.get("code").asString()).isEqualTo("INVALID_STATUS_TRANSITION");
        assertThat(problem.get("currentStatus").asString()).isEqualTo("ENTREGUE");
        assertThat(problem.get("requestedStatus").asString()).isEqualTo("CANCELADO");
        assertThat(problem.get("allowedTransitions").size()).isZero();

        // histórico final: 4 entradas na ordem certa, a rejeitada não conta
        var finalState = json.readTree(send("GET", "/api/v1/orders/" + id, token, null).body());
        assertThat(finalState.get("status").asString()).isEqualTo("ENTREGUE");
        assertThat(finalState.get("allowedTransitions").size()).isZero();
        var history = finalState.get("history");
        assertThat(history.size()).isEqualTo(4);
        var expected = List.of("RECEBIDO", "EM_PREPARO", "SAIU_PARA_ENTREGA", "ENTREGUE");
        for (int i = 0; i < 4; i++) {
            assertThat(history.get(i).get("to").asString()).isEqualTo(expected.get(i));
            assertThat(history.get(i).get("changedBy").asString()).isNotBlank();
            assertThat(history.get(i).get("changedAt").asString()).endsWith("Z");
        }
        assertThat(history.get(0).get("from").isNull()).isTrue();
    }

    private HttpResponse<String> send(String method, String path, String bearer, String body) throws Exception {
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
