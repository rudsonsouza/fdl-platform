package com.fdl.platform.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doAnswer;

import com.fdl.platform.order.api.CreateOrderRequest;
import com.fdl.platform.order.service.OrderService;
import com.fdl.platform.support.OrderJson;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** API de pedidos de ponta a ponta, com SQLite real e autenticação por token. */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class OrdersApiIT {

    @TempDir
    static Path dbDir;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:sqlite:" + dbDir.resolve("fdl-orders-it.db"));
    }

    @LocalServerPort
    int port;

    @Autowired
    JsonMapper json;

    @Autowired
    OrderService service;

    @Autowired
    JdbcTemplate jdbc;

    // O Clock é lido por Order#changeStatus depois que o serviço carregou o pedido e antes do flush:
    // é o ponto em que a "outra transação" é simulada. (O repositório é um proxy de interface e não pode ser espionado.)
    @MockitoSpyBean
    Clock clock;

    private final HttpClient client = HttpClient.newHttpClient();
    private String token;
    private UUID userId;

    @BeforeEach
    void authenticate() throws Exception {
        var email = "ops-" + System.nanoTime() + "@exemplo.com";
        var registered = send("POST", "/api/v1/auth/register", null,
                "{\"name\":\"Operador\",\"email\":\"" + email + "\",\"password\":\"segredo123\"}");
        userId = UUID.fromString(json.readTree(registered.body()).get("id").asString());
        var login = send("POST", "/api/v1/auth/login", null,
                "{\"email\":\"" + email + "\",\"password\":\"segredo123\"}");
        token = json.readTree(login.body()).get("accessToken").asString();
    }

    // ---- fluxo completo ----

    @Test
    void createdOrderGoesThroughTheWholeDeliveryFlowWithHistory() throws Exception {
        var created = send("POST", "/api/v1/orders", token, OrderJson.valid());
        assertThat(created.statusCode()).isEqualTo(201);
        var body = json.readTree(created.body());
        var id = body.get("id").asString();
        assertThat(created.headers().firstValue("Location")).hasValue("/api/v1/orders/" + id);
        assertThat(body.get("status").asString()).isEqualTo("RECEBIDO");
        assertThat(body.get("totalCents").asLong()).isEqualTo(7890);
        assertThat(body.get("createdBy").asString()).isEqualTo(userId.toString());

        assertThat(patchStatus(id, "SAIU_PARA_ENTREGA").statusCode()).isEqualTo(409); // pular etapa
        for (var next : List.of("EM_PREPARO", "SAIU_PARA_ENTREGA", "ENTREGUE")) {
            var step = patchStatus(id, next);
            assertThat(step.statusCode()).isEqualTo(200);
            assertThat(json.readTree(step.body()).get("status").asString()).isEqualTo(next);
        }

        var finalState = json.readTree(send("GET", "/api/v1/orders/" + id, token, null).body());
        assertThat(finalState.get("status").asString()).isEqualTo("ENTREGUE");
        assertThat(finalState.get("allowedTransitions").size()).isZero();
        var history = finalState.get("history");
        assertThat(history.size()).isEqualTo(4);
        assertThat(history.get(0).get("from").isNull()).isTrue();
        assertThat(history.get(0).get("to").asString()).isEqualTo("RECEBIDO");
        assertThat(history.get(3).get("from").asString()).isEqualTo("SAIU_PARA_ENTREGA");
        assertThat(history.get(3).get("to").asString()).isEqualTo("ENTREGUE");
        assertThat(history.get(3).get("changedBy").asString()).isEqualTo(userId.toString());
        assertThat(history.get(3).get("changedAt").asString()).endsWith("Z");
        assertThat(finalState.get("items").size()).isEqualTo(2);
        assertThat(finalState.get("items").get(0).get("name").asString()).isEqualTo("Pizza");
        assertThat(finalState.get("items").get(1).get("subtotalCents").asLong()).isEqualTo(2400);
    }

    @Test
    void rejectedTransitionsDoNotChangeTheOrderOrItsHistory() throws Exception {
        var id = createOrder();
        patchStatus(id, "EM_PREPARO");
        patchStatus(id, "SAIU_PARA_ENTREGA");

        var rejected = patchStatus(id, "CANCELADO");

        assertThat(rejected.statusCode()).isEqualTo(409);
        var problem = json.readTree(rejected.body());
        assertThat(problem.get("code").asString()).isEqualTo("INVALID_STATUS_TRANSITION");
        assertThat(problem.get("detail").asString()).isEqualTo("Não é possível ir de SAIU_PARA_ENTREGA para CANCELADO");
        assertThat(problem.get("currentStatus").asString()).isEqualTo("SAIU_PARA_ENTREGA");
        assertThat(problem.get("requestedStatus").asString()).isEqualTo("CANCELADO");
        assertThat(problem.get("allowedTransitions").get(0).asString()).isEqualTo("ENTREGUE");
        var order = json.readTree(send("GET", "/api/v1/orders/" + id, token, null).body());
        assertThat(order.get("status").asString()).isEqualTo("SAIU_PARA_ENTREGA");
        assertThat(order.get("history").size()).isEqualTo(3);
    }

    @Test
    void unknownOrderIs404AndBadIdIs400() throws Exception {
        var missing = send("GET", "/api/v1/orders/" + UUID.randomUUID(), token, null);
        assertThat(missing.statusCode()).isEqualTo(404);
        assertThat(json.readTree(missing.body()).get("code").asString()).isEqualTo("ORDER_NOT_FOUND");
        assertThat(patchStatus(UUID.randomUUID().toString(), "EM_PREPARO").statusCode()).isEqualTo(404);
        assertThat(send("GET", "/api/v1/orders/abc", token, null).statusCode()).isEqualTo(400);
    }

    @Test
    void invalidPayloadsPersistNothing() throws Exception {
        var before = totalOrders();

        var response = send("POST", "/api/v1/orders", token, OrderJson.replacing("\"state\":\"SP\"", "\"state\":\"sp\""));

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.headers().firstValue("Content-Type").orElse("")).contains("application/problem+json");
        assertThat(json.readTree(response.body()).get("errors").get(0).get("field").asString())
                .isEqualTo("deliveryAddress.state");
        assertThat(totalOrders()).isEqualTo(before);
    }

    // ---- listagem ----

    @Test
    void listWithoutParametersReturnsEverythingInOnePage() throws Exception {
        var before = totalOrders();
        for (int i = 0; i < 30; i++) {
            createOrder();
        }

        var page = json.readTree(send("GET", "/api/v1/orders", token, null).body());

        assertThat(page.get("totalElements").asLong()).isEqualTo(before + 30);
        assertThat(page.get("size").asInt()).isEqualTo(100);
        assertThat(page.get("page").asInt()).isZero();
        assertThat(page.get("content").size()).isEqualTo((int) Math.min(100, before + 30));
    }

    @Test
    void explicitPaginationSplitsTheRemainingOrders() throws Exception {
        while (totalOrders() < 150) {
            service.create(request(), userId);
        }
        var total = totalOrders();

        var second = json.readTree(send("GET", "/api/v1/orders?page=1&size=100", token, null).body());

        assertThat(second.get("totalElements").asLong()).isEqualTo(total);
        assertThat(second.get("totalPages").asInt()).isEqualTo((int) Math.ceil(total / 100.0));
        assertThat(second.get("content").size()).isEqualTo((int) Math.min(100, total - 100));
    }

    @Test
    void statusFilterAndSortAreApplied() throws Exception {
        var id = createOrder();
        patchStatus(id, "EM_PREPARO");

        var inPrep = json.readTree(send("GET", "/api/v1/orders?status=EM_PREPARO&size=500", token, null).body());
        var received = json.readTree(send("GET", "/api/v1/orders?status=RECEBIDO&size=500", token, null).body());

        assertThat(ids(inPrep)).contains(id);
        assertThat(ids(received)).doesNotContain(id);
        for (int i = 0; i < inPrep.get("content").size(); i++) {
            assertThat(inPrep.get("content").get(i).get("status").asString()).isEqualTo("EM_PREPARO");
        }

        service.create(cheaperRequest(), userId);
        var byTotal = json.readTree(send("GET", "/api/v1/orders?sort=totalCents,asc&size=500", token, null).body())
                .get("content");
        for (int i = 1; i < byTotal.size(); i++) {
            assertThat(byTotal.get(i).get("totalCents").asLong())
                    .isGreaterThanOrEqualTo(byTotal.get(i - 1).get("totalCents").asLong());
        }
    }

    @Test
    void defaultOrderIsNewestFirst() throws Exception {
        var older = createOrder();
        Thread.sleep(5);
        var newer = createOrder();

        var content = json.readTree(send("GET", "/api/v1/orders", token, null).body()).get("content");

        var ids = ids(json.readTree(send("GET", "/api/v1/orders", token, null).body()));
        assertThat(content.size()).isGreaterThanOrEqualTo(2);
        assertThat(ids.indexOf(newer)).isLessThan(ids.indexOf(older));
    }

    // ---- concorrência (7.7) ----

    @Test
    void anUpdateBasedOnAStaleVersionIsRejectedWithConcurrentModification() throws Exception {
        var id = createOrder();
        // Entre a leitura e a gravação do serviço, "outro operador" confirma uma alteração do mesmo pedido
        // (na mesma conexão, pois o pool do SQLite é de uma só): o UPDATE com a versão antiga não casa nenhuma linha.
        var armed = new java.util.concurrent.atomic.AtomicBoolean(true);
        doAnswer(call -> {
            var result = call.callRealMethod();
            var insideChangeStatus = java.util.Arrays.stream(Thread.currentThread().getStackTrace())
                    .anyMatch(frame -> frame.getMethodName().equals("changeStatus"));
            if (insideChangeStatus && armed.compareAndSet(true, false)) {
                jdbc.update("UPDATE orders SET version = version + 1 WHERE id = ?", id);
            }
            return result;
        }).when(clock).instant();

        var response = patchStatus(id, "EM_PREPARO");

        assertThat(response.statusCode()).isEqualTo(409);
        assertThat(json.readTree(response.body()).get("code").asString()).isEqualTo("CONCURRENT_MODIFICATION");
        var status = jdbc.queryForObject("SELECT status FROM orders WHERE id = ?", String.class, id);
        var historyCount = jdbc.queryForObject("SELECT COUNT(*) FROM order_status_history WHERE order_id = ?",
                Integer.class, id);
        assertThat(status).isEqualTo("RECEBIDO");
        assertThat(historyCount).isEqualTo(1);
    }

    @Test
    void twoSimultaneousIdenticalUpdatesApplyExactlyOnce() throws Exception {
        var id = createOrder();
        var start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var futures = new ArrayList<java.util.concurrent.Future<HttpResponse<String>>>();
            for (int i = 0; i < 2; i++) {
                futures.add(pool.submit(() -> {
                    start.await();
                    return patchStatus(id, "EM_PREPARO");
                }));
            }
            start.countDown();
            var codes = new ArrayList<Integer>();
            for (var future : futures) {
                var response = future.get();
                codes.add(response.statusCode());
                if (response.statusCode() == 409) {
                    assertThat(json.readTree(response.body()).get("code").asString())
                            .isIn("INVALID_STATUS_TRANSITION", "CONCURRENT_MODIFICATION");
                }
            }
            assertThat(codes).containsExactlyInAnyOrder(200, 409);
        }
        var historyCount = jdbc.queryForObject("SELECT COUNT(*) FROM order_status_history WHERE order_id = ?",
                Integer.class, id);
        assertThat(historyCount).isEqualTo(2);
    }

    // ---- autenticação e documentação ----

    @Test
    void ordersEndpointsNeedAToken() throws Exception {
        assertThat(send("GET", "/api/v1/orders", null, null).statusCode()).isEqualTo(401);
        assertThat(send("POST", "/api/v1/orders", null, OrderJson.valid()).statusCode()).isEqualTo(401);
        assertThat(send("PATCH", "/api/v1/orders/" + UUID.randomUUID() + "/status", null,
                "{\"status\":\"EM_PREPARO\"}").statusCode()).isEqualTo(401);
    }

    @Test
    void openApiDocumentsTheOrderEndpointsAndTheTransitionMatrix() throws Exception {
        var doc = json.readTree(send("GET", "/v3/api-docs", null, null).body());
        var paths = doc.get("paths");

        assertThat(paths.has("/api/v1/orders")).isTrue();
        assertThat(paths.has("/api/v1/orders/{id}")).isTrue();
        assertThat(paths.has("/api/v1/orders/{id}/status")).isTrue();
        assertThat(paths.get("/api/v1/orders").get("post").get("responses").has("201")).isTrue();
        assertThat(paths.get("/api/v1/orders").get("post").has("security")).isTrue();
        var patch = paths.get("/api/v1/orders/{id}/status").get("patch");
        var description = patch.get("description").asString();
        assertThat(description).contains("RECEBIDO", "EM_PREPARO", "SAIU_PARA_ENTREGA", "ENTREGUE", "CANCELADO",
                "INVALID_STATUS_TRANSITION", "CONCURRENT_MODIFICATION", "400", "409");
        assertThat(patch.get("responses").has("404")).isTrue();
        assertThat(patch.get("responses").has("409")).isTrue();
        var list = paths.get("/api/v1/orders").get("get");
        assertThat(list.get("description").asString()).contains("size=100", "500", "createdAt");
        var schemas = doc.get("components").get("schemas");
        assertThat(schemas.has("OrderResponse")).isTrue();
        assertThat(schemas.has("CreateOrderRequest")).isTrue();
    }

    // ---- helpers ----

    private CreateOrderRequest request() {
        return cheaperRequest();
    }

    private CreateOrderRequest cheaperRequest() {
        return new CreateOrderRequest(new com.fdl.platform.order.api.CustomerRequest("Maria Silva", "11987654321"),
                List.of(new com.fdl.platform.order.api.OrderItemRequest("Agua", 1, 500L)),
                new com.fdl.platform.order.api.AddressRequest("Rua A", "10", null, "Centro", "São Paulo", "SP",
                        "01001000"));
    }

    private String createOrder() throws Exception {
        var response = send("POST", "/api/v1/orders", token, OrderJson.valid());
        assertThat(response.statusCode()).isEqualTo(201);
        return json.readTree(response.body()).get("id").asString();
    }

    private long totalOrders() throws Exception {
        return json.readTree(send("GET", "/api/v1/orders?size=1", token, null).body()).get("totalElements").asLong();
    }

    private static List<String> ids(JsonNode page) {
        var ids = new ArrayList<String>();
        var content = page.get("content");
        for (int i = 0; i < content.size(); i++) {
            ids.add(content.get(i).get("id").asString());
        }
        return ids;
    }

    private HttpResponse<String> patchStatus(String id, String status) throws Exception {
        return send("PATCH", "/api/v1/orders/" + id + "/status", token, "{\"status\":\"" + status + "\"}");
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
