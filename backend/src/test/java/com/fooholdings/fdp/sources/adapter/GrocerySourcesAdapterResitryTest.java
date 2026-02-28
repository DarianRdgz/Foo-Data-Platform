package com.fooholdings.fdp.sources.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import com.fooholdings.fdp.sources.kroger.client.KrogerApiClient; // only in test; adapter depends on this
import com.fooholdings.fdp.sources.model.SourceType;

@SpringBootTest
class GrocerySourceAdapterRegistryTest {

    // We mock the API client so the context can start without real credentials/calls.
    @MockBean
    private KrogerApiClient krogerApiClient;

    @Test
    void resolvesKrogerAdapterBySourceType() {
        // Arrange

        // Act
        GrocerySourceAdapter adapter =
                SpringContext.getBean(GrocerySourceAdapterRegistry.class).getRequired(SourceType.KROGER);

        // Assert
        assertThat(adapter).isNotNull();
        assertThat(adapter.sourceType()).isEqualTo(SourceType.KROGER);
    }

    @org.springframework.stereotype.Component
    static class SpringContext implements org.springframework.context.ApplicationContextAware {
        private static org.springframework.context.ApplicationContext ctx;

        @Override
        public void setApplicationContext(org.springframework.context.ApplicationContext applicationContext) {
            ctx = applicationContext;
        }

        static <T> T getBean(Class<T> type) {
            return ctx.getBean(type);
        }
    }
}