package com.fooholdings.fdp.api.dto;

import java.time.LocalDate;

public record HistoryPoint(
        LocalDate snapshotPeriod,
        Double value,
        String source,
        boolean isRollup
) {}
