package com.fdl.platform.auth.api;

import com.fdl.platform.auth.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/api/v1/auth", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Autenticação", description = "Cadastro, login e consulta do usuário autenticado")
public class AuthController {

    private static final String PROBLEM = "application/problem+json";

    private final AuthService auth;

    public AuthController(AuthService auth) {
        this.auth = auth;
    }

    @PostMapping(path = "/register", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Cadastra um operador",
            description = "O e-mail é único sem diferenciar maiúsculas de minúsculas e é salvo em minúsculas. "
                    + "A senha é guardada apenas como hash e nunca volta nas respostas.")
    @ApiResponse(responseCode = "201", description = "Usuário criado")
    @ApiResponse(responseCode = "400", description = "Payload inválido (`VALIDATION_ERROR` ou `MALFORMED_REQUEST`)",
            content = @Content(mediaType = PROBLEM, schema = @Schema(ref = "#/components/schemas/ProblemDetail")))
    @ApiResponse(responseCode = "409", description = "E-mail já cadastrado (`EMAIL_ALREADY_REGISTERED`)",
            content = @Content(mediaType = PROBLEM, schema = @Schema(ref = "#/components/schemas/ProblemDetail")))
    public UserResponse register(@Valid @RequestBody RegisterRequest request) {
        return UserResponse.from(auth.register(request.name(), request.email(), request.password()));
    }

    @PostMapping(path = "/login", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Autentica por e-mail e senha",
            description = "Retorna um token Bearer (validade padrão de 60 minutos). E-mail inexistente e senha "
                    + "incorreta recebem a mesma resposta `401 INVALID_CREDENTIALS`.")
    @ApiResponse(responseCode = "200", description = "Token emitido")
    @ApiResponse(responseCode = "400", description = "Payload inválido",
            content = @Content(mediaType = PROBLEM, schema = @Schema(ref = "#/components/schemas/ProblemDetail")))
    @ApiResponse(responseCode = "401", description = "Credenciais inválidas (`INVALID_CREDENTIALS`)",
            content = @Content(mediaType = PROBLEM, schema = @Schema(ref = "#/components/schemas/ProblemDetail")))
    public TokenResponse login(@Valid @RequestBody LoginRequest request) {
        var token = auth.login(request.email(), request.password());
        return new TokenResponse(token.value(), "Bearer", token.expiresInSeconds());
    }

    @GetMapping("/me")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Retorna o usuário dono do token")
    @ApiResponse(responseCode = "200", description = "Usuário autenticado")
    @ApiResponse(responseCode = "401", description = "Token ausente, expirado ou inválido (`UNAUTHENTICATED`)",
            content = @Content(mediaType = PROBLEM, schema = @Schema(ref = "#/components/schemas/ProblemDetail")))
    public UserResponse me(@AuthenticationPrincipal Jwt jwt) {
        return UserResponse.from(auth.currentUser(UUID.fromString(jwt.getSubject())));
    }
}
