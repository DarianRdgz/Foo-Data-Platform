package com.fooholdings.fdp.admin.db;

import java.util.List;

public record DbIndexDto(
        String indexName,
        boolean unique,
        List<String> columns
) { }