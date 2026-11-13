package com.thaishopfun.merchant.plan;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Platform bank account shown when a shop pays for a plan upgrade.
 * Configure via {@code app.platform-billing.*} / env vars — there is no shop-owned account for this flow.
 */
@ConfigurationProperties(prefix = "app.platform-billing")
public class PlatformBillingProperties {
    private String bankName = "กสิกรไทย";
    private String accountName = "ThaiShopFun Co., Ltd.";
    private String accountNumber = "1234567890";
    private String promptpay = "";
    private String displayName = "ThaiShopFun — ค่าแพ็กเกจร้านค้า";

    public String getBankName() { return bankName; }
    public void setBankName(String bankName) { this.bankName = bankName; }
    public String getAccountName() { return accountName; }
    public void setAccountName(String accountName) { this.accountName = accountName; }
    public String getAccountNumber() { return accountNumber; }
    public void setAccountNumber(String accountNumber) { this.accountNumber = accountNumber; }
    public String getPromptpay() { return promptpay; }
    public void setPromptpay(String promptpay) { this.promptpay = promptpay; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
}
