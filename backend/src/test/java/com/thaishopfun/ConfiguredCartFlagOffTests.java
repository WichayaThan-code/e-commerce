package com.thaishopfun;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Default config (configurator flag OFF): add-to-cart with a configuration is rejected; plain add-to-cart unchanged. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ConfiguredCartFlagOffTests extends CatalogExtrasTestSupport {

    @AfterEach
    void wipeCarts() {
        jdbc.update("DELETE FROM cart_items");
        jdbc.update("DELETE FROM carts");
    }

    @Test
    void configurationRejectedWhileFlagOff() throws Exception {
        var shop = approvedShop("cfgcart-off", "0816300031", "1100000000531", "CUSTOM");
        var body = mvc.perform(post("/api/merchant/shops/" + shop.shopId() + "/products").header("Authorization", bearer(shop.owner().token()))
                .contentType(MediaType.APPLICATION_JSON).content("""
                    {"nameTh":"กำไล","nameEn":"Off","productType":"SIMPLE","basePrice":100.00,"skus":[{"skuCode":"CFGOFF-1","price":100.00,"initialStock":5}]}
                    """))
            .andExpect(status().is2xxSuccessful()).andReturn().getResponse().getContentAsString();
        jdbc.update("update products set status = 'PUBLISHED' where id = ?", java.util.UUID.fromString(JsonPath.read(body, "$.id")));
        String sku = JsonPath.read(body, "$.skus[0].id");
        var buyer = register("cfgcart-off-buyer@example.com", "0816300131").token();

        mvc.perform(post("/api/cart/items").header("Authorization", bearer(buyer)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"skuId\":\"" + sku + "\",\"configuration\":{\"selections\":{\"color\":\"gold\"}}}"))
            .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("FEATURE_DISABLED"));
        assertEquals(0, jdbc.queryForObject("select count(*) from cart_items", Integer.class));
        var plain = mvc.perform(post("/api/cart/items").header("Authorization", bearer(buyer)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"skuId\":\"" + sku + "\",\"quantity\":1}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.groups[0].items[0].unitPrice").value(100.0))
            .andReturn().getResponse().getContentAsString();
        assertFalse(plain.contains("\"configuration\""));
    }
}
