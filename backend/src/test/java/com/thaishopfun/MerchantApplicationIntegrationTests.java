package com.thaishopfun;

import com.jayway.jsonpath.JsonPath;
import com.thaishopfun.auth.OtpVerificationRepository;
import com.thaishopfun.auth.PasswordResetTokenRepository;
import com.thaishopfun.auth.RefreshTokenRepository;
import com.thaishopfun.auth.UserAccountRepository;
import com.thaishopfun.merchant.MerchantApplicationRepository;
import com.thaishopfun.merchant.MerchantDocumentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MerchantApplicationIntegrationTests {
    @Autowired MockMvc mvc;
    @Autowired MerchantDocumentRepository documents;
    @Autowired MerchantApplicationRepository applications;
    @Autowired RefreshTokenRepository refreshTokens;
    @Autowired PasswordResetTokenRepository passwordResetTokens;
    @Autowired OtpVerificationRepository otpVerifications;
    @Autowired UserAccountRepository users;

    @BeforeEach void clean(){documents.deleteAll();applications.deleteAll();otpVerifications.deleteAll();passwordResetTokens.deleteAll();refreshTokens.deleteAll();users.deleteAll();}

    @Test void savesUploadsAndSubmitsRealApplication() throws Exception {
        var registered=mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
          {"firstName":"สมชาย","lastName":"ใจดี","email":"merchant@example.com","phone":"0812345678","password":"Password1"}
          """)).andExpect(status().isCreated()).andReturn();
        String token=JsonPath.read(registered.getResponse().getContentAsString(),"$.accessToken");

        mvc.perform(put("/api/merchant-applications/draft/shop").header("Authorization","Bearer "+token).contentType(MediaType.APPLICATION_JSON).content("""
          {"shopName":"ร้านทดสอบ","shopNameEn":"Test Shop","description":"สินค้าคุณภาพ","category":"สินค้าไทย","saleType":"OWNER","website":"https://example.com","address":"88 Silom Rd.","province":"กรุงเทพมหานคร","district":"บางรัก","subdistrict":"สีลม","postalCode":"10500"}
          """)).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("DRAFT"));

        mvc.perform(put("/api/merchant-applications/draft/owner").header("Authorization","Bearer "+token).contentType(MediaType.APPLICATION_JSON).content("""
          {"ownerType":"PERSON","title":"นาย","firstName":"สมชาย","lastName":"ใจดี","citizenId":"1234567890123","birthDate":"1980-08-15","nationality":"ไทย","phone":"0812345678","email":"merchant@example.com","houseNo":"123/45","villageNo":"5","building":"","alley":"สุขุมวิท 101","road":"สุขุมวิท","province":"กรุงเทพมหานคร","district":"บางนา","subdistrict":"บางนา","postalCode":"10260","bankName":"KBANK","bankAccountNumber":"1234567890","bankAccountName":"สมชาย ใจดี"}
          """)).andExpect(status().isOk()).andExpect(jsonPath("$.citizenIdMasked").value("*********0123"));

        upload(token,"IDENTITY","identity.png");
        upload(token,"ADDRESS","address.png");

        mvc.perform(post("/api/merchant-applications/draft/submit").header("Authorization","Bearer "+token))
          .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("SUBMITTED"))
          .andExpect(jsonPath("$.referenceNo").isNotEmpty()).andExpect(jsonPath("$.documents.length()").value(2));
        mvc.perform(post("/api/merchant-applications/draft/submit").header("Authorization","Bearer "+token))
          .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("APPLICATION_ALREADY_SUBMITTED"));
    }

    private void upload(String token,String type,String filename) throws Exception {
        var file=new MockMultipartFile("file",filename,"image/png",new byte[]{(byte)137,80,78,71,13,10,26,10});
        mvc.perform(multipart("/api/merchant-applications/draft/documents/"+type).file(file).header("Authorization","Bearer "+token))
          .andExpect(status().isOk()).andExpect(jsonPath("$.documents[0].originalFilename").isNotEmpty());
    }
}
