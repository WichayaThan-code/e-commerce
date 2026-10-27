package com.thaishopfun;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.ResultActions;

import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** V43 configurator -> cart / order lines with the flag ON (flag off: {@link ConfiguredCartFlagOffTests}). */
@SpringBootTest(properties = "app.features.configurator.enabled=true")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ConfiguredCartIntegrationTests extends CatalogExtrasTestSupport {

    static final String ADDRESS = """
        {"acceptTerms":true,"shippingAddress":{"recipientName":"สมหญิง ผู้รับ","phone":"0812345678","addressLine1":"1 ถนนสุขุมวิท",
         "subdistrict":"คลองเตย","district":"คลองเตย","province":"กรุงเทพมหานคร","postalCode":"10110"}}""";

    static final String SCHEMA = """
        {"steps":[
          {"id":"color","type":"SINGLE","label":{"th":"สี","en":"Color"},"required":true,"options":[
            {"id":"gold","label":{"th":"ทอง","en":"Gold"},"priceDelta":"%s"},
            {"id":"silver","label":{"th":"เงิน"},"priceDelta":0}]},
          {"id":"engrave","type":"TEXT","label":{"th":"สลักชื่อ"},"required":false,"maxLength":8,"charset":"LATIN","priceDelta":"25"}
        ]}
        """;

    /** Runs before the support class wipes products (FK order). */
    @AfterEach
    void wipeCartsAndOrders() {
        for (var t : List.of("merchant_order_confirmations", "order_item_reveal_logs", "order_financial_snapshots", "payment_status_history",
                "payments", "order_status_history", "inventory_reservations", "promotion_usages", "order_items", "order_addresses")) {
            jdbc.update("DELETE FROM " + t);
        }
        ledger.deleteAll();
        jdbc.update("DELETE FROM orders");
        jdbc.update("DELETE FROM shop_customers");
        jdbc.update("DELETE FROM cart_items");
        jdbc.update("DELETE FROM carts");
    }

    record Fixture(ShopFixture shop, UUID product, String sku, String buyer) {}

    Fixture fixture(String key, String phone, String citizen, String buyerPhone) throws Exception {
        var shop = approvedShop(key, phone, citizen, "CUSTOM");
        var body = mvc.perform(post("/api/merchant/shops/" + shop.shopId() + "/products").header("Authorization", bearer(shop.owner().token()))
                .contentType(MediaType.APPLICATION_JSON).content("""
                    {"nameTh":"กำไล","nameEn":"Bracelet","productType":"SIMPLE","basePrice":100.00,"skus":[{"skuCode":"%s-1","price":100.00,"initialStock":20}]}
                    """.formatted(key.toUpperCase().replaceAll("[^A-Z0-9]", ""))))
            .andExpect(status().is2xxSuccessful()).andReturn().getResponse().getContentAsString();
        var product = UUID.fromString(JsonPath.read(body, "$.id"));
        String sku = JsonPath.read(body, "$.skus[0].id");
        jdbc.update("update products set status = 'PUBLISHED' where id = ?", product);
        saveSchema(shop, product, 0, "120.00").andExpect(status().isOk());
        var buyer = register(key + "-buyer@example.com", buyerPhone).token();
        return new Fixture(shop, product, sku, buyer);
    }

    ResultActions saveSchema(ShopFixture shop, UUID product, int version, String goldDelta) throws Exception {
        return mvc.perform(put("/api/merchant/shops/" + shop.shopId() + "/products/" + product + "/configurator")
            .header("Authorization", bearer(shop.owner().token())).contentType(MediaType.APPLICATION_JSON)
            .content("{\"enabled\":true,\"version\":" + version + ",\"schema\":" + SCHEMA.formatted(goldDelta) + "}"));
    }

    ResultActions add(String buyer, String body) throws Exception {
        return mvc.perform(post("/api/cart/items").header("Authorization", bearer(buyer)).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    @Test
    void configuredLinesArePricedServerSideAndKeyedByHash() throws Exception {
        var f = fixture("cfgcart-a", "0816300001", "1100000000501", "0816300101");
        // plain line: unchanged shape (no configuration key)
        var plain = add(f.buyer(), "{\"skuId\":\"" + f.sku() + "\",\"quantity\":1}").andExpect(status().isOk())
            .andExpect(jsonPath("$.groups[0].items[0].unitPrice").value(100.0))
            .andReturn().getResponse().getContentAsString();
        assertFalse(plain.contains("\"configuration\""), "plain line JSON has no configuration key");

        // configured: client price fields are ignored, server quote wins (100 + 120 + 25)
        var gold = "{\"skuId\":\"" + f.sku() + "\",\"quantity\":1,\"unitPrice\":1,\"configuration\":{\"version\":1,\"selections\":{\"color\":\"gold\",\"engrave\":\" Ann \"}}}";
        add(f.buyer(), gold).andExpect(status().isOk());
        add(f.buyer(), gold).andExpect(status().isOk());
        add(f.buyer(), "{\"skuId\":\"" + f.sku() + "\",\"configuration\":{\"selections\":{\"color\":\"silver\"}}}").andExpect(status().isOk());
        add(f.buyer(), "{\"skuId\":\"" + f.sku() + "\",\"quantity\":2}").andExpect(status().isOk());

        var cart = mvc.perform(get("/api/cart").header("Authorization", bearer(f.buyer())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.groups[0].items", hasSize(3)))
            .andReturn().getResponse().getContentAsString();
        List<Integer> plainQty = JsonPath.read(cart, "$.groups[0].items[?(!@.configuration)].quantity");
        List<Number> plainPrice = JsonPath.read(cart, "$.groups[0].items[?(!@.configuration)].unitPrice");
        assertEquals(List.of(3), plainQty, "plain line merged (1 + 2)");
        assertEquals(100.0, plainPrice.get(0).doubleValue());
        List<Integer> goldQty = JsonPath.read(cart, "$.groups[0].items[?(@.configuration.selections.color == 'gold')].quantity");
        List<Number> goldPrice = JsonPath.read(cart, "$.groups[0].items[?(@.configuration.selections.color == 'gold')].unitPrice");
        assertEquals(List.of(2), goldQty, "same configuration merged by hash (1 + 1)");
        assertEquals(245.0, goldPrice.get(0).doubleValue());
        List<Number> silverPrice = JsonPath.read(cart, "$.groups[0].items[?(@.configuration.selections.color == 'silver')].unitPrice");
        assertEquals(100.0, silverPrice.get(0).doubleValue(), "different configuration = separate line");
        List<String> delta = JsonPath.read(cart, "$.groups[0].items[?(@.configuration.selections.color == 'gold')].configuration.configDelta");
        List<String> engrave = JsonPath.read(cart, "$.groups[0].items[?(@.configuration.selections.color == 'gold')].configuration.selections.engrave");
        List<String> label = JsonPath.read(cart, "$.groups[0].items[?(@.configuration.selections.color == 'gold')].configuration.lines[0].label.th");
        assertEquals(List.of("145.00"), delta);
        assertEquals(List.of("Ann"), engrave);
        assertEquals(List.of("ทอง"), label);
        assertEquals(2, jdbc.queryForObject("select count(*) from cart_items where config_hash is not null", Integer.class));

        // invalid / incomplete selections are rejected, nothing added
        add(f.buyer(), "{\"skuId\":\"" + f.sku() + "\",\"configuration\":{\"selections\":{}}}")
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors[0].code").value("CONFIGURATOR_SELECTION_REQUIRED"));
        add(f.buyer(), "{\"skuId\":\"" + f.sku() + "\",\"configuration\":{\"selections\":{\"color\":\"plastic\"}}}")
            .andExpect(status().isBadRequest());
        add(f.buyer(), "{\"skuId\":\"" + f.sku() + "\",\"configuration\":{\"version\":9,\"selections\":{\"color\":\"gold\"}}}")
            .andExpect(status().isConflict());
        // stock counts every line of the SKU: 3 + 2 + 1 already, 20 in stock
        add(f.buyer(), "{\"skuId\":\"" + f.sku() + "\",\"quantity\":15,\"configuration\":{\"selections\":{\"color\":\"gold\"}}}")
            .andExpect(status().isConflict());
        assertEquals(3, jdbc.queryForObject("select count(*) from cart_items", Integer.class));
    }

    @Test
    void optionPriceChangeIsDetectedAndCheckoutCopiesSnapshot() throws Exception {
        var f = fixture("cfgcart-b", "0816300002", "1100000000502", "0816300102");
        add(f.buyer(), "{\"skuId\":\"" + f.sku() + "\",\"quantity\":2,\"configuration\":{\"selections\":{\"color\":\"gold\"}}}").andExpect(status().isOk());
        add(f.buyer(), "{\"skuId\":\"" + f.sku() + "\",\"quantity\":1}").andExpect(status().isOk());

        // merchant raises the gold option price -> checkout validation reports PRICE_CHANGED on the configured line
        saveSchema(f.shop(), f.product(), 1, "150.00").andExpect(status().isOk());
        mvc.perform(post("/api/cart/validate").header("Authorization", bearer(f.buyer())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.valid").value(false))
            .andExpect(jsonPath("$.issues[?(@.code == 'PRICE_CHANGED')]", hasSize(1)));
        mvc.perform(post("/api/checkout").header("Idempotency-Key", "cfg-key-0001").header("Authorization", bearer(f.buyer()))
                .contentType(MediaType.APPLICATION_JSON).content(ADDRESS))
            .andExpect(status().is4xxClientError());
        assertEquals(0, jdbc.queryForObject("select count(*) from orders", Integer.class));

        // buyer accepts -> re-quoted (100 + 150)
        mvc.perform(post("/api/cart/accept-prices").header("Authorization", bearer(f.buyer()))).andExpect(status().isOk())
            .andExpect(jsonPath("$.groups[0].items[?(@.configuration)].unitPrice", contains(250.0)))
            .andExpect(jsonPath("$.groups[0].items[?(@.configuration)].configuration.configDelta", contains("150.00")));
        var out = mvc.perform(post("/api/checkout").header("Idempotency-Key", "cfg-key-0002").header("Authorization", bearer(f.buyer()))
                .contentType(MediaType.APPLICATION_JSON).content(ADDRESS))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String orderId = JsonPath.read(out, "$.orders[0].id");
        var order = mvc.perform(get("/api/orders/" + orderId).header("Authorization", bearer(f.buyer())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items", hasSize(2)))
            .andReturn().getResponse().getContentAsString();
        List<Object> configs = JsonPath.read(order, "$.items[*].configuration");
        assertEquals(1, configs.size(), "only the configured item carries a configuration");
        List<Number> configuredPrice = JsonPath.read(order, "$.items[?(@.configuration)].unitPrice");
        assertEquals(250.0, configuredPrice.get(0).doubleValue());
        List<String> optionLabel = JsonPath.read(order, "$.items[?(@.configuration)].configuration.lines[0].label.th");
        assertEquals("ทอง", optionLabel.get(0));
        assertEquals(1, jdbc.queryForObject("select count(*) from order_items where config_hash is not null and configuration is not null", Integer.class));
    }

    @Test
    void configurationTurnedOffBlocksCheckoutOfThatLine() throws Exception {
        var f = fixture("cfgcart-c", "0816300003", "1100000000503", "0816300103");
        add(f.buyer(), "{\"skuId\":\"" + f.sku() + "\",\"configuration\":{\"selections\":{\"color\":\"gold\"}}}").andExpect(status().isOk());
        jdbc.update("update product_configurators set enabled = false where product_id = ?", f.product());
        mvc.perform(post("/api/cart/validate").header("Authorization", bearer(f.buyer())))
            .andExpect(jsonPath("$.valid").value(false))
            .andExpect(jsonPath("$.issues[0].code").value("CONFIGURATION_INVALID"));
    }
}
