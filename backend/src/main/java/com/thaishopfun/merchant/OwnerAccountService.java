package com.thaishopfun.merchant;

import com.thaishopfun.auth.AuthException;
import com.thaishopfun.auth.UserAccount;
import com.thaishopfun.auth.UserAccountRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class OwnerAccountService {
    private final OwnerRepository owners;
    private final OwnerAuditLogRepository audits;
    private final ShopRepository shops;
    private final UserAccountRepository users;
    private final SensitiveDataCipher cipher;

    public OwnerAccountService(OwnerRepository owners, OwnerAuditLogRepository audits, ShopRepository shops, UserAccountRepository users, SensitiveDataCipher cipher) {
        this.owners = owners;
        this.audits = audits;
        this.shops = shops;
        this.users = users;
        this.cipher = cipher;
    }

    @Transactional(readOnly = true)
    public List<MerchantDtos.MerchantOwnerResponse> list(UUID userId) {
        return owners.findByUser_IdOrderByCreatedAtAsc(userId).stream().map(this::toResponse).toList();
    }

    /** Oldest owner profile for this user, if any (used to prefill a second-shop draft). */
    @Transactional(readOnly = true)
    public java.util.Optional<Owner> primaryOwned(UUID userId) {
        return owners.findByUser_IdOrderByCreatedAtAsc(userId).stream().findFirst();
    }

    @Transactional
    public Owner upsert(UUID userId, MerchantDtos.OwnerRequest request) {
        var user = requireUser(userId);
        assertCitizenAvailable(userId, request.citizenId());
        var hash = CitizenIdHasher.hash(request.citizenId());
        var citizen = cipher.encrypt(digits(request.citizenId()));
        var bank = cipher.encrypt(digits(request.bankAccountNumber()));
        var match = hash == null ? null : owners.findByCitizenIdHash(hash).orElse(null);
        if (match != null) {
            recordSensitiveChange(match, user, request, hash);
            match.apply(request, citizen, hash, bank, pendingReview(match, request, hash));
            return owners.save(match);
        }
        return owners.save(new Owner(user, request, citizen, hash, bank));
    }

    @Transactional
    public MerchantDtos.MerchantOwnerResponse update(UUID userId, UUID ownerId, MerchantDtos.OwnerRequest request) {
        var owner = owners.findById(ownerId).orElseThrow(() -> new AuthException(HttpStatus.NOT_FOUND, "OWNER_NOT_FOUND", "ไม่พบข้อมูลเจ้าของ"));
        if (owner.getUser() == null || !userId.equals(owner.getUser().getId())) {
            throw new AuthException(HttpStatus.FORBIDDEN, "OWNER_FORBIDDEN", "ไม่มีสิทธิ์แก้ไขเจ้าของนี้");
        }
        var hash = CitizenIdHasher.hash(request.citizenId());
        var match = hash == null ? null : owners.findByCitizenIdHash(hash).orElse(null);
        if (match != null && !match.getId().equals(owner.getId())) {
            if (!userId.equals(match.getUser().getId())) {
                throw new AuthException(HttpStatus.CONFLICT, "CITIZEN_ID_TAKEN", "เลขบัตรประชาชนนี้ถูกใช้สมัครร้านค้าแล้ว", "citizenId");
            }
            throw new AuthException(HttpStatus.CONFLICT, "OWNER_EXISTS", "เลขบัตรนี้เป็นเจ้าของอีกโปรไฟล์ของคุณอยู่แล้ว", "citizenId");
        }
        var user = requireUser(userId);
        recordSensitiveChange(owner, user, request, hash);
        owner.apply(request, cipher.encrypt(digits(request.citizenId())), hash, cipher.encrypt(digits(request.bankAccountNumber())), pendingReview(owner, request, hash));
        return toResponse(owners.save(owner));
    }

    @Transactional(readOnly = true)
    public void assertCitizenAvailable(UUID userId, String citizenId) {
        var hash = CitizenIdHasher.hash(citizenId);
        var match = hash == null ? null : owners.findByCitizenIdHash(hash).orElse(null);
        if (match != null && match.getUser() != null && !userId.equals(match.getUser().getId())) {
            throw new AuthException(HttpStatus.CONFLICT, "CITIZEN_ID_TAKEN", "เลขบัตรประชาชนนี้ถูกใช้สมัครร้านค้าแล้ว", "citizenId");
        }
    }

    @Transactional(readOnly = true)
    public Owner requireOwned(UUID userId, UUID ownerId) {
        var owner = owners.findById(ownerId).orElseThrow(() -> new AuthException(HttpStatus.NOT_FOUND, "OWNER_NOT_FOUND", "ไม่พบข้อมูลเจ้าของ"));
        if (owner.getUser() == null || !userId.equals(owner.getUser().getId())) {
            throw new AuthException(HttpStatus.FORBIDDEN, "OWNER_FORBIDDEN", "ไม่มีสิทธิ์ใช้เจ้าของนี้");
        }
        return owner;
    }

    private MerchantDtos.MerchantOwnerResponse toResponse(Owner owner) {
        String citizen = null;
        String bank = null;
        try {
            if (owner.getCitizenIdEncrypted() != null) citizen = digits(cipher.decrypt(owner.getCitizenIdEncrypted()));
        } catch (RuntimeException ignored) { /* rotated secret: keep masked-only */ }
        try {
            if (owner.getBankAccountEncrypted() != null) bank = digits(cipher.decrypt(owner.getBankAccountEncrypted()));
        } catch (RuntimeException ignored) { /* rotated secret */ }
        return new MerchantDtos.MerchantOwnerResponse(
            owner.getId(), owner.getOwnerType(), owner.getTitle(), owner.getFirstName(), owner.getLastName(),
            cipher.mask(owner.getCitizenIdEncrypted()), owner.getPhone(), owner.getEmail(), owner.getSensitiveReview(),
            citizen, owner.getBirthDate(), owner.getNationality(),
            owner.getHouseNo(), owner.getVillageNo(), owner.getBuilding(), owner.getAlley(), owner.getRoad(),
            owner.getProvince(), owner.getDistrict(), owner.getSubdistrict(), owner.getPostalCode(),
            owner.getBankName(), bank, owner.getBankAccountName()
        );
    }

    private boolean pendingReview(Owner owner, MerchantDtos.OwnerRequest request, String hash) {
        return hasApprovedShop(owner) && sensitiveChanged(owner, request, hash);
    }

    private void recordSensitiveChange(Owner owner, UserAccount user, MerchantDtos.OwnerRequest request, String hash) {
        if (!hasApprovedShop(owner)) return;
        if (owner.getCitizenIdHash() != null && hash != null && !owner.getCitizenIdHash().equals(hash)) {
            audits.save(new OwnerAuditLog(owner, user, "citizen_id"));
        }
        var currentBank = owner.getBankAccountEncrypted() == null ? "" : cipher.decrypt(owner.getBankAccountEncrypted());
        if (!digits(request.bankAccountNumber()).equals(currentBank == null ? "" : currentBank.replaceAll("[^0-9]", ""))) {
            audits.save(new OwnerAuditLog(owner, user, "bank_account"));
        }
    }

    private boolean sensitiveChanged(Owner owner, MerchantDtos.OwnerRequest request, String hash) {
        var citizenChanged = owner.getCitizenIdHash() != null && hash != null && !owner.getCitizenIdHash().equals(hash);
        var currentBank = owner.getBankAccountEncrypted() == null ? "" : cipher.decrypt(owner.getBankAccountEncrypted());
        var bankChanged = !digits(request.bankAccountNumber()).equals(currentBank == null ? "" : currentBank.replaceAll("[^0-9]", ""));
        return citizenChanged || bankChanged;
    }

    private boolean hasApprovedShop(Owner owner) {
        return shops.findByOwner_IdOrderByShopIdAsc(owner.getId()).stream().anyMatch(shop -> shop.getStatus() == ShopStatus.APPROVED);
    }

    private UserAccount requireUser(UUID userId) {
        return users.findById(userId).orElseThrow(() -> new AuthException(HttpStatus.UNAUTHORIZED, "USER_NOT_FOUND", "ไม่พบบัญชีผู้ใช้"));
    }

    private static String digits(String value) { return value.replaceAll("[^0-9]", ""); }
}
