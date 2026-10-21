package com.fdl.platform.order.api;

import com.fdl.platform.order.domain.OrderStatus;
import com.fdl.platform.order.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import java.net.URI;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/api/v1/orders", produces = MediaType.APPLICATION_JSON_VALUE)
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Pedidos", description = "Criação, listagem, consulta e atualização de status dos pedidos")
public class OrderController {

    private static final String PROBLEM = "application/problem+json";
    private static final String SORT_PATTERN = "(createdAt|updatedAt|status|totalCents)(,(asc|desc))?";

    private final OrderService orders;

    public OrderController(OrderService orders) {
        this.orders = orders;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Cria um pedido",
            description = "O pedido nasce em `RECEBIDO`. O total é calculado pelo servidor (soma de quantidade × preço "
                    + "unitário); enviar `id`, `status` ou `totalCents` resulta em `400 MALFORMED_REQUEST`.")
    @ApiResponse(responseCode = "201", description = "Pedido criado; `Location` aponta para o novo recurso")
    @ApiResponse(responseCode = "400", description = "Payload inválido (`VALIDATION_ERROR` ou `MALFORMED_REQUEST`)",
            content = @Content(mediaType = PROBLEM, schema = @Schema(ref = "#/components/schemas/ProblemDetail")))
    @ApiResponse(responseCode = "401", description = "Token ausente, expirado ou inválido (`UNAUTHENTICATED`)",
            content = @Content(mediaType = PROBLEM, schema = @Schema(ref = "#/components/schemas/ProblemDetail")))
    public ResponseEntity<OrderResponse> create(@Valid @RequestBody CreateOrderRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        var created = orders.create(request, userId(jwt));
        return ResponseEntity.created(URI.create("/api/v1/orders/" + created.id())).body(created);
    }

    @GetMapping
    @Operation(summary = "Lista os pedidos",
            description = "Todos os pedidos, paginados. Padrão: `page=0`, `size=100` (máximo 500), "
                    + "`sort=createdAt,desc`. Campos de ordenação aceitos: `createdAt`, `updatedAt`, `status`, "
                    + "`totalCents`.")
    @ApiResponse(responseCode = "200", description = "Página de pedidos")
    @ApiResponse(responseCode = "400", description = "Parâmetro inválido (`INVALID_PARAMETER` ou `VALIDATION_ERROR`)",
            content = @Content(mediaType = PROBLEM, schema = @Schema(ref = "#/components/schemas/ProblemDetail")))
    @ApiResponse(responseCode = "401", description = "Token ausente, expirado ou inválido (`UNAUTHENTICATED`)",
            content = @Content(mediaType = PROBLEM, schema = @Schema(ref = "#/components/schemas/ProblemDetail")))
    public PageResponse<OrderSummaryResponse> list(
            @Parameter(description = "Filtra pelo status atual") @RequestParam(required = false) OrderStatus status,
            @RequestParam(defaultValue = "0") @Min(value = 0, message = "deve ser no mínimo 0") int page,
            @RequestParam(defaultValue = "100") @Min(value = 1, message = "deve ser no mínimo 1")
            @Max(value = 500, message = "deve ser no máximo 500") int size,
            @Parameter(example = "createdAt,desc")
            @RequestParam(defaultValue = "createdAt,desc")
            @Pattern(regexp = SORT_PATTERN, message = "use campo[,asc|desc] com campo em createdAt, updatedAt, status ou totalCents") String sort) {
        return orders.findAll(status, PageRequest.of(page, size, toSort(sort)));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Consulta um pedido",
            description = "Pedido completo, com itens, endereço, `history` (ordem cronológica) e `allowedTransitions`.")
    @ApiResponse(responseCode = "200", description = "Pedido encontrado")
    @ApiResponse(responseCode = "400", description = "`id` não é um UUID (`INVALID_PARAMETER`)",
            content = @Content(mediaType = PROBLEM, schema = @Schema(ref = "#/components/schemas/ProblemDetail")))
    @ApiResponse(responseCode = "404", description = "Pedido inexistente (`ORDER_NOT_FOUND`)",
            content = @Content(mediaType = PROBLEM, schema = @Schema(ref = "#/components/schemas/ProblemDetail")))
    public OrderResponse get(@PathVariable UUID id) {
        return orders.findById(id);
    }

    @PatchMapping(path = "/{id}/status", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Atualiza o status de um pedido",
            description = """
                    Transições permitidas:

                    | De | Para |
                    | --- | --- |
                    | `RECEBIDO` | `EM_PREPARO`, `CANCELADO` |
                    | `EM_PREPARO` | `SAIU_PARA_ENTREGA`, `CANCELADO` |
                    | `SAIU_PARA_ENTREGA` | `ENTREGUE` |
                    | `ENTREGUE`, `CANCELADO` | (estados finais, nenhuma) |

                    **400** — `status` ausente, nulo, fora da lista, em minúsculas, não textual, ou `id` que não é UUID \
                    (`VALIDATION_ERROR`, `MALFORMED_REQUEST` ou `INVALID_PARAMETER`); o pedido não é alterado.

                    **409** — `INVALID_STATUS_TRANSITION` quando a transição não é permitida (inclui o mesmo estado, \
                    pular ou voltar etapa e sair de estado final; o corpo traz `currentStatus`, `requestedStatus` e \
                    `allowedTransitions`), ou `CONCURRENT_MODIFICATION` quando outro operador alterou o pedido ao \
                    mesmo tempo. Em ambos o pedido não é alterado e nenhuma entrada de histórico é criada.""")
    @ApiResponse(responseCode = "200", description = "Pedido atualizado")
    @ApiResponse(responseCode = "400", description = "Payload ou `id` inválido",
            content = @Content(mediaType = PROBLEM, schema = @Schema(ref = "#/components/schemas/ProblemDetail")))
    @ApiResponse(responseCode = "404", description = "Pedido inexistente (`ORDER_NOT_FOUND`)",
            content = @Content(mediaType = PROBLEM, schema = @Schema(ref = "#/components/schemas/ProblemDetail")))
    @ApiResponse(responseCode = "409", description = "Transição inválida ou alteração concorrente",
            content = @Content(mediaType = PROBLEM, schema = @Schema(ref = "#/components/schemas/ProblemDetail")))
    public OrderResponse updateStatus(@PathVariable UUID id, @Valid @RequestBody UpdateStatusRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return orders.updateStatus(id, request.status(), userId(jwt));
    }

    private static UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }

    /** Desempate por {@code id} mantém a paginação estável quando o campo escolhido se repete. */
    private static Sort toSort(String sort) {
        var parts = sort.split(",");
        var direction = parts.length > 1 && parts[1].equals("asc") ? Sort.Direction.ASC : Sort.Direction.DESC;
        return Sort.by(direction, parts[0]).and(Sort.by(Sort.Direction.ASC, "id"));
    }
}
