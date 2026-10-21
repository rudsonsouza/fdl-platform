package com.fdl.platform.shared.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class TraceIdFilterTest {

    private final TraceIdFilter filter = new TraceIdFilter();

    private record Result(MockHttpServletResponse response, String traceIdInsideChain) {
    }

    private Result run(String incomingHeader) throws Exception {
        var request = new MockHttpServletRequest();
        if (incomingHeader != null) {
            request.addHeader(TraceIdFilter.HEADER, incomingHeader);
        }
        var response = new MockHttpServletResponse();
        var seen = new AtomicReference<String>();
        filter.doFilter(request, response, new MockFilterChain() {
            @Override
            public void doFilter(jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse res) {
                seen.set(MDC.get(TraceIdFilter.MDC_KEY));
            }
        });
        return new Result(response, seen.get());
    }

    @Test
    void generatesATraceIdAvailableInTheMdcAndInTheResponseHeader() throws Exception {
        var result = run(null);

        assertThat(result.traceIdInsideChain()).isNotBlank();
        assertThat(result.response().getHeader(TraceIdFilter.HEADER)).isEqualTo(result.traceIdInsideChain());
    }

    @Test
    void keepsASafeTraceIdSentByTheClient() throws Exception {
        var result = run("abc12345-trace");

        assertThat(result.traceIdInsideChain()).isEqualTo("abc12345-trace");
        assertThat(result.response().getHeader(TraceIdFilter.HEADER)).isEqualTo("abc12345-trace");
    }

    @Test
    void replacesAnUnsafeOrTooShortClientTraceId() throws Exception {
        for (var unsafe : new String[] {"short", "has spaces and \r\nInjected: header", "a".repeat(65), "<script>alert(1)</script>"}) {
            var result = run(unsafe);

            assertThat(result.traceIdInsideChain()).isNotEqualTo(unsafe).matches("[0-9a-f-]{36}");
        }
    }

    @Test
    void clearsTheMdcAfterTheRequest() throws Exception {
        run(null);

        assertThat(MDC.get(TraceIdFilter.MDC_KEY)).isNull();
    }

    @Test
    void clearsTheMdcEvenWhenTheChainFails() {
        var request = new MockHttpServletRequest();
        var response = new MockHttpServletResponse();

        try {
            filter.doFilter(request, response, (req, res) -> {
                throw new IllegalStateException("boom");
            });
        } catch (Exception expected) {
            // a falha da cadeia segue para quem chamou
        }

        assertThat(MDC.get(TraceIdFilter.MDC_KEY)).isNull();
    }
}
