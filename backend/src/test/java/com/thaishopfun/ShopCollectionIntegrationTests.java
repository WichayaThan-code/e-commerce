package com.thaishopfun;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** V41 shop collections: CRUD, ownership, published-only storefront, limits (small limits via properties). */
@SpringBootTest(properties = {"app.merchant.collections.max-per-shop=3", "app.merchant.collections.max-products-per-collection=2"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ShopCollectionIntegrationTests extends CatalogExtrasTestSupport {

    private String base(ShopFixture shop) { return "/api/merchant/shops/" + shop.shopId() + "/collections"; }

    @Test
    void merchantCrudAndPublicVisibility() throws Exception {
        var shop = approvedShop("col-crud", "0816100001", "1100000000301", "GENERAL");
        var tea = product(shop, "Tea Leaf", "40.00", true);
        var draft = product(shop, "Draft Item", "10.00", false);
        var auth = bearer(shop.owner().token());

        var created = mvc.perform(post(base(shop)).header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                .content("{\"nameTh\":\"ชุดของขวัญ\",\"nameEn\":\"Gift Sets!\",\"description\":\"รวมของขวัญ\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.collection.slug").value("gift-sets"))
            .andExpect(jsonPath("$.collection.published").value(false))
            .andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(created, "$.collection.id");

        mvc.perform(post(base(shop) + "/" + id + "/products").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                .content("{\"productIds\":[\"" + tea + "\",\"" + draft + "\"]}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.products", hasSize(2)))
            .andExpect(jsonPath("$.collection.productCount").value(2));

        // unpublished: hidden from the storefront
        mvc.perform(get("/api/shops/" + shop.slug() + "/collections")).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(0)));
        mvc.perform(get("/api/shops/" + shop.slug() + "/collections/gift-sets")).andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("COLLECTION_NOT_FOUND"));

        mvc.perform(put(base(shop) + "/" + id).header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                .content("{\"nameTh\":\"ชุดของขวัญ\",\"nameEn\":\"Gift Sets\",\"slug\":\"gifts\",\"published\":true}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.collection.slug").value("gifts"));

        // published: only PUBLISHED products are listed / counted
        mvc.perform(get("/api/shops/" + shop.slug() + "/collections").param("lang", "en"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].name").value("Gift Sets"))
            .andExpect(jsonPath("$[0].productCount").value(1));
        mvc.perform(get("/api/shops/" + shop.slug() + "/collections/gifts"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.products.total").value(1))
            .andExpect(jsonPath("$.products.items[0].id").value(tea.toString()))
            .andExpect(jsonPath("$.collection.slug").value("gifts"));

        // reorder + remove via replace, then single remove
        mvc.perform(put(base(shop) + "/" + id + "/products").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                .content("{\"productIds\":[\"" + draft + "\",\"" + tea + "\"]}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.products[0].productId").value(draft.toString()))
            .andExpect(jsonPath("$.products[1].productId").value(tea.toString()));
        mvc.perform(delete(base(shop) + "/" + id + "/products/" + draft).header("Authorization", auth))
            .andExpect(status().isOk()).andExpect(jsonPath("$.products", hasSize(1)));

        // second collection + collection reorder
        var second = mvc.perform(post(base(shop)).header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                .content("{\"nameTh\":\"มาใหม่\"}"))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.collection.slug").value("collection"))
            .andReturn().getResponse().getContentAsString();
        String secondId = JsonPath.read(second, "$.collection.id");
        mvc.perform(put(base(shop) + "/order").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                .content("{\"ids\":[\"" + secondId + "\",\"" + id + "\"]}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(secondId));
        mvc.perform(put(base(shop) + "/order").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                .content("{\"ids\":[\"" + id + "\"]}"))
            .andExpect(status().isBadRequest());

        // slug clash -> 409, validation
        mvc.perform(put(base(shop) + "/" + secondId).header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                .content("{\"nameTh\":\"มาใหม่\",\"slug\":\"gifts\"}"))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("COLLECTION_SLUG_TAKEN"));
        mvc.perform(post(base(shop)).header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                .content("{\"nameTh\":\"<b>x</b>\",\"slug\":\"Bad Slug\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.fieldErrors[*].field", hasItems("nameTh", "slug")));

        mvc.perform(delete(base(shop) + "/" + id).header("Authorization", auth)).andExpect(status().isNoContent());
        mvc.perform(get(base(shop)).header("Authorization", auth)).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    void ownershipIsEnforced() throws Exception {
        var mine = approvedShop("col-own-a", "0816100011", "1100000000311", "GENERAL");
        var other = approvedShop("col-own-b", "0816100012", "1100000000312", "GENERAL");
        var foreignProduct = product(other, "Foreign Tea", "30.00", true);
        var created = mvc.perform(post(base(mine)).header("Authorization", bearer(mine.owner().token())).contentType(MediaType.APPLICATION_JSON)
                .content("{\"nameTh\":\"ของฉัน\",\"slug\":\"mine\"}"))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(created, "$.collection.id");

        // product of another shop
        mvc.perform(post(base(mine) + "/" + id + "/products").header("Authorization", bearer(mine.owner().token())).contentType(MediaType.APPLICATION_JSON)
                .content("{\"productIds\":[\"" + foreignProduct + "\"]}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.fieldErrors[0].code").value("COLLECTION_PRODUCT_INVALID"));
        // another shop's owner cannot reach my collections
        mvc.perform(get(base(mine)).header("Authorization", bearer(other.owner().token()))).andExpect(status().isNotFound());
        // collection id of my shop through the other shop's path
        mvc.perform(get(base(other) + "/" + id).header("Authorization", bearer(other.owner().token())))
            .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("COLLECTION_NOT_FOUND"));
        mvc.perform(get(base(mine))).andExpect(status().isUnauthorized());
        // cover must be an image of this shop's library
        mvc.perform(put(base(mine) + "/" + id).header("Authorization", bearer(mine.owner().token())).contentType(MediaType.APPLICATION_JSON)
                .content("{\"nameTh\":\"ของฉัน\",\"coverMediaId\":\"" + java.util.UUID.randomUUID() + "\"}"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors[0].code").value("COLLECTION_COVER_INVALID"));
    }

    @Test
    void limitsAreEnforced() throws Exception {
        var shop = approvedShop("col-limit", "0816100021", "1100000000321", "GENERAL");
        var auth = bearer(shop.owner().token());
        String first = null;
        for (int i = 0; i < 3; i++) {
            var body = mvc.perform(post(base(shop)).header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"nameTh\":\"ชุด " + i + "\",\"slug\":\"set-" + i + "\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
            if (first == null) first = JsonPath.read(body, "$.collection.id");
        }
        mvc.perform(post(base(shop)).header("Authorization", auth).contentType(MediaType.APPLICATION_JSON).content("{\"nameTh\":\"เกิน\"}"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("COLLECTION_LIMIT_REACHED"));

        var a = product(shop, "Item A", "10.00", true);
        var b = product(shop, "Item B", "10.00", true);
        var c = product(shop, "Item C", "10.00", true);
        mvc.perform(post(base(shop) + "/" + first + "/products").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                .content("{\"productIds\":[\"" + a + "\",\"" + b + "\"]}"))
            .andExpect(status().isOk());
        mvc.perform(post(base(shop) + "/" + first + "/products").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                .content("{\"productIds\":[\"" + c + "\"]}"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("COLLECTION_PRODUCT_LIMIT_REACHED"));
        // re-adding an existing product is a no-op, not a limit hit
        mvc.perform(post(base(shop) + "/" + first + "/products").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                .content("{\"productIds\":[\"" + a + "\"]}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.products", hasSize(2)));
    }

    @Test
    void collectionsBlockIsCustomOnlyAndValidated() throws Exception {
        var shop = approvedShop("col-block", "0816100031", "1100000000331", "CUSTOM");
        saveThemeConfig(shop.owner(), shop.shopId(), "CUSTOM", """
            ,"layout":{"blocks":[{"id":"col-1","type":"COLLECTIONS","enabled":true,"settings":{},
              "content":{"title":{"th":"คอลเลกชัน"},"collectionSlugs":["gifts","new-in"],"limit":4}}]}
            """)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.config.layout.blocks[0].type").value("COLLECTIONS"))
            .andExpect(jsonPath("$.config.layout.blocks[0].content.limit").value(4))
            .andExpect(jsonPath("$.config.layout.blocks[0].content.collectionSlugs", contains("gifts", "new-in")));
        saveThemeConfig(shop.owner(), shop.shopId(), "CUSTOM", """
            ,"layout":{"blocks":[{"id":"col-1","type":"COLLECTIONS","enabled":true,"settings":{},
              "content":{"collectionSlugs":["Bad Slug!"],"limit":99}}]}
            """)
            .andExpect(status().isBadRequest());
    }
}
