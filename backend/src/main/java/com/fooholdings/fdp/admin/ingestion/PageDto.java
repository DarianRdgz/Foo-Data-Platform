package com.fooholdings.fdp.admin.ingestion;

import java.util.List;

public record PageDto<T>(List<T> items, int page, int size, long total) { }