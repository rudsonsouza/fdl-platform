package com.fdl.platform.support;

import com.fdl.platform.order.domain.InvalidStatusTransitionException;
import com.fdl.platform.order.domain.OrderNotFoundException;
import com.fdl.platform.order.domain.OrderStatus;
import com.fdl.platform.user.domain.EmailAlreadyRegisteredException;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Valid;
import jakarta.validation.Validation;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Controller só de teste: expõe cada caminho de erro para os testes de Problem Details e Jackson. */
@RestController
@RequestMapping("/probe")
public class ProbeController {

    public enum Kind { A, B }

    public record Item(@NotBlank String name, @Min(1) @Max(99) int quantity, @NotNull Kind kind) {
    }

    public record Wrapper(@Valid @NotEmpty List<@Valid Item> items) {
    }

    @PostMapping(value = "/items", consumes = MediaType.APPLICATION_JSON_VALUE)
    Wrapper items(@Valid @RequestBody Wrapper body) {
        return body;
    }

    @GetMapping("/page")
    String page(@RequestParam @Min(1) @Max(500) int size) {
        return "size=" + size;
    }

    @GetMapping("/required")
    String required(@RequestParam String status) {
        return status;
    }

    @GetMapping("/uuid/{id}")
    String uuid(@PathVariable UUID id) {
        return id.toString();
    }

    @GetMapping("/throw/{kind}")
    String fail(@PathVariable String kind) {
        switch (kind) {
            case "order-not-found" -> throw new OrderNotFoundException(UUID.fromString("00000000-0000-0000-0000-000000000001"));
            case "email" -> throw new EmailAlreadyRegisteredException();
            case "transition" -> throw new InvalidStatusTransitionException(OrderStatus.SAIU_PARA_ENTREGA, OrderStatus.CANCELADO);
            case "concurrent" -> throw new OptimisticLockingFailureException("versão antiga");
            case "credentials" -> throw new BadCredentialsException("senha errada");
            case "unauthenticated" -> throw new InsufficientAuthenticationException("sem token");
            case "denied" -> throw new AccessDeniedException("negado");
            case "constraint" -> throw new ConstraintViolationException(Validation.buildDefaultValidatorFactory()
                    .getValidator().validate(new Item("", 0, null)));
            default -> throw new IllegalStateException("segredo interno: jdbc:sqlite:/data/fdl.db");
        }
    }
}
