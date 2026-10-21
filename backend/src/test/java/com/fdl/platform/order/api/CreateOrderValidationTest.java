package com.fdl.platform.order.api;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fdl.platform.order.service.OrderService;
import com.fdl.platform.shared.config.JacksonConfig;
import com.fdl.platform.support.OrderJson;
import com.fdl.platform.support.WebSliceSecurityConfig;
import java.util.UUID;
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

/** Todos os cenários 400 (e 415) de criação de pedido; nenhum deles pode chegar ao serviço. */
@WebMvcTest(OrderController.class)
@Import({WebSliceSecurityConfig.class, JacksonConfig.class})
class CreateOrderValidationTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    OrderService orders;

    private ResultActions send(String json) throws Exception {
        return mvc.perform(post("/api/v1/orders").with(jwt().jwt(j -> j.subject(UUID.randomUUID().toString())))
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    /** Espera 400 VALIDATION_ERROR apontando {@code field} e garante que o serviço não foi chamado. */
    private void assertFieldRejected(String json, String field) throws Exception {
        send(json).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[?(@.field=='" + field + "')]").exists());
        verifyNoInteractions(orders);
    }

    private void stubCreate() {
        var now = java.time.Instant.parse("2026-01-01T10:00:00Z");
        org.mockito.Mockito.when(orders.create(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(new OrderResponse(UUID.randomUUID(), null, java.util.List.of(), null,
                        com.fdl.platform.order.domain.OrderStatus.RECEBIDO, 0, java.util.List.of(),
                        java.util.List.of(), UUID.randomUUID(), now, now));
    }

    private void assertMalformed(String json) throws Exception {
        send(json).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
        verifyNoInteractions(orders);
    }

    @Test
    void emptyBodyListsCustomerItemsAndAddress() throws Exception {
        send("{}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[?(@.field=='customer')]").exists())
                .andExpect(jsonPath("$.errors[?(@.field=='items')]").exists())
                .andExpect(jsonPath("$.errors[?(@.field=='deliveryAddress')]").exists());
        verifyNoInteractions(orders);
    }

    @ParameterizedTest
    @ValueSource(strings = {"not json", "{\"customer\":", "[]", "", "null"})
    void malformedJsonIsMalformedRequest(String body) throws Exception {
        assertMalformed(body);
    }

    @Test
    void nonJsonContentTypeIs415() throws Exception {
        mvc.perform(post("/api/v1/orders").with(jwt().jwt(j -> j.subject(UUID.randomUUID().toString())))
                .contentType(MediaType.TEXT_PLAIN).content("x"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"));
        verifyNoInteractions(orders);
    }

    // ---- campos somente-servidor e desconhecidos ----

    @ParameterizedTest
    @ValueSource(strings = {"\"totalCents\":1", "\"id\":\"22222222-2222-2222-2222-222222222222\"",
            "\"status\":\"ENTREGUE\"", "\"createdBy\":\"x\"", "\"extra\":true"})
    void serverOnlyAndUnknownFieldsAreRejected(String extra) throws Exception {
        assertMalformed(OrderJson.valid().replaceFirst("\\}$", "," + extra.replace("\\", "\\\\") + "}"));
    }

    @Test
    void unknownFieldInsideAnItemIsRejected() throws Exception {
        assertMalformed(OrderJson.replacing("\"quantity\":1,", "\"quantity\":1,\"subtotalCents\":5490,"));
    }

    // ---- cliente ----

    @Test
    void missingCustomer() throws Exception {
        assertFieldRejected("{" + OrderJson.ITEMS + "," + OrderJson.ADDRESS + "}", "customer");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "A"})
    void customerNameEmptyBlankOrTooShort(String name) throws Exception {
        assertFieldRejected(OrderJson.replacing("\"name\":\"Maria Silva\"", "\"name\":\"" + name + "\""),
                "customer.name");
    }

    @Test
    void customerNameLongerThan100() throws Exception {
        assertFieldRejected(
                OrderJson.replacing("\"name\":\"Maria Silva\"", "\"name\":\"" + "n".repeat(101) + "\""),
                "customer.name");
    }

    @ParameterizedTest
    @ValueSource(strings = {"11abc", "119876543", "119876543210", "(11)98765-4321", "", " "})
    void customerPhoneMustBe10Or11Digits(String phone) throws Exception {
        assertFieldRejected(OrderJson.replacing("\"phone\":\"11987654321\"", "\"phone\":\"" + phone + "\""),
                "customer.phone");
    }

    @ParameterizedTest
    @ValueSource(strings = {"1198765432", "11987654321"})
    void customerPhoneWith10Or11DigitsIsAccepted(String phone) throws Exception {
        stubCreate();
        send(OrderJson.replacing("\"phone\":\"11987654321\"", "\"phone\":\"" + phone + "\""))
                .andExpect(status().isCreated());
    }

    // ---- lista de itens ----

    @Test
    void missingItems() throws Exception {
        assertFieldRejected("{" + OrderJson.CUSTOMER + "," + OrderJson.ADDRESS + "}", "items");
    }

    @Test
    void emptyItems() throws Exception {
        assertFieldRejected(OrderJson.replacing(OrderJson.ITEMS, "\"items\":[]"), "items");
    }

    @Test
    void moreThan50Items() throws Exception {
        var item = "{\"name\":\"Pizza\",\"quantity\":1,\"unitPriceCents\":100}";
        var items = "\"items\":[" + String.join(",", java.util.Collections.nCopies(51, item)) + "]";

        assertFieldRejected(OrderJson.replacing(OrderJson.ITEMS, items), "items");
    }

    @Test
    void exactly50ItemsIsAccepted() throws Exception {
        stubCreate();
        var item = "{\"name\":\"Pizza\",\"quantity\":1,\"unitPriceCents\":100}";
        var items = "\"items\":[" + String.join(",", java.util.Collections.nCopies(50, item)) + "]";

        send(OrderJson.replacing(OrderJson.ITEMS, items)).andExpect(status().isCreated());
    }

    @Test
    void nullItemInTheList() throws Exception {
        send(OrderJson.replacing(OrderJson.ITEMS, "\"items\":[null]")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        verifyNoInteractions(orders);
    }

    // ---- item ----

    @ParameterizedTest
    @ValueSource(strings = {"", " "})
    void itemNameEmptyOrBlank(String name) throws Exception {
        assertFieldRejected(OrderJson.replacing("\"name\":\"Pizza\"", "\"name\":\"" + name + "\""), "items[0].name");
    }

    @Test
    void itemWithoutName() throws Exception {
        assertFieldRejected(OrderJson.replacing("{\"name\":\"Pizza\",", "{"), "items[0].name");
    }

    @Test
    void itemNameLongerThan120() throws Exception {
        assertFieldRejected(OrderJson.replacing("\"name\":\"Pizza\"", "\"name\":\"" + "p".repeat(121) + "\""),
                "items[0].name");
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "100"})
    void itemQuantityOutOfRange(String quantity) throws Exception {
        assertFieldRejected(OrderJson.replacing("\"quantity\":1,", "\"quantity\":" + quantity + ","),
                "items[0].quantity");
    }

    @Test
    void itemWithoutQuantity() throws Exception {
        assertFieldRejected(OrderJson.replacing("\"quantity\":1,", ""), "items[0].quantity");
    }

    @ParameterizedTest
    @ValueSource(strings = {"1.5", "\"dois\"", "\"2\"", "true", "null"})
    void itemQuantityDecimalOrNonNumeric(String quantity) throws Exception {
        send(OrderJson.replacing("\"quantity\":1,", "\"quantity\":" + quantity + ","))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(orders);
    }

    @Test
    void itemQuantityDecimalNamesTheField() throws Exception {
        send(OrderJson.replacing("\"quantity\":1,", "\"quantity\":1.5,"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"))
                .andExpect(jsonPath("$.errors[0].field").value("items[0].quantity"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"-1", "10000001"})
    void itemUnitPriceOutOfRange(String price) throws Exception {
        assertFieldRejected(OrderJson.replacing("\"unitPriceCents\":5490", "\"unitPriceCents\":" + price),
                "items[0].unitPriceCents");
    }

    @Test
    void itemWithoutUnitPrice() throws Exception {
        assertFieldRejected(OrderJson.replacing(",\"unitPriceCents\":5490", ""), "items[0].unitPriceCents");
    }

    @Test
    void errorsPointAtTheOffendingItemIndex() throws Exception {
        assertFieldRejected(OrderJson.replacing("\"quantity\":2,", "\"quantity\":0,"), "items[1].quantity");
    }

    // ---- endereço ----

    @Test
    void missingAddress() throws Exception {
        assertFieldRejected("{" + OrderJson.CUSTOMER + "," + OrderJson.ITEMS + "}", "deliveryAddress");
    }

    @ParameterizedTest
    @CsvSource({"street,Avenida Paulista", "number,1000", "neighborhood,Bela Vista", "city,São Paulo"})
    void requiredAddressTextFieldsCannotBeEmptyOrBlank(String field, String value) throws Exception {
        assertFieldRejected(OrderJson.replacing("\"" + field + "\":\"" + value + "\"", "\"" + field + "\":\"\""),
                "deliveryAddress." + field);
        assertFieldRejected(OrderJson.replacing("\"" + field + "\":\"" + value + "\"", "\"" + field + "\":\"  \""),
                "deliveryAddress." + field);
    }

    @ParameterizedTest
    @CsvSource({"street,Avenida Paulista", "number,1000", "complement,Apto 12", "neighborhood,Bela Vista",
            "city,São Paulo"})
    void addressTextFieldsAbove120CharactersAreRejected(String field, String value) throws Exception {
        assertFieldRejected(
                OrderJson.replacing("\"" + field + "\":\"" + value + "\"", "\"" + field + "\":\"" + "x".repeat(121) + "\""),
                "deliveryAddress." + field);
    }

    @ParameterizedTest
    @ValueSource(strings = {"sp", "SPA", "S", "S1", "", "São"})
    void stateMustBeTwoUppercaseLetters(String state) throws Exception {
        assertFieldRejected(OrderJson.replacing("\"state\":\"SP\"", "\"state\":\"" + state + "\""),
                "deliveryAddress.state");
    }

    @ParameterizedTest
    @ValueSource(strings = {"01310-100", "123", "0131010a", "013101000", ""})
    void zipCodeMustHaveExactly8Digits(String zip) throws Exception {
        assertFieldRejected(OrderJson.replacing("\"zipCode\":\"01310100\"", "\"zipCode\":\"" + zip + "\""),
                "deliveryAddress.zipCode");
    }

    @Test
    void complementIsOptional() throws Exception {
        stubCreate();
        send(OrderJson.replacing("\"complement\":\"Apto 12\",", "")).andExpect(status().isCreated());
        send(OrderJson.replacing("\"complement\":\"Apto 12\"", "\"complement\":null")).andExpect(status().isCreated());
    }
}
