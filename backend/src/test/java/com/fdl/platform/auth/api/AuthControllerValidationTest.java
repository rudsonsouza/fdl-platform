package com.fdl.platform.auth.api;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fdl.platform.auth.service.AuthService;
import com.fdl.platform.shared.config.JacksonConfig;
import com.fdl.platform.support.WebSliceSecurityConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@WebMvcTest(AuthController.class)
@Import({WebSliceSecurityConfig.class, JacksonConfig.class})
class AuthControllerValidationTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    AuthService auth;

    private ResultActions send(String path, String json) throws Exception {
        return mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private static String register(String name, String email, String password) {
        return "{\"name\":" + quote(name) + ",\"email\":" + quote(email) + ",\"password\":" + quote(password) + "}";
    }

    private static String quote(String value) {
        return "\"" + value + "\"";
    }

    // ---- cadastro ----

    @Test
    void emptyBodyListsNameEmailAndPassword() throws Exception {
        send("/api/v1/auth/register", "{}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[?(@.field=='name')]").exists())
                .andExpect(jsonPath("$.errors[?(@.field=='email')]").exists())
                .andExpect(jsonPath("$.errors[?(@.field=='password')]").exists());
        verifyNoInteractions(auth);
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc", "ana@", "@exemplo.com", "ana@exemplo", "ana exemplo@x.com", ""})
    void invalidEmailIsRejected(String email) throws Exception {
        send("/api/v1/auth/register", register("Ana Souza", email, "segredo123"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[?(@.field=='email')]").exists());
        verifyNoInteractions(auth);
    }

    @Test
    void emailLongerThan254CharactersIsRejected() throws Exception {
        var email = "a".repeat(250) + "@x.com";

        send("/api/v1/auth/register", register("Ana Souza", email, "segredo123"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[?(@.field=='email')]").exists());
        verifyNoInteractions(auth);
    }

    @ParameterizedTest
    @CsvSource({"1234567", "abcdefgh", "12345678", "abc1", "ab cd efg"})
    void weakOrShortPasswordIsRejected(String password) throws Exception {
        send("/api/v1/auth/register", register("Ana Souza", "ana@exemplo.com", password))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[?(@.field=='password')]").exists());
        verifyNoInteractions(auth);
    }

    @Test
    void passwordWith73CharactersIsRejected() throws Exception {
        send("/api/v1/auth/register", register("Ana Souza", "ana@exemplo.com", "a1" + "x".repeat(71)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[?(@.field=='password')]").exists());
        verifyNoInteractions(auth);
    }

    @Test
    void passwordUnder72CharactersButOver72BytesIsRejected() throws Exception {
        // 40 caracteres "é" (2 bytes cada) + dígito = 81 bytes: o BCrypt truncaria em 72.
        send("/api/v1/auth/register", register("Ana Souza", "ana@exemplo.com", "é".repeat(40) + "1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[?(@.field=='password')]").exists());
        verifyNoInteractions(auth);
    }

    @ParameterizedTest
    @ValueSource(strings = {"   ", "A", ""})
    void blankOrTooShortNameIsRejected(String name) throws Exception {
        send("/api/v1/auth/register", register(name, "ana@exemplo.com", "segredo123"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[?(@.field=='name')]").exists());
        verifyNoInteractions(auth);
    }

    @Test
    void nameWith101CharactersIsRejected() throws Exception {
        send("/api/v1/auth/register", register("n".repeat(101), "ana@exemplo.com", "segredo123"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[?(@.field=='name')]").exists());
        verifyNoInteractions(auth);
    }

    @Test
    void unknownFieldIsMalformedRequest() throws Exception {
        send("/api/v1/auth/register",
                "{\"name\":\"Ana Souza\",\"email\":\"ana@exemplo.com\",\"password\":\"segredo123\",\"role\":\"ADMIN\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
        verifyNoInteractions(auth);
    }

    @ParameterizedTest
    @ValueSource(strings = {"not json", "{\"name\":", "[]", ""})
    void malformedJsonIsMalformedRequest(String body) throws Exception {
        send("/api/v1/auth/register", body)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
        verifyNoInteractions(auth);
    }

    @Test
    void nonJsonContentTypeIs415() throws Exception {
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.TEXT_PLAIN).content("x"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"));
        verifyNoInteractions(auth);
    }

    // ---- login ----

    @Test
    void loginWithoutPasswordNamesThePasswordField() throws Exception {
        send("/api/v1/auth/login", "{\"email\":\"ana@exemplo.com\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[?(@.field=='password')]").exists());
        verifyNoInteractions(auth);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ana@", "abc", ""})
    void loginWithInvalidEmailNamesTheEmailField(String email) throws Exception {
        send("/api/v1/auth/login", "{\"email\":" + quote(email) + ",\"password\":\"segredo123\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[?(@.field=='email')]").exists());
        verifyNoInteractions(auth);
    }

    @Test
    void loginWithEmptyPasswordIsRejected() throws Exception {
        send("/api/v1/auth/login", "{\"email\":\"ana@exemplo.com\",\"password\":\"\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[?(@.field=='password')]").exists());
        verifyNoInteractions(auth);
    }

    @Test
    void loginWithMalformedJsonIsMalformedRequest() throws Exception {
        send("/api/v1/auth/login", "{oops")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
        verifyNoInteractions(auth);
    }
}
