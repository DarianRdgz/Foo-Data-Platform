package com.fooholdings.fdp.geo.event;
 
import org.springframework.context.ApplicationEvent;
 
/**
 * Published by any adapter after a successful batch write to area_snapshot.
 *
 * GeoChangeDetectionService and GeoRollupService both listen to this event.
 * The {@code category} field is optional; adapters that ingest a single
 * category should populate it to narrow the re-computation scope.
 * A null category means "all categories were touched — run a full sweep."
 */
public class AreaIngestCompletedEvent extends ApplicationEvent {
 
    private final String sourceCode;
    private final String category;
    private final int rowsWritten;
 
    public AreaIngestCompletedEvent(Object source, String sourceCode, String category, int rowsWritten) {
        super(source);
        this.sourceCode = sourceCode;
        this.category   = category;
        this.rowsWritten = rowsWritten;
    }
 
    public String getSourceCode() { return sourceCode; }
 
    /**
     * The category ingested, or null if the event covers multiple categories.
     * Listeners should treat null as "recompute all."
     */
    public String getCategory() { return category; }
 
    public int getRowsWritten() { return rowsWritten; }
 
    @Override
    public String toString() {
        return "AreaIngestCompletedEvent[source=%s, category=%s, rows=%d]"
                .formatted(sourceCode, category, rowsWritten);
    }
}