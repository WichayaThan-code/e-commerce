package com.thaishopfun.merchant;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.merchant")
public record MerchantProperties(String storageRoot, String dataEncryptionSecret) {}
