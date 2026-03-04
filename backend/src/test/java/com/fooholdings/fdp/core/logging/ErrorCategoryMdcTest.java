package com.fooholdings.fdp.core.logging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

class ErrorCategoryMdcTest {

    @Test
    void restoresPreviousCategory() {
        MDC.put(ErrorCategoryMdc.KEY, "PRIOR");

        try (@SuppressWarnings("unused") var ignored = ErrorCategoryMdc.with(ErrorCategory.DB_ERROR)) {
            assertEquals("DB_ERROR", MDC.get(ErrorCategoryMdc.KEY));
        }

        assertEquals("PRIOR", MDC.get(ErrorCategoryMdc.KEY));
        MDC.clear();
    }

    @Test
    void clearsWhenNoPrior() {
        MDC.remove(ErrorCategoryMdc.KEY);

        try (@SuppressWarnings("unused") var ignored = ErrorCategoryMdc.with(ErrorCategory.API_ERROR)) {
            assertEquals("API_ERROR", MDC.get(ErrorCategoryMdc.KEY));
        }

        assertNull(MDC.get(ErrorCategoryMdc.KEY));
        MDC.clear();
    }
}