package ke.ledgercore.rate;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import ke.ledgercore.service.AuditService;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class RateLimitFilter extends OncePerRequestFilter {

    private final AuditService auditService;
    private final boolean enabled;
    private final Map<String, Bucket> bucketCache = new ConcurrentHashMap<>();

    public RateLimitFilter(AuditService auditService, boolean enabled) {
        this.auditService = auditService;
        this.enabled = enabled;
    }

    public RateLimitFilter(AuditService auditService) {
        this(auditService, true);
    }

    private Bucket resolveBucket(HttpServletRequest request) {
        org.springframework.security.core.Authentication auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        String user;
        if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getName())) {
            user = auth.getName();
        } else if (request.getUserPrincipal() != null) {
            user = request.getUserPrincipal().getName();
        } else {
            user = request.getRemoteAddr();
        }
        String uri = request.getRequestURI();
        String method = request.getMethod();

        String bucketType;
        Bandwidth limit;

        if (uri.contains("/auth/login")) {
            bucketType = "auth";
            limit = Bandwidth.classic(10, Refill.greedy(10, Duration.ofMinutes(1)));
        } else if ("POST".equalsIgnoreCase(method) && (uri.contains("/transfers") || uri.contains("/disbursements") || uri.contains("/ledger/post"))) {
            // 10 transfers/min per user
            bucketType = "transfers";
            limit = Bandwidth.classic(10, Refill.greedy(10, Duration.ofMinutes(1)));
        } else if ("GET".equalsIgnoreCase(method)) {
            // 100 reads/min per user
            bucketType = "reads";
            limit = Bandwidth.classic(100, Refill.greedy(100, Duration.ofMinutes(1)));
        } else {
            bucketType = "general";
            limit = Bandwidth.classic(100, Refill.greedy(100, Duration.ofMinutes(1)));
        }

        String cacheKey = user + ":" + bucketType;
        return bucketCache.computeIfAbsent(cacheKey, k -> Bucket.builder().addLimit(limit).build());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (!enabled) {
            filterChain.doFilter(request, response);
            return;
        }

        String uri = request.getRequestURI();
        if (uri.startsWith("/actuator") || uri.startsWith("/swagger-ui") || uri.startsWith("/v3/api-docs")) {
            filterChain.doFilter(request, response);
            return;
        }

        Bucket bucket = resolveBucket(request);
        var probe = bucket.tryConsumeAndReturnRemaining(1);
        if (probe.isConsumed()) {
            response.setHeader("X-RateLimit-Remaining", Long.toString(probe.getRemainingTokens()));
            filterChain.doFilter(request, response);
        } else {
            long waitSec = Math.max(1, probe.getNanosToWaitForRefill() / 1_000_000_000L);
            response.setHeader("Retry-After", Long.toString(waitSec));
            response.setHeader("X-RateLimit-Remaining", "0");
            org.springframework.security.core.Authentication auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
            String actor = (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getName()))
                    ? auth.getName()
                    : (request.getUserPrincipal() != null ? request.getUserPrincipal().getName() : "anonymous");
            String clientIp = request.getRemoteAddr();
            String traceId = MDC.get("traceId");
            auditService.audit("RATE_LIMIT", "RATE_LIMIT_REJECTED", "FAILURE", null, actor, "UNKNOWN", clientIp, traceId, "Rate limit exceeded for " + request.getRequestURI());
            response.sendError(429, "Too many requests. Rate limit exceeded.");
        }
    }
}
