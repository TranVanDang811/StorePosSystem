package com.possystem.backend.common.observability;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.propagation.Propagator;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Coordinates OpenTelemetry/Micrometer Tracing span lifecycle across HTTP requests,
 * Redis cache lookups, Transactional Outbox async workers, Apache Kafka transport,
 * and external provider integrations (Cloudinary / Payment Webhooks).
 * Automatically enforces PII/secret redaction on every span attribute.
 */
@Component
public class PosTracingHelper {

    @FunctionalInterface
    public interface CheckedSupplier<T, E extends Throwable> {
        T get() throws E;
    }

    private final ObjectProvider<Tracer> tracerProvider;
    private final ObjectProvider<Propagator> propagatorProvider;

    public PosTracingHelper(
            ObjectProvider<Tracer> tracerProvider,
            ObjectProvider<Propagator> propagatorProvider) {
        this.tracerProvider = tracerProvider;
        this.propagatorProvider = propagatorProvider;
    }

    /**
     * Captures the currently active W3C `traceparent` header so async boundaries (Outbox/Kafka)
     * can resume the distributed trace asynchronously without carrying secrets or PII.
     */
    public String currentTraceParent() {
        Tracer tracer = tracerProvider != null ? tracerProvider.getIfAvailable() : null;
        if (tracer == null) {
            return null;
        }
        Span current = tracer.currentSpan();
        if (current == null || current.context() == null) {
            return null;
        }
        TraceContext ctx = current.context();
        Propagator propagator = propagatorProvider != null ? propagatorProvider.getIfAvailable() : null;
        if (propagator != null) {
            Map<String, String> carrier = new HashMap<>(4);
            propagator.inject(ctx, carrier, Map::put);
            String injected = TraceSanitizer.sanitizeTraceParent(carrier.get("traceparent"));
            if (injected != null) {
                return injected;
            }
        }
        String traceId = ctx.traceId();
        String spanId = ctx.spanId();
        if (traceId != null && spanId != null) {
            return TraceSanitizer.sanitizeTraceParent("00-" + traceId + "-" + spanId + "-01");
        }
        return null;
    }

    public <T> T traceSupplier(
            String spanName,
            Span.Kind kind,
            String remoteTraceParent,
            Map<String, String> safeTags,
            Supplier<T> supplier) {
        return traceChecked(spanName, kind, remoteTraceParent, safeTags, supplier::get);
    }

    public void traceRunnable(
            String spanName,
            Span.Kind kind,
            String remoteTraceParent,
            Map<String, String> safeTags,
            Runnable runnable) {
        traceChecked(spanName, kind, remoteTraceParent, safeTags, () -> {
            runnable.run();
            return null;
        });
    }

    public <T, E extends Throwable> T traceChecked(
            String spanName,
            Span.Kind kind,
            String remoteTraceParent,
            Map<String, String> safeTags,
            CheckedSupplier<T, E> supplier) throws E {
        Tracer tracer = tracerProvider != null ? tracerProvider.getIfAvailable() : null;
        if (tracer == null) {
            return supplier.get();
        }

        Span.Builder builder = createSpanBuilder(tracer, remoteTraceParent);
        builder.name(spanName);
        if (kind != null) {
            builder.kind(kind);
        }
        if (safeTags != null) {
            for (Map.Entry<String, String> entry : safeTags.entrySet()) {
                if (entry.getKey() != null && entry.getValue() != null) {
                    String safeVal = TraceSanitizer.redactAttribute(entry.getKey(), entry.getValue());
                    builder.tag(entry.getKey(), safeVal);
                }
            }
        }

        Span span = builder.start();
        try (Tracer.SpanInScope ignored = tracer.withSpan(span)) {
            return supplier.get();
        } catch (Throwable t) {
            span.error(t);
            throw t;
        } finally {
            span.end();
        }
    }

    private Span.Builder createSpanBuilder(Tracer tracer, String remoteTraceParent) {
        String validParent = TraceSanitizer.sanitizeTraceParent(remoteTraceParent);
        Propagator propagator = propagatorProvider != null ? propagatorProvider.getIfAvailable() : null;
        if (validParent != null && propagator != null) {
            Map<String, String> carrier = Map.of("traceparent", validParent);
            return propagator.extract(carrier, Map::get);
        }
        return tracer.spanBuilder();
    }
}
