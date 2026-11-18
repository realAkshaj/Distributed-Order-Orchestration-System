package com.orderorchestration.common.outbox;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapGetter;

import java.util.Map;

final class TraceContext {

    private static final String HEADER = "traceparent";
    private static final TextMapGetter<Map<String, String>> GETTER = new TextMapGetter<>() {
        @Override
        public Iterable<String> keys(Map<String, String> carrier) {
            return carrier.keySet();
        }

        @Override
        public String get(Map<String, String> carrier, String key) {
            return carrier == null ? null : carrier.get(key);
        }
    };

    private TraceContext() {
    }

    static String current() {
        SpanContext ctx = Span.current().getSpanContext();
        if (!ctx.isValid()) {
            return null;
        }
        return "00-" + ctx.getTraceId() + "-" + ctx.getSpanId() + "-" + ctx.getTraceFlags().asHex();
    }

    static Context restore(String traceparent) {
        if (traceparent == null) {
            return Context.root();
        }
        return W3CTraceContextPropagator.getInstance().extract(Context.root(), Map.of(HEADER, traceparent), GETTER);
    }
}
