package com.fooholdings.fdp.core.logging;

import org.slf4j.MDC;
import org.slf4j.Marker;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.turbo.TurboFilter;
import ch.qos.logback.core.spi.FilterReply;

public class ErrorCategoryTurboFilter extends TurboFilter {
    @Override
    public FilterReply decide(Marker marker, Logger logger, Level level,
                             String format, Object[] params, Throwable t) {
        if (level != null && level.isGreaterOrEqual(Level.WARN)) {
            String existing = MDC.get(ErrorCategoryMdc.KEY);
            if (existing == null || existing.isBlank()) {
                MDC.put(ErrorCategoryMdc.KEY, ErrorCategory.UNCLASSIFIED.name());
            }
        }
        return FilterReply.NEUTRAL;
    }
}