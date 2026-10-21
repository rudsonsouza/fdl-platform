package com.fdl.platform.shared.error;

import org.springframework.http.HttpStatus;

/** Códigos estáveis de erro da API, com o status HTTP e o título de cada um. */
public enum ErrorCode {
    VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "Dados inválidos"),
    MALFORMED_REQUEST(HttpStatus.BAD_REQUEST, "Requisição malformada"),
    INVALID_PARAMETER(HttpStatus.BAD_REQUEST, "Parâmetro inválido"),
    UNAUTHENTICATED(HttpStatus.UNAUTHORIZED, "Autenticação necessária"),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "Credenciais inválidas"),
    FORBIDDEN(HttpStatus.FORBIDDEN, "Acesso negado"),
    NOT_FOUND(HttpStatus.NOT_FOUND, "Recurso não encontrado"),
    ORDER_NOT_FOUND(HttpStatus.NOT_FOUND, "Pedido não encontrado"),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "Método não permitido"),
    UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Tipo de conteúdo não suportado"),
    EMAIL_ALREADY_REGISTERED(HttpStatus.CONFLICT, "E-mail já cadastrado"),
    INVALID_STATUS_TRANSITION(HttpStatus.CONFLICT, "Transição de status inválida"),
    CONCURRENT_MODIFICATION(HttpStatus.CONFLICT, "Pedido alterado por outro usuário"),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Erro interno");

    private final HttpStatus status;
    private final String title;

    ErrorCode(HttpStatus status, String title) {
        this.status = status;
        this.title = title;
    }

    public HttpStatus status() {
        return status;
    }

    public String title() {
        return title;
    }
}
