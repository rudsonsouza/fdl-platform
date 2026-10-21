package com.fdl.platform.auth.api;

import com.fdl.platform.user.domain.User;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@Schema(description = "Dados públicos do usuário; nunca inclui senha nem hash")
public record UserResponse(UUID id, String name, @Schema(example = "ana@exemplo.com") String email) {

    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getName(), user.getEmail());
    }
}
