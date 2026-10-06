package com.thaishopfun;

import com.thaishopfun.auth.AuthException;
import com.thaishopfun.auth.authz.AccessRule;
import com.thaishopfun.auth.authz.AuthorizationInterceptor;
import com.thaishopfun.auth.authz.PermissionMatrix;
import com.thaishopfun.auth.authz.RequirePermission;
import com.thaishopfun.auth.authz.RequireShopPermission;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Guard: every controller endpoint of the application must declare exactly one access rule, and every permission
 * it names must exist (active) in the database catalog with a matching scope and be granted to at least one role.
 * A new endpoint without @PublicEndpoint / @RequirePermission / @RequireShopPermission / @FilterByShopPermission
 * makes this test fail (and is denied with 403 at runtime).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class EndpointAccessRuleCoverageTests {
    @Autowired ApplicationContext context;
    @Autowired PermissionMatrix matrix;
    @Autowired AuthorizationInterceptor interceptor;

    @Test void everyEndpointDeclaresAValidPermissionRuleFromTheDatabaseCatalog() {
        matrix.invalidate();
        var snapshot = matrix.current();
        var granted = new HashSet<String>();
        snapshot.permissionsByRole().values().forEach(granted::addAll);
        var problems = new ArrayList<String>();
        var table = new TreeMap<String, String>();
        var governed = 0;
        for (var mapping : context.getBeansOfType(RequestMappingHandlerMapping.class).values()) {
            for (var entry : mapping.getHandlerMethods().entrySet()) {
                var handler = entry.getValue();
                if (!AccessRule.governs(handler)) continue;
                governed++;
                var endpoint = entry.getKey().getMethodsCondition() + " " + entry.getKey().getPatternValues();
                AccessRule rule;
                try {
                    rule = AccessRule.resolve(handler);
                } catch (IllegalStateException exception) {
                    problems.add(endpoint + ": " + exception.getMessage());
                    continue;
                }
                if (rule == null) {
                    problems.add(endpoint + " (" + handler.getShortLogMessage() + ") has no access rule");
                    continue;
                }
                table.put(endpoint, rule.kind() + (rule.permission() == null ? "" : " " + rule.permission()));
                if (rule.kind() == AccessRule.Kind.PUBLIC) continue;
                var scope = snapshot.scopeByPermission().get(rule.permission());
                if (scope == null) {
                    problems.add(endpoint + ": permission " + rule.permission() + " is not an active code in auth_permissions");
                    continue;
                }
                var shopScoped = rule.kind() == AccessRule.Kind.SHOP || rule.kind() == AccessRule.Kind.SHOP_FILTER;
                if (shopScoped && !"STORE".equals(scope)) {
                    problems.add(endpoint + ": shop rule uses " + rule.permission() + " whose scope is " + scope + " (expected STORE)");
                }
                if (!shopScoped && "STORE".equals(scope)) {
                    problems.add(endpoint + ": @RequirePermission uses STORE-scope " + rule.permission() + " (use @RequireShopPermission)");
                }
                if (!granted.contains(rule.permission())) {
                    problems.add(endpoint + ": no role is granted " + rule.permission() + " in auth_role_permissions (endpoint unreachable)");
                }
                if (rule.kind() == AccessRule.Kind.SHOP && !hasParameter(handler, rule.shopParam())) {
                    problems.add(endpoint + ": shop parameter '" + rule.shopParam() + "' is not a @RequestParam/@PathVariable of the method");
                }
            }
        }
        assertTrue(governed > 20, "expected the application's endpoints to be discovered, found " + governed);
        if (!problems.isEmpty()) fail("Access rule problems:\n  " + String.join("\n  ", problems));
        System.out.println("Endpoint access rules:");
        table.forEach((endpoint, rule) -> System.out.println("  " + endpoint + " -> " + rule));
    }

    @Test void endpointWithoutAnAccessRuleIsDeniedAtRuntime() throws Exception {
        var handler = new HandlerMethod(new UndeclaredController(), UndeclaredController.class.getMethod("open"));
        var error = assertThrows(AuthException.class,
            () -> interceptor.preHandle(new MockHttpServletRequest("GET", "/x"), new MockHttpServletResponse(), handler));
        assertEquals("ACCESS_RULE_MISSING", error.getCode());
        assertEquals(403, error.getStatus().value());
    }

    @Test void endpointWithTwoRulesIsRejected() throws Exception {
        var handler = new HandlerMethod(new UndeclaredController(), UndeclaredController.class.getMethod("ambiguous"));
        assertThrows(IllegalStateException.class, () -> AccessRule.resolve(handler));
        var error = assertThrows(AuthException.class,
            () -> interceptor.preHandle(new MockHttpServletRequest("GET", "/y"), new MockHttpServletResponse(), handler));
        assertEquals("ACCESS_RULE_MISSING", error.getCode());
    }

    private static boolean hasParameter(HandlerMethod handler, String name) {
        var discoverer = new DefaultParameterNameDiscoverer();
        var names = discoverer.getParameterNames(handler.getMethod());
        var parameters = handler.getMethodParameters();
        Set<String> bound = new HashSet<>();
        for (var i = 0; i < parameters.length; i++) {
            var parameter = parameters[i];
            var requestParam = parameter.getParameterAnnotation(RequestParam.class);
            var pathVariable = parameter.getParameterAnnotation(PathVariable.class);
            if (requestParam == null && pathVariable == null) continue;
            var explicit = requestParam != null ? requestParam.name() : pathVariable.name();
            if (explicit.isEmpty()) explicit = requestParam != null ? requestParam.value() : pathVariable.value();
            bound.add(!explicit.isEmpty() ? explicit : names == null ? "" : names[i]);
        }
        return bound.contains(name);
    }

    /** Not a Spring bean: only used to build HandlerMethods for the deny-by-default checks. */
    static class UndeclaredController {
        public String open() { return "no rule"; }

        @RequirePermission("BUYER_PROFILE_READ")
        @RequireShopPermission("SHOP_READ")
        public String ambiguous() { return "two rules"; }
    }
}
