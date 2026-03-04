package com.fooholdings.fdp.admin.db;

import java.util.List;
import java.util.Map;

public record DbSampleDto(
        String schemaName,
        String tableName,
        List<String> primaryKeyColumns,
        List<Map<String, Object>> rows
) { }