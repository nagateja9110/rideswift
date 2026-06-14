package com.rideswift.config;

import com.rideswift.security.SecurityUtils;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Populates the logging MDC with a per-request id and the authenticated user id
 * so Log4j2 can emit correlated, structured log lines. Runs after the security
 * filter chain so the user id is available.
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class MdcLoggingFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        try {
            MDC.put("requestId", UUID.randomUUID().toString());
            UUID userId = SecurityUtils.currentUserId();
            MDC.put("userId", userId != null ? userId.toString() : "-");
            filterChain.doFilter(request, response);
        } finally {
            MDC.clear();
        }
    }
}
