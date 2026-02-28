package com.fooholdings.fdp.sources.adapter;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.fooholdings.fdp.sources.model.SourceType;

/**
 * Resolves by SourceType.
 */
@Component
public class GrocerySourceAdapterRegistry {

    private final Map<SourceType, GrocerySourceAdapter> byType;

    public GrocerySourceAdapterRegistry(List<GrocerySourceAdapter> adapters) {
        EnumMap<SourceType, GrocerySourceAdapter> map = new EnumMap<>(SourceType.class);

        for (GrocerySourceAdapter adapter : adapters) {
            SourceType type = adapter.sourceType();

            GrocerySourceAdapter existing = map.put(type, adapter);
            if (existing != null) {
                throw new IllegalStateException(
                        "Multiple GrocerySourceAdapter beans registered for " + type +
                        ": " + existing.getClass().getName() + " and " + adapter.getClass().getName()
                );
            }
        }

        this.byType = Map.copyOf(map);
    }

    public GrocerySourceAdapter getRequired(SourceType type) {
        GrocerySourceAdapter adapter = byType.get(type);
        if (adapter == null) {
            throw new IllegalStateException(
                    "No GrocerySourceAdapter registered for " + type +
                    ". Did you add the source module and annotate it as a Spring bean?"
            );
        }
        return adapter;
    }
}