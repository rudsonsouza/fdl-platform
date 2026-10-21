package com.fdl.platform.shared.error;

import com.fdl.platform.shared.web.TraceIdFilter;
import java.net.URI;
import java.util.List;
import org.slf4j.MDC;
import org.springframework.http.ProblemDetail;

/** Monta o Problem Details padrão da API; usado pelo handler de exceções e pelos erros de segurança. */
public final class ProblemFactory {

    private ProblemFactory() {
    }

    public static ProblemDetail create(ErrorCode code, String detail, List<FieldErrorDetail> errors, String instance) {
        var problem = ProblemDetail.forStatusAndDetail(code.status(), detail);
        problem.setType(typeOf(code));
        problem.setTitle(code.title());
        if (instance != null) {
            problem.setInstance(URI.create(instance));
        }
        problem.setProperty("code", code.name());
        if (!errors.isEmpty()) {
            problem.setProperty("errors", errors);
        }
        problem.setProperty("traceId", MDC.get(TraceIdFilter.MDC_KEY));
        return problem;
    }

    public static URI typeOf(ErrorCode code) {
        return URI.create("urn:fdl:problem:" + code.name().toLowerCase().replace('_', '-'));
    }
}
