package com.thaishopfun.audit;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

public class UserActionAuditFilter extends OncePerRequestFilter {
    private final UserActionAudit audit;

    public UserActionAuditFilter(UserActionAudit audit) {
        this.audit = audit;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
        chain.doFilter(request, response);
        if (request.getDispatcherType() != DispatcherType.REQUEST) return;
        audit.record(request, response);
    }
}
