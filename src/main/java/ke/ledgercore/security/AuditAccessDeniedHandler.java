package ke.ledgercore.security;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import ke.ledgercore.service.AuditService;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class AuditAccessDeniedHandler implements AccessDeniedHandler {

    @Autowired
    private AuditService auditService;

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException accessDeniedException)
            throws IOException, ServletException {
        // Extract MDC values for traceId and clientIp if present
        String traceId = MDC.get("traceId");
        String clientIp = MDC.get("clientIp");
        String actor = request.getUserPrincipal() != null ? request.getUserPrincipal().getName() : "anonymous";
        String role = request.isUserInRole("ADMIN") ? "ADMIN" : request.isUserInRole("TELLER") ? "TELLER" : request.isUserInRole("CUSTOMER") ? "CUSTOMER" : "UNKNOWN";
        // Audit the denial
        if (auditService != null) {
            auditService.audit("ACCESS", "ACCESS_DENIED", "FAILURE", null, actor, role, clientIp, traceId, "Access denied to " + request.getRequestURI());
        }
        // Proceed with default handling (403)
        response.sendError(HttpServletResponse.SC_FORBIDDEN, "Access Denied");
    }
}
