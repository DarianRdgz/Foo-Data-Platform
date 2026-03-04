package com.fooholdings.fdp.sources.kroger.web;

import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fooholdings.fdp.sources.kroger.ingestion.KrogerLocationIngestionService;
import com.fooholdings.fdp.sources.kroger.ingestion.KrogerProductIngestionService;

/**
 * Web layer slice tests for KrogerIngestionController.
 *
 * Uses @WebMvcTest to load only the web layer
 * Service layer is mocked with @MockitoBean (Spring Boot 4 replacement for @MockBean)
 */
@WebMvcTest(KrogerIngestionController.class)
class KrogerIngestionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private KrogerLocationIngestionService locationIngestionService;

    @MockitoBean
    private KrogerProductIngestionService productIngestionService;

    // Location ingestion

    @Test
    void triggerLocations_returns200OnSuccess() throws Exception {
        when(locationIngestionService.ingest(anyList()))
                .thenReturn("Processed 3 locations across 1 zip(s). Upserted: 3");

        mockMvc.perform(post("/kroger/ingestion/locations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"zipCodes\":[\"77001\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.summary").exists());
    }

    @Test
    void triggerLocations_returns409WhenLocked() throws Exception {
        when(locationIngestionService.ingest(anyList()))
                .thenThrow(new com.fooholdings.fdp.core.ingestion.IngestionLockException("already running"));

        mockMvc.perform(post("/kroger/ingestion/locations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"zipCodes\":[\"77001\"]}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value("LOCKED"));
    }

    @Test
    void triggerLocations_returns400WhenZipCodesEmpty() throws Exception {
        mockMvc.perform(post("/kroger/ingestion/locations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"zipCodes\":[]}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(locationIngestionService);
    }

    @Test
    void triggerLocations_returns400WhenBodyMissing() throws Exception {
        mockMvc.perform(post("/kroger/ingestion/locations")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }

    // Product ingestion

    @Test
    void triggerProducts_returns200OnSuccess() throws Exception {
        when(productIngestionService.ingest(anyList(), anyList()))
                .thenReturn("Products: 10 processed, 10/10 price observations inserted");

        mockMvc.perform(post("/kroger/ingestion/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"locationIds\":[\"70100277\"],\"searchTerms\":[\"milk\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"));
    }

    @Test
    void triggerProducts_returns409WhenLocked() throws Exception {
        when(productIngestionService.ingest(anyList(), anyList()))
                .thenThrow(new com.fooholdings.fdp.core.ingestion.IngestionLockException("already running"));

        mockMvc.perform(post("/kroger/ingestion/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"locationIds\":[\"70100277\"],\"searchTerms\":[\"milk\"]}"))
                .andExpect(status().isConflict());
    }

    @Test
    void triggerProducts_returns400WhenSearchTermsEmpty() throws Exception {
        mockMvc.perform(post("/kroger/ingestion/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"locationIds\":[\"70100277\"],\"searchTerms\":[]}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(productIngestionService);
    }
}
