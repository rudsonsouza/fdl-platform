package com.fdl.platform.shared.error;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * Os erros de segurança acontecem dentro da cadeia de filtros, antes de qualquer controller, e por
 * isso não passam pelo {@link GlobalExceptionHandler}. Este componente escreve o mesmo Problem Details.
 */
@Component
public class ProblemDetailsSecurityHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final JsonMapper mapper;

    public ProblemDetailsSecurityHandler(JsonMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException exception) throws IOException {
        response.setHeader("WWW-Authenticate", "Bearer");
        write(request, response, ErrorCode.UNAUTHENTICATED,
                "Autenticação necessária: envie um token válido em Authorization: Bearer.");
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
            AccessDeniedException exception) throws IOException {
        write(request, response, ErrorCode.FORBIDDEN, "Você não tem permissão para acessar este recurso.");
    }

    private void write(HttpServletRequest request, HttpServletResponse response, ErrorCode code, String detail)
            throws IOException {
        var problem = ProblemFactory.create(code, detail, List.of(), request.getRequestURI());
        response.setStatus(code.status().value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(mapper.writeValueAsString(problem));
    }
}
