package com.thaishopfun;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** V42 product configurator with the feature flag ON (flag-off behaviour: {@link ProductConfiguratorFlagOffTests}). */
@SpringBootTest(properties = "app.features.configurator.enabled=true")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProductConfiguratorIntegrationTests extends CatalogExtrasTestSupport {

    static final String SCHEMA = """
        {"steps":[
          {"id":"color","type":"SINGLE","label":{"th":"สี","en":"Color"},"required":true,"options":[
            {"id":"gold","label":{"th":"ทอง","en":"Gold"},"priceDelta":"120.00","mediaId":%s},
            {"id":"silver","label":{"th":"เงิน"},"priceDelta":0},
            {"id":"rose","label":{"th":"โรสโกลด์"},"priceDelta":"50","available":false}]},
          {"id":"charms","type":"MULTI","label":{"th":"ชาร์ม"},"required":false,"min":0,"max":2,"options":[
            {"id":"heart","label":{"th":"หัวใจ"},"priceDelta":"30.50","stock":5},
            {"id":"star","label":{"th":"ดาว"},"priceDelta":"20"},
            {"id":"moon","label":{"th":"จันทร์"},"priceDelta":"10"}]},
          {"id":"engrave","type":"TEXT","label":{"th":"สลักชื่อ"},"required":false,"maxLength":8,"charset":"LATIN","priceDelta":"25"}
        ]}
        """;

    static String path(ShopFixture shop, UUID product) {
        return "/api/merchant/shops/" + shop.shopId() + "/products/" + product + "/configurator";
    }

    @Test
    void merchantSavesSchemaAndPublicQuotesServerSide() throws Exception {
        var shop = approvedShop("cfg-ok", "0816200001", "1100000000401", "CUSTOM");
        var product = product(shop, "Charm Bracelet", "100.00", true);
        var image = uploadImage(shop, 0xAA5500);
        var auth = bearer(shop.owner().token());

        mvc.perform(get(path(shop, product)).header("Authorization", auth))
            .andExpect(status().isOk()).andExpect(jsonPath("$.enabled").value(false)).andExpect(jsonPath("$.version").value(0));
        // not enabled yet -> hidden publicly
        mvc.perform(get("/api/shops/" + shop.slug() + "/products/" + product + "/configurator")).andExpect(status().isNotFound());

        mvc.perform(put(path(shop, product)).header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                .content("{\"enabled\":true,\"version\":0,\"schema\":" + SCHEMA.formatted("\"" + image + "\"") + "}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.version").value(1))
            .andExpect(jsonPath("$.schema.steps", hasSize(3)))
            .andExpect(jsonPath("$.schema.steps[0].options[0].priceDelta").value("120.00"));
        // stale version -> conflict
        mvc.perform(put(path(shop, product)).header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                .content("{\"enabled\":true,\"version\":0,\"schema\":" + SCHEMA.formatted("null") + "}"))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CONFIGURATOR_VERSION_CONFLICT"));

        var pub = mvc.perform(get("/api/shops/" + shop.slug() + "/products/" + product + "/configurator").param("lang", "en"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.version").value(1))
            .andExpect(jsonPath("$.steps[0].label").exists())
            .andExpect(jsonPath("$.steps[0].options[0].mediaUrl").value("/api/shops/" + shop.slug() + "/theme-media/" + image))
            .andReturn().getResponse().getContentAsString();
        assertFalse(pub.contains("\"stock\""), "stock counts stay private");

        var quote = "/api/shops/" + shop.slug() + "/products/" + product + "/configurator/quote";
        var body = mvc.perform(post(quote).contentType(MediaType.APPLICATION_JSON)
                .content("{\"version\":1,\"selections\":{\"color\":\"gold\",\"charms\":[\"heart\",\"star\"],\"engrave\":\"  Ann\\u0007  B  \"}}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.complete").value(true))
            .andExpect(jsonPath("$.total").value(295.5))
            .andExpect(jsonPath("$.selections.engrave").value("Ann B"))
            .andExpect(jsonPath("$.lines", hasSize(4)))
            .andReturn().getResponse().getContentAsString();
        String hash = JsonPath.read(body, "$.configHash");
        assertEquals(64, hash.length());
        // same selection, same hash (cart-line identity)
        var again = mvc.perform(post(quote).contentType(MediaType.APPLICATION_JSON)
                .content("{\"version\":1,\"selections\":{\"charms\":[\"heart\",\"star\"],\"color\":\"gold\",\"engrave\":\"Ann B\"}}"))
            .andReturn().getResponse().getContentAsString();
        assertEquals(hash, JsonPath.read(again, "$.configHash"));

        // incomplete -> missing, base price only
        mvc.perform(post(quote).contentType(MediaType.APPLICATION_JSON).content("{\"selections\":{}}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.complete").value(false))
            .andExpect(jsonPath("$.missing", contains("color")))
            .andExpect(jsonPath("$.total").value(100.0));
        // bad selections
        mvc.perform(post(quote).contentType(MediaType.APPLICATION_JSON).content("{\"selections\":{\"color\":\"rose\"}}"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors[0].field").value("selections.color"));
        mvc.perform(post(quote).contentType(MediaType.APPLICATION_JSON).content("{\"selections\":{\"color\":\"plastic\"}}"))
            .andExpect(status().isBadRequest());
        mvc.perform(post(quote).contentType(MediaType.APPLICATION_JSON).content("{\"selections\":{\"color\":\"gold\",\"charms\":[\"heart\",\"star\",\"moon\"]}}"))
            .andExpect(status().isBadRequest());
        mvc.perform(post(quote).contentType(MediaType.APPLICATION_JSON).content("{\"selections\":{\"color\":\"gold\",\"engrave\":\"<b>x</b>\"}}"))
            .andExpect(status().isBadRequest());
        mvc.perform(post(quote).contentType(MediaType.APPLICATION_JSON).content("{\"selections\":{\"color\":\"gold\",\"engrave\":\"TooLongName\"}}"))
            .andExpect(status().isBadRequest());
        mvc.perform(post(quote).contentType(MediaType.APPLICATION_JSON).content("{\"selections\":{\"color\":\"gold\",\"hack\":\"1\"}}"))
            .andExpect(status().isBadRequest());
        mvc.perform(post(quote).contentType(MediaType.APPLICATION_JSON).content("{\"version\":7,\"selections\":{\"color\":\"gold\"}}"))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CONFIGURATOR_VERSION_STALE"));
    }

    @Test
    void schemaIsValidated() throws Exception {
        var shop = approvedShop("cfg-bad", "0816200011", "1100000000411", "CUSTOM");
        var product = product(shop, "Ring", "100.00", true);
        var auth = bearer(shop.owner().token());
        for (var schema : new String[]{
            "{\"steps\":[{\"id\":\"BAD ID\",\"type\":\"SINGLE\",\"label\":{\"th\":\"x\"},\"options\":[{\"id\":\"a\",\"label\":{\"th\":\"a\"}}]}]}",
            "{\"steps\":[{\"id\":\"a\",\"type\":\"WHEEL\",\"label\":{\"th\":\"x\"}}]}",
            "{\"steps\":[{\"id\":\"a\",\"type\":\"SINGLE\",\"label\":{\"th\":\"<script>\"},\"options\":[{\"id\":\"a\",\"label\":{\"th\":\"a\"}}]}]}",
            "{\"steps\":[{\"id\":\"a\",\"type\":\"SINGLE\",\"label\":{\"th\":\"x\"},\"options\":[]}]}",
            "{\"steps\":[{\"id\":\"a\",\"type\":\"SINGLE\",\"label\":{\"th\":\"x\"},\"options\":[{\"id\":\"o\",\"label\":{\"th\":\"o\"}},{\"id\":\"o\",\"label\":{\"th\":\"p\"}}]}]}",
            "{\"steps\":[{\"id\":\"a\",\"type\":\"SINGLE\",\"label\":{\"th\":\"x\"},\"options\":[{\"id\":\"o\",\"label\":{\"th\":\"o\"},\"priceDelta\":\"-500\"}]}]}",
            "{\"steps\":[{\"id\":\"a\",\"type\":\"SINGLE\",\"label\":{\"th\":\"x\"},\"options\":[{\"id\":\"o\",\"label\":{\"th\":\"o\"},\"mediaId\":\"" + UUID.randomUUID() + "\"}]}]}",
            "{\"steps\":[{\"id\":\"a\",\"type\":\"MULTI\",\"label\":{\"th\":\"x\"},\"min\":3,\"max\":1,\"options\":[{\"id\":\"o\",\"label\":{\"th\":\"o\"}}]}]}",
            "{\"steps\":[{\"id\":\"a\",\"type\":\"TEXT\",\"label\":{\"th\":\"x\"},\"maxLength\":500}]}",
            "{\"steps\":[]}"
        }) {
            mvc.perform(put(path(shop, product)).header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"enabled\":true,\"version\":0,\"schema\":" + schema + "}"))
                .andExpect(status().isBadRequest());
        }
        assertEquals(0, configurators.count());
    }

    @Test
    void ownershipAndCustomOnly() throws Exception {
        var custom = approvedShop("cfg-own-a", "0816200021", "1100000000421", "CUSTOM");
        var general = approvedShop("cfg-own-b", "0816200022", "1100000000422", "GENERAL");
        var mine = product(custom, "Mine", "100.00", true);
        var theirs = product(general, "Theirs", "100.00", true);
        var schema = "{\"enabled\":true,\"version\":0,\"schema\":{\"steps\":[{\"id\":\"a\",\"type\":\"SINGLE\",\"label\":{\"th\":\"x\"},\"options\":[{\"id\":\"o\",\"label\":{\"th\":\"o\"}}]}]}}";

        // GENERAL shop cannot use the configurator
        mvc.perform(put(path(general, theirs)).header("Authorization", bearer(general.owner().token())).contentType(MediaType.APPLICATION_JSON).content(schema))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("CONFIGURATOR_CUSTOM_ONLY"));
        // other owner cannot touch my product (neither via their shop path nor mine)
        mvc.perform(put(path(general, mine)).header("Authorization", bearer(general.owner().token())).contentType(MediaType.APPLICATION_JSON).content(schema))
            .andExpect(status().isNotFound());
        mvc.perform(put(path(custom, mine)).header("Authorization", bearer(general.owner().token())).contentType(MediaType.APPLICATION_JSON).content(schema))
            .andExpect(status().isNotFound());
        mvc.perform(put(path(custom, mine)).contentType(MediaType.APPLICATION_JSON).content(schema)).andExpect(status().isUnauthorized());
        // product of another shop under my slug is not public
        mvc.perform(put(path(custom, mine)).header("Authorization", bearer(custom.owner().token())).contentType(MediaType.APPLICATION_JSON).content(schema))
            .andExpect(status().isOk());
        mvc.perform(get("/api/shops/" + general.slug() + "/products/" + mine + "/configurator")).andExpect(status().isNotFound());
        // unpublished product hides its configurator
        jdbc.update("update products set status = 'DRAFT' where id = ?", mine);
        mvc.perform(get("/api/shops/" + custom.slug() + "/products/" + mine + "/configurator")).andExpect(status().isNotFound());
    }
}
