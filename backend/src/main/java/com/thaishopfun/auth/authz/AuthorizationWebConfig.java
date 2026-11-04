package com.thaishopfun.auth.authz;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class AuthorizationWebConfig implements WebMvcConfigurer {
    private final AuthorizationInterceptor interceptor;
    private final EmailNotVerifiedInterceptor emailNotVerifiedInterceptor;

    public AuthorizationWebConfig(AuthorizationInterceptor interceptor, EmailNotVerifiedInterceptor emailNotVerifiedInterceptor) {
        this.interceptor = interceptor;
        this.emailNotVerifiedInterceptor = emailNotVerifiedInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(emailNotVerifiedInterceptor).addPathPatterns("/api/**").excludePathPatterns("/error");
        registry.addInterceptor(interceptor).addPathPatterns("/**").excludePathPatterns("/error");
    }
}
