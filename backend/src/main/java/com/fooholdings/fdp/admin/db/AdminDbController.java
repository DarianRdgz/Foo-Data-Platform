package com.fooholdings.fdp.admin.db;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/db")
public class AdminDbController {

    private final AdminDbQueryService db;

    public AdminDbController(AdminDbQueryService db) {
        this.db = db;
    }

    @GetMapping("/tables")
    public List<DbTableDto> tables() {
        return db.listTables();
    }

    @GetMapping("/tables/{schema}/{table}")
    public DbTableDto refreshOne(@PathVariable String schema, @PathVariable String table) {
        return db.refreshOne(schema, table);
    }

    @GetMapping("/tables/{schema}/{table}/columns")
    public List<DbColumnDto> columns(@PathVariable String schema, @PathVariable String table) {
        return db.listColumns(schema, table);
    }

    @GetMapping("/tables/{schema}/{table}/indexes")
    public List<DbIndexDto> indexes(@PathVariable String schema, @PathVariable String table) {
        return db.listIndexes(schema, table);
    }

    @GetMapping("/tables/{schema}/{table}/sample")
    public DbSampleDto sample(
            @PathVariable String schema,
            @PathVariable String table,
            @RequestParam(defaultValue = "20") int limit
    ) {
        return db.sampleRows(schema, table, limit);
    }
}