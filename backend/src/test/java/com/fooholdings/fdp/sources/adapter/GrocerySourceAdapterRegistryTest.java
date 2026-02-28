package com.fooholdings.fdp.sources.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.fooholdings.fdp.sources.kroger.client.KrogerApiClient;
import com.fooholdings.fdp.sources.model.SourceType;

@SpringBootTest
class GrocerySourceAdapterRegistryTest {

    @Autowired
    private GrocerySourceAdapterRegistry registry;

    @MockitoBean
    @SuppressWarnings("unused")
    private KrogerApiClient krogerApiClient;

    @Test
    void resolvesKrogerAdapterBySourceType() {
        GrocerySourceAdapter adapter = registry.getRequired(SourceType.KROGER);

        assertThat(adapter).isNotNull();
        assertThat(adapter.sourceType()).isEqualTo(SourceType.KROGER);
    }
}