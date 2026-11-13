package com.thaishopfun.auth.authz;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpMethod;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Spring Security matcher for every endpoint annotated {@link PublicEndpoint}: those are permitAll, everything
 * else requires a valid access token. Built lazily from the MVC handler mappings so the annotation is the single
 * source of truth (no second list of public URLs to keep in sync).
 */
@Component
public class PublicEndpointRegistry implements RequestMatcher {
    private final ApplicationContext context;
    private volatile List<RequestMatcher> matchers;

    public PublicEndpointRegistry(ApplicationContext context) {
        this.context = context;
    }

    @Override
    public boolean matches(HttpServletRequest request) {
        for (var matcher : matchers()) {
            if (matcher.matches(request)) return true;
        }
        return false;
    }

    public List<RequestMatcher> matchers() {
        var current = matchers;
        if (current == null) {
            synchronized (this) {
                current = matchers;
                if (current == null) {
                    current = build();
                    matchers = current;
                }
            }
        }
        return current;
    }

    private List<RequestMatcher> build() {
        var builder = PathPatternRequestMatcher.withDefaults();
        var result = new ArrayList<RequestMatcher>();
        for (var mapping : context.getBeansOfType(RequestMappingHandlerMapping.class).values()) {
            mapping.getHandlerMethods().forEach((info, handler) -> {
                if (!AccessRule.governs(handler)) return;
                var rule = AccessRule.resolve(handler);
                if (rule == null || rule.kind() != AccessRule.Kind.PUBLIC) return;
                var methods = info.getMethodsCondition().getMethods();
                for (var pattern : patterns(info)) {
                    if (methods.isEmpty()) {
                        result.add(builder.matcher(pattern));
                    } else {
                        for (var method : methods) {
                            var httpMethod = HttpMethod.valueOf(method.name());
                            result.add(builder.matcher(httpMethod, pattern));
                            if (httpMethod == HttpMethod.GET) result.add(builder.matcher(HttpMethod.HEAD, pattern));
                        }
                    }
                }
            });
        }
        return List.copyOf(result);
    }

    private static Set<String> patterns(RequestMappingInfo info) {
        return info.getPatternValues();
    }
}
