package com.fooholdings.fdp.admin.db;

public record DbColumnDto(
        String columnName,
        String dataType,
        boolean nullable,
        String columnDefault
) { }