package vn.sunvibe.shared.exception;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

import org.springframework.core.MethodParameter;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

import tools.jackson.core.JacksonException;
import vn.sunvibe.shared.dto.response.error.ValidationError;

/**
 * Helpers the exception handlers of the modules share (clean-up spec S-8; ADR-004 allows errors here). What differs
 * between modules stays in each handler: survey's dotted field indexes, the predicate of what may quote request data,
 * and the log text.
 */
public final class ApiErrorSupport {

    /** How deep {@link #findCause} reads a cause chain, the exception itself included. */
    private static final int MAX_CAUSE_DEPTH = 20;
    /** How many exceptions {@link #anyInChain} reads before it assumes the worst. */
    private static final int MAX_EXCEPTIONS_CHECKED = 50;

    private ApiErrorSupport() {
    }

    /** The JSON path of the value Jackson refused, property names (or array indexes) joined by dots. */
    public static String jsonPath(JacksonException e) {
        List<String> parts = new ArrayList<>();
        for (JacksonException.Reference reference : e.getPath()) {
            parts.add(reference.getPropertyName() != null
                    ? reference.getPropertyName() : String.valueOf(reference.getIndex()));
        }
        return String.join(".", parts);
    }

    /** The first exception of {@code type} in the cause chain of {@code e}, within its first 20 links; else null. */
    public static <T extends Throwable> T findCause(Throwable e, Class<T> type) {
        Throwable current = e;
        for (int depth = 0; current != null && depth < MAX_CAUSE_DEPTH; depth++) {
            if (type.isInstance(current)) {
                return type.cast(current);
            }
            current = current.getCause();
        }
        return null;
    }

    /**
     * True when {@code e}, one of its causes or one of the exceptions suppressed along the way (a failed rollback or
     * close) passes {@code test}. A stack trace prints all of them, so all of them are checked, each once. Past 50
     * exceptions it answers true: a chain too long to check is assumed to match, so a caller that keeps request data
     * out of the log keeps it out.
     */
    public static boolean anyInChain(Throwable e, Predicate<Throwable> test) {
        Deque<Throwable> pending = new ArrayDeque<>();
        Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        pending.add(e);
        while (!pending.isEmpty() && seen.size() < MAX_EXCEPTIONS_CHECKED) {
            Throwable current = pending.poll();
            if (!seen.add(current)) {
                continue;
            }
            if (test.test(current)) {
                return true;
            }
            if (current.getCause() != null) {
                pending.add(current.getCause());
            }
            pending.addAll(List.of(current.getSuppressed()));
        }
        // Too many to check: assume the worst
        return !pending.isEmpty();
    }

    /**
     * The name the client used: the header, query or path name when the parameter has one ({@code name} or
     * {@code value}, since a parameter built outside Spring MVC does not merge the two); else the parameter's own name,
     * which the modules, compiled without {@code -parameters}, do not have, so {@code request}.
     */
    public static String parameterName(MethodParameter parameter) {
        RequestHeader header = parameter.getParameterAnnotation(RequestHeader.class);
        if (header != null) {
            return firstNonEmpty(header.name(), header.value(), parameter.getParameterName());
        }
        RequestParam param = parameter.getParameterAnnotation(RequestParam.class);
        if (param != null) {
            return firstNonEmpty(param.name(), param.value(), parameter.getParameterName());
        }
        PathVariable path = parameter.getParameterAnnotation(PathVariable.class);
        if (path != null) {
            return firstNonEmpty(path.name(), path.value(), parameter.getParameterName());
        }
        return firstNonEmpty(parameter.getParameterName(), "request", null);
    }

    /** One entry per field (the first message), sorted by field, so the body does not depend on check order. */
    public static List<ValidationError> sortedByField(List<ValidationError> errors) {
        Map<String, ValidationError> byField = new LinkedHashMap<>();
        errors.forEach(error -> byField.putIfAbsent(error.field(), error));
        return byField.values().stream()
                .sorted(Comparator.comparing(ValidationError::field))
                .toList();
    }

    private static String firstNonEmpty(String first, String second, String third) {
        if (first != null && !first.isEmpty()) {
            return first;
        }
        if (second != null && !second.isEmpty()) {
            return second;
        }
        return third;
    }
}
