package com.fooholdings.fdp.api.dto;

import java.time.LocalDate;

public record ChangeBadgeView(
        String category,
        LocalDate priorPeriod,
        LocalDate currentPeriod,
        double pctChange,
        String direction,
        String magnitude
) {}
