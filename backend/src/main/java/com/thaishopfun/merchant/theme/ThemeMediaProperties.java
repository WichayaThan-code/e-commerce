package com.thaishopfun.merchant.theme;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Per-shop limits for the CUSTOM theme media library (picked up by {@code @ConfigurationPropertiesScan}). */
@ConfigurationProperties("app.merchant.theme-media")
public record ThemeMediaProperties(
    @DefaultValue("209715200") long maxTotalBytes,
    @DefaultValue("200") int maxImages,
    @DefaultValue("5") int maxVideos,
    @DefaultValue("4") int maxFonts,
    @DefaultValue("5242880") long maxImageBytes,
    @DefaultValue("20971520") long maxVideoBytes,
    @DefaultValue("1048576") long maxFontBytes,
    @DefaultValue("4000") int maxImageDimension
) {}
