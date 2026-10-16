package ke.ledgercore.audit;

import jakarta.servlet.http.HttpServletRequest;
import ke.ledgercore.service.AuditService;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.MDC;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Aspect
@Component
public class AuditAspect {

    private final AuditService auditService;

    public AuditAspect(AuditService auditService) {
        this.auditService = auditService;
    }

    @Around("@annotation(auditable)")
    public Object auditMethod(ProceedingJoinPoint joinPoint, Auditable auditable) throws Throwable {
        String actor = getActor();
        String ip = getClientIp();
        String userAgent = getUserAgent();
        String traceId = MDC.get("traceId");
        if (traceId == null) {
            traceId = MDC.get("correlationId");
        }

        Object[] args = joinPoint.getArgs();
        Object beforeState = args != null && args.length > 0 ? args[0] : null;

        Object result;
        try {
            result = joinPoint.proceed();
        } catch (Throwable t) {
            auditService.recordAuditLog(
                    actor,
                    auditable.action() + "_FAILED",
                    auditable.entityType(),
                    null,
                    beforeState,
                    "ERROR: " + t.getMessage(),
                    ip,
                    userAgent,
                    traceId
            );
            throw t;
        }

        auditService.recordAuditLog(
                actor,
                auditable.action(),
                auditable.entityType(),
                null,
                beforeState,
                result,
                ip,
                userAgent,
                traceId
        );

        return result;
    }

    private String getActor() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getName())) {
            return auth.getName();
        }
        return "system";
    }

    private String getClientIp() {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs != null) {
            HttpServletRequest req = attrs.getRequest();
            String xf = req.getHeader("X-Forwarded-For");
            return (xf != null && !xf.isBlank()) ? xf.split(",")[0].trim() : req.getRemoteAddr();
        }
        return null;
    }

    private String getUserAgent() {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs != null) {
            return attrs.getRequest().getHeader("User-Agent");
        }
        return null;
    }
}
