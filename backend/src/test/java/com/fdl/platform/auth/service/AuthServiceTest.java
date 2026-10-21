package com.fdl.platform.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fdl.platform.support.Users;
import com.fdl.platform.user.domain.EmailAlreadyRegisteredException;
import com.fdl.platform.user.domain.User;
import com.fdl.platform.user.repository.UserRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

class AuthServiceTest {

    UserRepository users = mock(UserRepository.class);
    PasswordEncoder encoder = new BCryptPasswordEncoder(4);
    JwtTokenService tokens = mock(JwtTokenService.class);
    AuthService service;

    @BeforeEach
    void setUp() {
        service = new AuthService(users, encoder, tokens, Users.CLOCK);
        when(users.save(any(User.class))).thenAnswer(call -> call.getArgument(0));
    }

    @Test
    void registerNormalizesTheEmailAndStoresOnlyAHash() {
        var created = service.register("  Ana Souza ", " ANA@Exemplo.com ", "segredo123");

        var saved = ArgumentCaptor.forClass(User.class);
        verify(users).save(saved.capture());
        assertThat(saved.getValue()).isSameAs(created);
        assertThat(created.getEmail()).isEqualTo("ana@exemplo.com");
        assertThat(created.getName()).isEqualTo("Ana Souza");
        assertThat(created.getPasswordHash()).isNotEqualTo("segredo123").startsWith("$2");
        assertThat(encoder.matches("segredo123", created.getPasswordHash())).isTrue();
    }

    @Test
    void registerRejectsAnExistingEmailRegardlessOfCase() {
        when(users.existsByEmailIgnoreCase("ana@exemplo.com")).thenReturn(true);

        assertThatThrownBy(() -> service.register("Ana", "ANA@exemplo.com", "segredo123"))
                .isInstanceOf(EmailAlreadyRegisteredException.class);
        verify(users, never()).save(any());
    }

    @Test
    void loginIssuesATokenForValidCredentials() {
        var user = User.create("Ana", "ana@exemplo.com", encoder.encode("segredo123"), Users.CLOCK);
        var issued = new JwtTokenService.IssuedToken("token", 3600);
        when(users.findByEmailIgnoreCase("ana@exemplo.com")).thenReturn(Optional.of(user));
        when(tokens.issue(user)).thenReturn(issued);

        assertThat(service.login("ANA@exemplo.com", "segredo123")).isSameAs(issued);
    }

    @Test
    void wrongPasswordAndUnknownEmailFailTheSameWay() {
        var user = User.create("Ana", "ana@exemplo.com", encoder.encode("segredo123"), Users.CLOCK);
        when(users.findByEmailIgnoreCase("ana@exemplo.com")).thenReturn(Optional.of(user));
        when(users.findByEmailIgnoreCase("ninguem@exemplo.com")).thenReturn(Optional.empty());

        var wrongPassword = catchThrowable(() -> service.login("ana@exemplo.com", "errada123"));
        var unknownEmail = catchThrowable(() -> service.login("ninguem@exemplo.com", "segredo123"));

        assertThat(wrongPassword).isInstanceOf(BadCredentialsException.class);
        assertThat(unknownEmail).isInstanceOf(BadCredentialsException.class)
                .hasMessage(wrongPassword.getMessage());
        verify(tokens, never()).issue(any());
    }

    @Test
    void loginForAnUnknownEmailStillVerifiesAPassword() {
        var spy = org.mockito.Mockito.spy(encoder);
        var guarded = new AuthService(users, spy, tokens, Users.CLOCK);
        org.mockito.Mockito.clearInvocations(spy);
        when(users.findByEmailIgnoreCase("ninguem@exemplo.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> guarded.login("ninguem@exemplo.com", "qualquer123"))
                .isInstanceOf(BadCredentialsException.class);

        verify(spy).matches(any(), any());
    }

    @Test
    void currentUserFailsAuthenticationWhenTheUserNoLongerExists() {
        var id = UUID.randomUUID();
        when(users.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.currentUser(id)).isInstanceOf(InsufficientAuthenticationException.class);
    }

    private static Throwable catchThrowable(org.assertj.core.api.ThrowableAssert.ThrowingCallable callable) {
        return org.assertj.core.api.Assertions.catchThrowable(callable);
    }
}
