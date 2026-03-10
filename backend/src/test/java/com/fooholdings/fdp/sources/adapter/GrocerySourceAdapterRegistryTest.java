package com.fooholdings.fdp.sources.adapter;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fooholdings.fdp.sources.model.SourceType;

class GrocerySourceAdapterRegistryTest {

    @Test
    void resolvesKrogerAdapterBySourceType() {
        GrocerySourceAdapter krogerAdapter = mock(GrocerySourceAdapter.class);
        when(krogerAdapter.sourceType()).thenReturn(SourceType.KROGER);

        GrocerySourceAdapterRegistry registry =
                new GrocerySourceAdapterRegistry(List.of(krogerAdapter));

        GrocerySourceAdapter adapter = registry.getRequired(SourceType.KROGER);

        assertThat(adapter).isNotNull();
        assertThat(adapter.sourceType()).isEqualTo(SourceType.KROGER);
    }
}