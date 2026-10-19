package com.thaishopfun;

import com.jayway.jsonpath.JsonPath;
import com.thaishopfun.auth.OtpVerificationRepository;
import com.thaishopfun.auth.PasswordResetTokenRepository;
import com.thaishopfun.auth.RefreshTokenRepository;
import com.thaishopfun.auth.UserAccountRepository;
import com.thaishopfun.auth.authz.PlatformRole;
import com.thaishopfun.auth.authz.PlatformRoleRepository;
import com.thaishopfun.merchant.MerchantApplicationRepository;
import com.thaishopfun.merchant.MerchantDocumentRepository;
import com.thaishopfun.merchant.OwnerAuditLogRepository;
import com.thaishopfun.merchant.OwnerRepository;
import com.thaishopfun.merchant.ShopMemberRepository;
import com.thaishopfun.merchant.ShopReinstatementRequestRepository;
import com.thaishopfun.merchant.ShopRepository;
import com.thaishopfun.merchant.policy.ShopPolicyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ShopPolicyServiceTests {
    @Autowired MockMvc mvc;
    @Autowired UserAccountRepository users;
    @Autowired PlatformRoleRepository platformRoles;
    @Autowired RefreshTokenRepository refreshTokens;
    @Autowired PasswordResetTokenRepository passwordResetTokens;
    @Autowired OtpVerificationRepository otpVerifications;
    @Autowired MerchantApplicationRepository applications;
    @Autowired MerchantDocumentRepository documents;
    @Autowired ShopReinstatementRequestRepository reinstatements;
    @Autowired ShopMemberRepository shopMembers;
    @Autowired ShopRepository shops;
    @Autowired OwnerAuditLogRepository ownerAudits;
    @Autowired OwnerRepository owners;
    @Autowired ShopPolicyRepository policies;

    @BeforeEach
    void clean() {
        policies.deleteAll();
        platformRoles.deleteAll();
        reinstatements.deleteAll();
        shopMembers.deleteAll();
        shops.deleteAll();
        documents.deleteAll();
        applications.deleteAll();
        ownerAudits.deleteAll();
        owners.deleteAll();
        otpVerifications.deleteAll();
        passwordResetTokens.deleteAll();
        refreshTokens.deleteAll();
        users.deleteAll();
    }

    @Test
    void saveThenListAndBlankBodyDeletesRow() throws Exception {
        var owner = register("policy-owner@example.com", "0811000001");
        long shopId = openShop(owner, "ร้านนโยบาย", "Policy Shop", "1100000000001");
        approve(shopId);
        var slug = shops.findByShopId(shopId).orElseThrow().getSlug();

        mvc.perform(put("/api/merchant/settings/policies?shopId=" + shopId)
                .header("Authorization", bearer(owner.token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"policies":[
                      {"type":"shipping","title":"ส่ง","body":"ส่งภายใน 2 วัน"},
                      {"type":"returns","title":"คืน","body":"คืนได้ 7 วัน"}
                    ]}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(2)))
            .andExpect(jsonPath("$[0].type").value("shipping"))
            .andExpect(jsonPath("$[1].type").value("returns"));

        mvc.perform(get("/api/shops/" + slug + "/policies"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(2)))
            .andExpect(jsonPath("$[0].body").value("ส่งภายใน 2 วัน"));

        mvc.perform(put("/api/merchant/settings/policies?shopId=" + shopId)
                .header("Authorization", bearer(owner.token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"policies":[
                      {"type":"shipping","title":"ส่ง","body":"ส่งภายใน 2 วัน"},
                      {"type":"returns","title":"คืน","body":"   "}
                    ]}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].type").value("shipping"));
    }

    @Test
    void duplicateTypeReturns400() throws Exception {
        var owner = register("policy-dup@example.com", "0811000002");
        long shopId = openShop(owner, "ร้านซ้ำ", "Dup Shop", "1100000000002");
        approve(shopId);

        mvc.perform(put("/api/merchant/settings/policies?shopId=" + shopId)
                .header("Authorization", bearer(owner.token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"policies":[
                      {"type":"shipping","title":"A","body":"หนึ่ง"},
                      {"type":"SHIPPING","title":"B","body":"สอง"}
                    ]}
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void unapprovedShopPoliciesAreHiddenFromPublic() throws Exception {
        var owner = register("policy-pending@example.com", "0811000003");
        long shopId = openShop(owner, "ร้านรอ", "Pending Shop", "1100000000003");
        var slug = shops.findByShopId(shopId).orElseThrow().getSlug();

        mvc.perform(put("/api/merchant/settings/policies?shopId=" + shopId)
                .header("Authorization", bearer(owner.token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"policies":[{"type":"privacy","title":"ส่วนตัว","body":"เก็บข้อมูล"}]}
                    """))
            .andExpect(status().isOk());

        mvc.perform(get("/api/shops/" + slug + "/policies"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("SHOP_NOT_FOUND"));
    }

    @Test
    void publicShopPhoneIsNotOwnerPhone() throws Exception {
        var owner = register("policy-phone@example.com", "0811000004");
        long shopId = openShop(owner, "ร้านโทร", "Phone Shop", "1100000000004");
        approve(shopId);
        var shop = shops.findByShopId(shopId).orElseThrow();
        var slug = shop.getSlug();

        mvc.perform(get("/api/shops/" + slug))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.phone").value(nullValue()))
            .andExpect(jsonPath("$.email").value(nullValue()));

        mvc.perform(put("/api/merchant/settings?shopId=" + shopId)
                .header("Authorization", bearer(owner.token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "shopName":"ร้านโทร",
                      "shopNameEn":"Phone Shop",
                      "description":"สินค้า",
                      "category":"อาหารและเครื่องดื่ม",
                      "phone":"0899999999",
                      "email":"owner@example.com",
                      "website":"",
                      "lineId":"",
                      "facebookUrl":"",
                      "instagramUrl":"",
                      "tiktokUrl":"",
                      "address":"88 Silom Rd.",
                      "province":"กรุงเทพมหานคร",
                      "district":"บางรัก",
                      "subdistrict":"สีลม",
                      "postalCode":"10500",
                      "publicPhone":"021111111",
                      "publicEmail":"shop@example.com"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.phone").value("0899999999"))
            .andExpect(jsonPath("$.publicPhone").value("021111111"))
            .andExpect(jsonPath("$.publicEmail").value("shop@example.com"));

        mvc.perform(get("/api/shops/" + slug))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.phone").value("021111111"))
            .andExpect(jsonPath("$.email").value("shop@example.com"));
    }

    private record Account(UUID id, String token) {}

    private Account register(String email, String phone) throws Exception {
        var body = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
              {"firstName":"ทดสอบ","lastName":"สิทธิ์","email":"%s","phone":"%s","password":"Password1"}
              """.formatted(email, phone)))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return new Account(UUID.fromString(JsonPath.read(body, "$.user.id")), JsonPath.read(body, "$.accessToken"));
    }

    private Account platformAdmin(String email, String phone) throws Exception {
        var account = register(email, phone);
        platformRoles.save(PlatformRole.grant(account.id, PlatformRole.PLATFORM_ADMIN, null, "test"));
        return account;
    }

    private void approve(long shopId) throws Exception {
        var admin = platformAdmin("policy-admin-" + shopId + "@example.com", "082" + String.format("%07d", shopId % 10000000));
        mvc.perform(post("/api/admin/store-applications/" + shopId + "/approve")
                .header("Authorization", bearer(admin.token)))
            .andExpect(status().isOk());
    }

    private long openShop(Account owner, String shopName, String shopNameEn, String citizenId) throws Exception {
        mvc.perform(put("/api/merchant-applications/draft/shop").header("Authorization", bearer(owner.token)).contentType(MediaType.APPLICATION_JSON).content("""
              {"shopName":"%s","shopNameEn":"%s","description":"สินค้า","category":"อาหารและเครื่องดื่ม","saleType":"OWNER","website":"","address":"88 Silom Rd.","province":"กรุงเทพมหานคร","district":"บางรัก","subdistrict":"สีลม","postalCode":"10500"}
              """.formatted(shopName, shopNameEn))).andExpect(status().isOk());
        mvc.perform(put("/api/merchant-applications/draft/owner").header("Authorization", bearer(owner.token)).contentType(MediaType.APPLICATION_JSON).content("""
              {"ownerType":"PERSON","title":"นาย","firstName":"ทดสอบ","lastName":"สิทธิ์","citizenId":"%s","birthDate":"1980-08-15","nationality":"ไทย","phone":"0899999999","email":"owner@example.com","houseNo":"1","province":"กรุงเทพมหานคร","district":"บางรัก","subdistrict":"สีลม","postalCode":"10500","bankName":"KBANK","bankAccountNumber":"1234567890","bankAccountName":"ทดสอบ สิทธิ์"}
              """.formatted(citizenId))).andExpect(status().isOk());
        for (var type : List.of("IDENTITY", "ADDRESS")) {
            mvc.perform(multipart("/api/merchant-applications/draft/documents/" + type).file(png()).header("Authorization", bearer(owner.token)))
                .andExpect(status().isOk());
        }
        mvc.perform(post("/api/merchant-applications/draft/submit").header("Authorization", bearer(owner.token))).andExpect(status().isOk());
        return shops.findAll().stream().filter(shop -> shop.getName().equals(shopName)).findFirst().orElseThrow().getId();
    }

    private static MockMultipartFile png() {
        return new MockMultipartFile("file", "image.png", "image/png", new byte[]{(byte) 137, 80, 78, 71, 13, 10, 26, 10});
    }

    private static String bearer(String token) { return "Bearer " + token; }
}
