package com.thaishopfun.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Social login IdP credentials under {@code app.auth.oauth.*}.
 * A provider is enabled when its client/app id is non-blank (Facebook also needs the app secret).
 */
@ConfigurationProperties("app.auth.oauth")
public record OAuthProperties(
    String googleClientId,
    String facebookAppId,
    String facebookAppSecret
) {
    public boolean enabled(OAuthProvider provider) {
        return switch (provider) {
            case GOOGLE -> nonBlank(googleClientId);
            case FACEBOOK -> nonBlank(facebookAppId) && nonBlank(facebookAppSecret);
        };
    }

    public AuthDtos.OAuthProvidersResponse providers() {
        return new AuthDtos.OAuthProvidersResponse(
            enabled(OAuthProvider.GOOGLE),
            enabled(OAuthProvider.FACEBOOK)
        );
    }

    private static boolean nonBlank(String value) {
        return value != null && !value.isBlank();
    }
}
