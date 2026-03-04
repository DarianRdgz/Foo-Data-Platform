package com.fooholdings.fdp.admin.jobs;

import java.util.function.Supplier;

public record JobDefinition(
        String name,
        String source,
        Supplier<String> cronExpressionSupplier,
        Supplier<Boolean> enabledSupplier
) { }