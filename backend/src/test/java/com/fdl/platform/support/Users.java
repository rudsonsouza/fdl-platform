package com.fdl.platform.support;

import com.fdl.platform.user.domain.User;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

/** Fábrica de usuários e relógio fixo para os testes. */
public final class Users {

    public static final Clock CLOCK = Clock.fixed(Instant.parse("2026-01-01T10:00:00Z"), ZoneOffset.UTC);

    private Users() {
    }

    public static User ana() {
        return User.create("Ana Souza", "ana@exemplo.com", "hash", CLOCK);
    }
}
