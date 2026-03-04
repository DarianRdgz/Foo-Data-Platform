package com.fooholdings.fdp.admin.ingestion;

import java.util.List;
import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/ingestion")
public class AdminIngestionController {

    private final AdminIngestionQueryService query;

    public AdminIngestionController(AdminIngestionQueryService query) {
        this.query = query;
    }

    @GetMapping("/runs")
    public PageDto<IngestionRunRowDto> runs(
            @RequestParam(required = false) String source,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size
    ) {
        return query.listRuns(source, status, page, size);
    }

    @GetMapping("/runs/{runId}")
    public IngestionRunDetailDto run(@PathVariable UUID runId) {
        return query.getRun(runId);
    }

    @GetMapping("/quota")
    public List<QuotaSummaryDto> quota() {
        return query.quotaToday();
    }
}