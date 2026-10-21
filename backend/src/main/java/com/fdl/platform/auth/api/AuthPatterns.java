package com.fdl.platform.auth.api;

final class AuthPatterns {

    /** Exige texto antes e depois do "@" e um ponto no domínio ("ana@" e "ana@host" são inválidos). */
    static final String EMAIL = "^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$";

    private AuthPatterns() {
    }
}
