package com.fdl.platform.user.domain;

public class EmailAlreadyRegisteredException extends RuntimeException {

    public EmailAlreadyRegisteredException() {
        super("Já existe um usuário cadastrado com este e-mail");
    }
}
