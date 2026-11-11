package com.thaishopfun;

import com.jayway.jsonpath.JsonPath;
import com.thaishopfun.merchant.MerchantDtos;
import jakarta.validation.constraints.Size;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.lang.reflect.RecordComponent;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Merchant onboarding DTO length parity + houseNo-too-long must be 400 with field errors (not 409).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MerchantOnboardingValidationTests {
    @Autowired MockMvc mvc;

    /** Column lengths from Flyway V1/V2 for owners + merchant_applications shop fields. */
    private static final Map<String, Integer> OWNER_MAX = Map.ofEntries(
        Map.entry("title", 30),
        Map.entry("firstName", 100),
        Map.entry("lastName", 100),
        Map.entry("nationality", 100),
        Map.entry("phone", 30),
        Map.entry("email", 255),
        Map.entry("houseNo", 50),
        Map.entry("villageNo", 50),
        Map.entry("building", 255),
        Map.entry("alley", 255),
        Map.entry("road", 255),
        Map.entry("province", 100),
        Map.entry("district", 100),
        Map.entry("subdistrict", 100),
        Map.entry("postalCode", 10),
        Map.entry("bankName", 100),
        Map.entry("bankAccountNumber", 30),
        Map.entry("bankAccountName", 255)
    );

    private static final Map<String, Integer> SHOP_MAX = Map.ofEntries(
        Map.entry("shopName", 100),
        Map.entry("shopNameEn", 100),
        Map.entry("description", 500),
        Map.entry("category", 100),
        Map.entry("website", 500),
        Map.entry("lineId", 500),
        Map.entry("facebookUrl", 500),
        Map.entry("instagramUrl", 500),
        Map.entry("tiktokUrl", 500),
        Map.entry("address", 500),
        Map.entry("province", 100),
        Map.entry("district", 100),
        Map.entry("subdistrict", 100),
        Map.entry("postalCode", 10)
    );

    @Test
    void ownerAndShopDtoSizeAnnotationsMatchSchema() {
        assertSizeAnnotations(MerchantDtos.OwnerRequest.class, OWNER_MAX);
        assertSizeAnnotations(MerchantDtos.ShopRequest.class, SHOP_MAX);
    }

    @Test
    void houseNoLongerThan50Returns400WithFieldErrorNot409() throws Exception {
        String token = tokenFor("house-long@example.com", "0811112233");
        String houseNo = "x".repeat(51);
        mvc.perform(put("/api/merchant-applications/draft/owner").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                  {"ownerType":"PERSON","title":"นาย","firstName":"สมชาย","lastName":"ใจดี","citizenId":"1234567890123","birthDate":"1980-08-15","nationality":"ไทย","phone":"0811112233","email":"house-long@example.com","houseNo":"%s","province":"กรุงเทพมหานคร","district":"บางรัก","subdistrict":"สีลม","postalCode":"10500","bankName":"KBANK","bankAccountNumber":"1234567890","bankAccountName":"สมชาย ใจดี"}
                  """.formatted(houseNo)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
            .andExpect(jsonPath("$.section").value("owner"))
            .andExpect(jsonPath("$.fieldErrors[?(@.field == 'houseNo')]").isNotEmpty())
            .andExpect(jsonPath("$.fieldErrors[?(@.field == 'houseNo')].code").value(org.hamcrest.Matchers.hasItem("TOO_LONG")));
    }

    @Test
    void multipleOwnerFieldErrorsReturnedTogether() throws Exception {
        String token = tokenFor("multi-err@example.com", "0811112244");
        mvc.perform(put("/api/merchant-applications/draft/owner").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                  {"ownerType":"PERSON","title":"","firstName":"","lastName":"","citizenId":"abc","birthDate":"1980-01-01","nationality":"ไทย","phone":"xx","email":"bad","houseNo":"%s","province":"กรุงเทพมหานคร","district":"บางรัก","subdistrict":"สีลม","postalCode":"12","bankName":"KBANK","bankAccountNumber":"abc","bankAccountName":""}
                  """.formatted("y".repeat(51))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
            .andExpect(jsonPath("$.fieldErrors[?(@.field == 'houseNo')]").isNotEmpty())
            .andExpect(jsonPath("$.fieldErrors[?(@.field == 'email')]").isNotEmpty())
            .andExpect(jsonPath("$.fieldErrors[?(@.field == 'postalCode')]").isNotEmpty());
    }

    private static void assertSizeAnnotations(Class<?> type, Map<String, Integer> expected) {
        Map<String, Integer> found = new LinkedHashMap<>();
        for (RecordComponent component : type.getRecordComponents()) {
            Size size = component.getAnnotation(Size.class);
            if (size == null) {
                for (var annotation : component.getDeclaredAnnotations()) {
                    try {
                        var method = annotation.annotationType().getMethod("value");
                        Object value = method.invoke(annotation);
                        if (value instanceof Size[] sizes && sizes.length > 0) {
                            size = sizes[0];
                        }
                    } catch (ReflectiveOperationException ignored) {
                        // not a container
                    }
                }
            }
            // Also check annotations on the record component accessor method / type use
            if (size == null) {
                try {
                    var accessor = type.getDeclaredMethod(component.getName());
                    size = accessor.getAnnotation(Size.class);
                } catch (NoSuchMethodException ignored) {
                    // continue
                }
            }
            if (size != null && expected.containsKey(component.getName())) {
                found.put(component.getName(), size.max());
            }
        }
        // jakarta @Size on record components is retained on the compact constructor parameters
        if (found.size() < expected.size()) {
            for (var ctor : type.getDeclaredConstructors()) {
                var params = ctor.getParameters();
                for (var param : params) {
                    if (!expected.containsKey(param.getName())) continue;
                    Size size = param.getAnnotation(Size.class);
                    if (size != null) found.put(param.getName(), size.max());
                }
            }
        }
        for (var entry : expected.entrySet()) {
            Integer max = found.get(entry.getKey());
            assertNotNull(max, () -> "Missing @Size(max) on " + type.getSimpleName() + "." + entry.getKey());
            assertEquals(entry.getValue(), max, () -> "Wrong @Size(max) on " + type.getSimpleName() + "." + entry.getKey());
        }
    }

    private String tokenFor(String email, String phone) throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
            {"firstName":"ทดสอบ","lastName":"ยาว","email":"%s","phone":"%s","password":"Password1"}
            """.formatted(email, phone))).andExpect(status().isCreated());
        var login = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
            {"identity":"%s","password":"Password1"}
            """.formatted(email))).andExpect(status().isOk()).andReturn();
        return JsonPath.read(login.getResponse().getContentAsString(), "$.accessToken");
    }
}
