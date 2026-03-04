package com.fooholdings.fdp.admin.ingestion;

import java.time.LocalDate;

public record QuotaSummaryDto(
        String source,
        LocalDate usageDate,
        int usedToday,
        int dailyLimit,
        int remaining
) { }