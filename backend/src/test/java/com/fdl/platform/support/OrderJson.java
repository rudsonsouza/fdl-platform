package com.fdl.platform.support;

/** JSON de pedido válido para os testes de API; os testes trocam trechos para montar variações inválidas. */
public final class OrderJson {

    public static final String CUSTOMER = "\"customer\":{\"name\":\"Maria Silva\",\"phone\":\"11987654321\"}";
    public static final String ITEMS = "\"items\":[{\"name\":\"Pizza\",\"quantity\":1,\"unitPriceCents\":5490},"
            + "{\"name\":\"Refrigerante\",\"quantity\":2,\"unitPriceCents\":1200}]";
    public static final String ADDRESS = "\"deliveryAddress\":{\"street\":\"Avenida Paulista\",\"number\":\"1000\","
            + "\"complement\":\"Apto 12\",\"neighborhood\":\"Bela Vista\",\"city\":\"São Paulo\",\"state\":\"SP\","
            + "\"zipCode\":\"01310100\"}";

    private OrderJson() {
    }

    public static String valid() {
        return "{" + CUSTOMER + "," + ITEMS + "," + ADDRESS + "}";
    }

    /** O JSON válido com um trecho trocado; falha se o trecho não existir (evita teste vazio). */
    public static String replacing(String from, String to) {
        var json = valid();
        if (!json.contains(from)) {
            throw new IllegalArgumentException("Trecho não encontrado no JSON base: " + from);
        }
        return json.replace(from, to);
    }
}
