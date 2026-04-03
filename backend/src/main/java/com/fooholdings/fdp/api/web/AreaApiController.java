package com.fooholdings.fdp.api.web;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fooholdings.fdp.api.dto.AreaResponse;
import com.fooholdings.fdp.api.dto.ChildrenResponse;
import com.fooholdings.fdp.api.dto.HistoryResponse;
import com.fooholdings.fdp.api.service.AreaChildrenQueryService;
import com.fooholdings.fdp.api.service.AreaHistoryQueryService;
import com.fooholdings.fdp.api.service.AreaQueryService;

@RestController
@RequestMapping("/api/area")
public class AreaApiController {

    private final AreaQueryService areaQueryService;
    private final AreaHistoryQueryService historyQueryService;
    private final AreaChildrenQueryService childrenQueryService;

    public AreaApiController(AreaQueryService areaQueryService,
                             AreaHistoryQueryService historyQueryService,
                             AreaChildrenQueryService childrenQueryService) {
        this.areaQueryService = areaQueryService;
        this.historyQueryService = historyQueryService;
        this.childrenQueryService = childrenQueryService;
    }

    @GetMapping("/{geoLevel}/{geoId}")
    public ResponseEntity<AreaResponse> getArea(@PathVariable String geoLevel,
                                                @PathVariable String geoId) {
        return ResponseEntity.ok(areaQueryService.getArea(geoLevel, geoId));
    }

    @GetMapping("/{geoLevel}/{geoId}/history")
    public ResponseEntity<HistoryResponse> getHistory(@PathVariable String geoLevel,
                                                      @PathVariable String geoId,
                                                      @RequestParam String category,
                                                      @RequestParam(defaultValue = "12") int periods) {
        return ResponseEntity.ok(historyQueryService.getHistory(geoLevel, geoId, category, periods));
    }

    @GetMapping("/{geoLevel}/{geoId}/children")
    public ResponseEntity<ChildrenResponse> getChildren(@PathVariable String geoLevel,
                                                        @PathVariable String geoId) {
        return ResponseEntity.ok(childrenQueryService.getChildren(geoLevel, geoId));
    }
}
