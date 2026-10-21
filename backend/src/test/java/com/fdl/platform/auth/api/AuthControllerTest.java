package com.fdl.platform.auth.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fdl.platform.auth.service.AuthService;
import com.fdl.platform.auth.service.JwtTokenService.IssuedToken;
import com.fdl.platform.shared.config.JacksonConfig;
import com.fdl.platform.support.Users;
import com.fdl.platform.support.WebSliceSecurityConfig;
import com.fdl.platform.user.domain.EmailAlreadyRegisteredException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AuthController.class)
@Import({WebSliceSecurityConfig.class, JacksonConfig.class})
class AuthControllerTest {

    private static final String REGISTER = "{\"name\":\"Ana Souza\",\"email\":\"ANA@exemplo.com\",\"password\":\"segredo123\"}";
    private static final String LOGIN = "{\"email\":\"ana@exemplo.com\",\"password\":\"segredo123\"}";

    @Autowired
    MockMvc mvc;

    @MockitoBean
    AuthService auth;

    @Test
    void registerReturns201WithTheUserAndNoPasswordFields() throws Exception {
        var user = Users.ana();
        when(auth.register("Ana Souza", "ANA@exemplo.com", "segredo123")).thenReturn(user);

        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(REGISTER))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(user.getId().toString()))
                .andExpect(jsonPath("$.name").value("Ana Souza"))
                .andExpect(jsonPath("$.email").value("ana@exemplo.com"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("hash"))));
    }

    @Test
    void registerWithAnExistingEmailIs409() throws Exception {
        when(auth.register(any(), any(), any())).thenThrow(new EmailAlreadyRegisteredException());

        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(REGISTER))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_REGISTERED"));
    }

    @Test
    void loginReturnsTheBearerToken() throws Exception {
        when(auth.login("ana@exemplo.com", "segredo123")).thenReturn(new IssuedToken("abc.def.ghi", 3600));

        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(LOGIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("abc.def.ghi"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(3600));
    }

    @Test
    void loginWithBadCredentialsIs401WithAGenericCode() throws Exception {
        when(auth.login(any(), any())).thenThrow(new BadCredentialsException("x"));

        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(LOGIN))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
                .andExpect(jsonPath("$.detail").value("E-mail ou senha inválidos."));
    }

    @Test
    void meReturnsTheOwnerOfTheToken() throws Exception {
        var user = Users.ana();
        when(auth.currentUser(user.getId())).thenReturn(user);

        mvc.perform(get("/api/v1/auth/me").with(jwt().jwt(j -> j.subject(user.getId().toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(user.getId().toString()))
                .andExpect(jsonPath("$.email").value("ana@exemplo.com"));
        verify(auth).currentUser(user.getId());
    }

    @Test
    void meWithoutATokenIs401InTheStandardFormat() throws Exception {
        mvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", org.hamcrest.Matchers.startsWith("Bearer")))
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }
}
