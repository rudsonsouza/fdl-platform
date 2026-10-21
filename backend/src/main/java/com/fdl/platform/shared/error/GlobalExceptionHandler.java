package com.fdl.platform.shared.error;

import com.fdl.platform.order.domain.InvalidStatusTransitionException;
import com.fdl.platform.order.domain.OrderNotFoundException;
import com.fdl.platform.shared.web.TraceIdFilter;
import com.fdl.platform.user.domain.EmailAlreadyRegisteredException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.validation.method.ParameterErrors;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DatabindException;

/**
 * Traduz toda exceção da API em Problem Details (RFC 9457) com {@code code} estável,
 * {@code traceId} e, na validação, a lista {@code errors[]}. Erros 500 nunca expõem a mensagem
 * da exceção nem a pilha: o detalhe vai só para o log, junto do {@code traceId}.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // ---- Requisições inválidas (400) ----

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        var errors = new ArrayList<FieldErrorDetail>();
        for (ObjectError error : ex.getBindingResult().getAllErrors()) {
            var field = error instanceof FieldError fieldError ? fieldError.getField() : error.getObjectName();
            errors.add(new FieldErrorDetail(field, error.getDefaultMessage()));
        }
        return respond(ErrorCode.VALIDATION_ERROR, "Um ou mais campos são inválidos.", errors, headers, request);
    }

    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(HandlerMethodValidationException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        var errors = new ArrayList<FieldErrorDetail>();
        ex.getParameterValidationResults().forEach(result -> {
            if (result instanceof ParameterErrors parameterErrors) {
                parameterErrors.getFieldErrors()
                        .forEach(e -> errors.add(new FieldErrorDetail(e.getField(), e.getDefaultMessage())));
            } else {
                var name = result.getMethodParameter().getParameterName();
                result.getResolvableErrors()
                        .forEach(e -> errors.add(new FieldErrorDetail(name, e.getDefaultMessage())));
            }
        });
        return respond(ErrorCode.VALIDATION_ERROR, "Um ou mais parâmetros são inválidos.", errors, headers, request);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<Object> handleConstraintViolation(ConstraintViolationException ex, WebRequest request) {
        var errors = ex.getConstraintViolations().stream()
                .map(v -> new FieldErrorDetail(lastNode(v.getPropertyPath().toString()), v.getMessage()))
                .toList();
        return respond(ErrorCode.VALIDATION_ERROR, "Um ou mais parâmetros são inválidos.", errors,
                new HttpHeaders(), request);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        var errors = new ArrayList<FieldErrorDetail>();
        if (ex.getCause() instanceof DatabindException jackson && !jackson.getPath().isEmpty()) {
            errors.add(new FieldErrorDetail(jsonPath(jackson), "Valor inválido, ausente ou não reconhecido."));
        }
        return respond(ErrorCode.MALFORMED_REQUEST,
                "O corpo da requisição está malformado ou contém campos ou valores inválidos.", errors, headers,
                request);
    }

    @Override
    protected ResponseEntity<Object> handleTypeMismatch(org.springframework.beans.TypeMismatchException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        var name = ex instanceof MethodArgumentTypeMismatchException mismatch ? mismatch.getName() : ex.getPropertyName();
        return respond(ErrorCode.INVALID_PARAMETER, "O valor informado para '%s' é inválido.".formatted(name),
                List.of(new FieldErrorDetail(name, "Valor inválido")), headers, request);
    }

    @Override
    protected ResponseEntity<Object> handleMissingServletRequestParameter(MissingServletRequestParameterException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return respond(ErrorCode.INVALID_PARAMETER, "O parâmetro '%s' é obrigatório.".formatted(ex.getParameterName()),
                List.of(new FieldErrorDetail(ex.getParameterName(), "Parâmetro obrigatório")), headers, request);
    }

    // ---- Protocolo HTTP ----

    @Override
    protected ResponseEntity<Object> handleHttpMediaTypeNotSupported(HttpMediaTypeNotSupportedException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return respond(ErrorCode.UNSUPPORTED_MEDIA_TYPE, "Use Content-Type: application/json.", List.of(),
                ex.getHeaders(), request);
    }

    @Override
    protected ResponseEntity<Object> handleHttpRequestMethodNotSupported(HttpRequestMethodNotSupportedException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return respond(ErrorCode.METHOD_NOT_ALLOWED, "Método %s não é permitido neste recurso.".formatted(ex.getMethod()),
                List.of(), ex.getHeaders(), request);
    }

    @Override
    protected ResponseEntity<Object> handleNoResourceFoundException(NoResourceFoundException ex, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {
        return respond(ErrorCode.NOT_FOUND, "O recurso solicitado não existe.", List.of(), headers, request);
    }

    // ---- Autenticação e autorização ----

    @ExceptionHandler(BadCredentialsException.class)
    ResponseEntity<Object> handleBadCredentials(BadCredentialsException ex, WebRequest request) {
        // Só o fato da falha: nunca o e-mail, a senha ou o token recebidos.
        log.debug("Login rejeitado por credenciais inválidas");
        return respond(ErrorCode.INVALID_CREDENTIALS, "E-mail ou senha inválidos.", List.of(), new HttpHeaders(),
                request);
    }

    @ExceptionHandler(AuthenticationException.class)
    ResponseEntity<Object> handleAuthentication(AuthenticationException ex, WebRequest request) {
        var headers = new HttpHeaders();
        headers.set(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        return respond(ErrorCode.UNAUTHENTICATED, "Autenticação necessária ou token inválido.", List.of(), headers,
                request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<Object> handleAccessDenied(AccessDeniedException ex, WebRequest request) {
        return respond(ErrorCode.FORBIDDEN, "Você não tem permissão para acessar este recurso.", List.of(),
                new HttpHeaders(), request);
    }

    // ---- Regras de negócio ----

    @ExceptionHandler(OrderNotFoundException.class)
    ResponseEntity<Object> handleOrderNotFound(OrderNotFoundException ex, WebRequest request) {
        return respond(ErrorCode.ORDER_NOT_FOUND, ex.getMessage(), List.of(), new HttpHeaders(), request);
    }

    @ExceptionHandler(EmailAlreadyRegisteredException.class)
    ResponseEntity<Object> handleEmailAlreadyRegistered(EmailAlreadyRegisteredException ex, WebRequest request) {
        return respond(ErrorCode.EMAIL_ALREADY_REGISTERED, ex.getMessage(), List.of(), new HttpHeaders(), request);
    }

    @ExceptionHandler(InvalidStatusTransitionException.class)
    ResponseEntity<Object> handleInvalidTransition(InvalidStatusTransitionException ex, WebRequest request) {
        var problem = problem(ErrorCode.INVALID_STATUS_TRANSITION, ex.getMessage(), List.of(), request);
        problem.setProperty("currentStatus", ex.currentStatus());
        problem.setProperty("requestedStatus", ex.requestedStatus());
        problem.setProperty("allowedTransitions", ex.allowedTransitions());
        return ResponseEntity.status(problem.getStatus()).body(problem);
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    ResponseEntity<Object> handleConcurrentModification(OptimisticLockingFailureException ex, WebRequest request) {
        return respond(ErrorCode.CONCURRENT_MODIFICATION,
                "O pedido foi alterado por outro usuário. Consulte-o novamente e repita a operação.", List.of(),
                new HttpHeaders(), request);
    }

    // ---- Qualquer outra falha ----

    @ExceptionHandler(Exception.class)
    ResponseEntity<Object> handleUnexpected(Exception ex, HttpServletRequest servletRequest, WebRequest request) {
        log.error("Erro inesperado em {} {}", servletRequest.getMethod(), servletRequest.getRequestURI(), ex);
        return respond(ErrorCode.INTERNAL_ERROR, "Ocorreu um erro inesperado. Informe o traceId ao suporte.",
                List.of(), new HttpHeaders(), request);
    }

    /** Respostas montadas pelo Spring (ex.: 406) também ganham code, type e traceId. */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
            HttpStatusCode statusCode, WebRequest request) {
        if (body instanceof ProblemDetail problem
                && (problem.getProperties() == null || !problem.getProperties().containsKey("code"))) {
            var code = statusCode.is4xxClientError() ? ErrorCode.MALFORMED_REQUEST : ErrorCode.INTERNAL_ERROR;
            problem.setType(ProblemFactory.typeOf(code));
            problem.setProperty("code", code.name());
            problem.setProperty("traceId", MDC.get(TraceIdFilter.MDC_KEY));
        }
        return super.handleExceptionInternal(ex, body, headers, statusCode, request);
    }

    // ---- Montagem da resposta ----

    private ResponseEntity<Object> respond(ErrorCode code, String detail, List<FieldErrorDetail> errors,
            HttpHeaders headers, WebRequest request) {
        var problem = problem(code, detail, errors, request);
        return ResponseEntity.status(code.status()).headers(headers).body(problem);
    }

    private ProblemDetail problem(ErrorCode code, String detail, List<FieldErrorDetail> errors, WebRequest request) {
        var instance = request instanceof ServletWebRequest servletRequest ? servletRequest.getRequest().getRequestURI()
                : null;
        return ProblemFactory.create(code, detail, errors, instance);
    }

    private static String jsonPath(JacksonException exception) {
        var path = new StringBuilder();
        for (var reference : exception.getPath()) {
            if (reference.getPropertyName() != null) {
                if (!path.isEmpty()) {
                    path.append('.');
                }
                path.append(reference.getPropertyName());
            } else if (reference.getIndex() >= 0) {
                path.append('[').append(reference.getIndex()).append(']');
            }
        }
        return path.toString();
    }

    private static String lastNode(String propertyPath) {
        var dot = propertyPath.lastIndexOf('.');
        return dot < 0 ? propertyPath : propertyPath.substring(dot + 1);
    }
}
