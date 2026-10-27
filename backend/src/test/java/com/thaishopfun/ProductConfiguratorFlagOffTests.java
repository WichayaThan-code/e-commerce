package com.thaishopfun;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Default config: app.features.configurator.enabled=false -> every configurator endpoint answers 404 FEATURE_DISABLED. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProductConfiguratorFlagOffTests extends CatalogExtrasTestSupport {

    @Test
    void flagOffHidesEverything() throws Exception {
        var shop = approvedShop("cfg-off", "0816200031", "1100000000431", "CUSTOM");
        var product = product(shop, "Off", "100.00", true);
        var merchant = "/api/merchant/shops/" + shop.shopId() + "/products/" + product + "/configurator";
        mvc.perform(get(merchant).header("Authorization", bearer(shop.owner().token())))
            .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("FEATURE_DISABLED"));
        mvc.perform(put(merchant).header("Authorization", bearer(shop.owner().token())).contentType(MediaType.APPLICATION_JSON)
                .content("{\"enabled\":false,\"version\":0,\"schema\":{\"steps\":[]}}"))
            .andExpect(status().isNotFound());
        mvc.perform(get("/api/shops/" + shop.slug() + "/products/" + product + "/configurator"))
            .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("FEATURE_DISABLED"));
        mvc.perform(post("/api/shops/" + shop.slug() + "/products/" + product + "/configurator/quote")
                .contentType(MediaType.APPLICATION_JSON).content("{\"selections\":{}}"))
            .andExpect(status().isNotFound());
    }
}
