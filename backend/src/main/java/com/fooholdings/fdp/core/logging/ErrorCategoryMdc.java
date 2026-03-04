package com.fooholdings.fdp.core.logging;

import org.slf4j.MDC;

public final class ErrorCategoryMdc implements AutoCloseable {
    public static final String KEY = "error_category";
    private final String prior = MDC.get(KEY);

    private ErrorCategoryMdc(ErrorCategory category) {
        MDC.put(KEY, (category == null ? ErrorCategory.UNCLASSIFIED : category).name());
    }

    public static ErrorCategoryMdc with(ErrorCategory category) {
        return new ErrorCategoryMdc(category);
    }

    @Override
    public void close() {
        if (prior == null) MDC.remove(KEY);
        else MDC.put(KEY, prior);
    }
}