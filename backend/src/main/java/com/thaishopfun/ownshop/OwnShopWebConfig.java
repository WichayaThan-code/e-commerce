package com.thaishopfun.ownshop;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Registers OwnShopCheckoutInterceptor for POST /api/checkout only (path + method check in the interceptor). */
@Configuration
public class OwnShopWebConfig implements WebMvcConfigurer {
    private final OwnShopCheckoutInterceptor interceptor;

    public OwnShopWebConfig(OwnShopCheckoutInterceptor interceptor) {
        this.interceptor = interceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(interceptor).addPathPatterns("/api/checkout");
    }
}
