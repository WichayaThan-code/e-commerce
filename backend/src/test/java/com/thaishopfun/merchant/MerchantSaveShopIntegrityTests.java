package com.thaishopfun.merchant;

import com.thaishopfun.admin.notification.AdminNotificationService;
import com.thaishopfun.auth.AuthException;
import com.thaishopfun.auth.UserAccount;
import com.thaishopfun.auth.UserAccountRepository;
import com.thaishopfun.common.IntegritySqlStates;
import com.thaishopfun.merchant.delivery.ShopLifecycleEmailPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;

import java.sql.SQLException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MerchantSaveShopIntegrityTests {
    @Mock MerchantApplicationRepository applications;
    @Mock MerchantDocumentRepository documents;
    @Mock UserAccountRepository users;
    @Mock SensitiveDataCipher cipher;
    @Mock MerchantStorageService storage;
    @Mock ShopCatalogService shops;
    @Mock OwnerAccountService ownerAccounts;
    @Mock ProductCategoryService categories;
    @Mock MerchantDraftFactory drafts;
    @Mock ShopRepository shopRepository;
    @Mock AdminNotificationService adminNotifications;
    @Mock ShopLifecycleEmailPublisher shopMails;

    MerchantApplicationService service;
    MerchantApplication draft;

    @BeforeEach
    void setUp() {
        service = new MerchantApplicationService(
            applications, documents, users, cipher, storage, shops, ownerAccounts,
            categories, drafts, shopRepository, adminNotifications, shopMails);
        UserAccount user = new UserAccount("ทดสอบ", "ร้าน", "save-shop@example.com", "0810000099", "hash");
        draft = new MerchantApplication(user);
        when(applications.findFirstByUser_IdAndStatusOrderByUpdatedAtDesc(any(), any())).thenReturn(Optional.of(draft));
        when(applications.existsByShopNameIgnoreCaseAndIdNot(anyString(), any())).thenReturn(false);
    }

    @Test
    void nonUniqueIntegrityErrorIsNotMappedToShopExists() {
        when(applications.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException(
            "too long",
            new SQLException("value too long for type character varying(100)", IntegritySqlStates.VALUE_TOO_LONG)));

        DataIntegrityViolationException thrown = assertThrows(DataIntegrityViolationException.class,
            () -> service.saveShop(draft.getUser().getId(), sampleShop("ร้านทดสอบ")));
        assertEquals(IntegritySqlStates.VALUE_TOO_LONG, IntegritySqlStates.sqlState(thrown));
    }

    @Test
    void uniqueShopNameConstraintMapsToShopExists() {
        when(applications.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException(
            "unique",
            new SQLException("duplicate key value violates unique constraint \"uq_merchant_applications_shop_name\"", IntegritySqlStates.UNIQUE)));

        AuthException thrown = assertThrows(AuthException.class,
            () -> service.saveShop(draft.getUser().getId(), sampleShop("ร้านซ้ำ")));
        assertEquals(HttpStatus.CONFLICT, thrown.getStatus());
        assertEquals("SHOP_EXISTS", thrown.getCode());
        assertEquals("shopName", thrown.getField());
    }

    private static MerchantDtos.ShopRequest sampleShop(String name) {
        return new MerchantDtos.ShopRequest(
            name, "Test", "desc", "แฟชั่นและเสื้อผ้า", SaleType.OWNER,
            null, null, null, null, null,
            "123 ถนนทดสอบ", "กรุงเทพมหานคร", "บางรัก", "สีลม", "10500");
    }
}
