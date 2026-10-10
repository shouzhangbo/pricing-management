package com.pricing.management.admin;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pricing.management.application.scheme.DeleteResult;
import com.pricing.management.application.scheme.DimensionView;
import com.pricing.management.application.scheme.PricingSchemeApplicationService;
import com.pricing.management.application.scheme.SchemeView;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(SchemeManagementController.class)
class SchemeManagementControllerTest {
    @Autowired private MockMvc mockMvc;
    @MockBean private PricingSchemeApplicationService service;

    @Test
    void shouldReturnSchemeDetail() throws Exception {
        when(service.detail(101L)).thenReturn(new SchemeView(101L, 11L, "杭州配送费", "DRAFT", null, null, null, 0));

        mockMvc.perform(get("/api/schemes/101"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.schemeId").value(101))
                .andExpect(jsonPath("$.data.schemeName").value("杭州配送费"));
        verify(service).detail(101L);
    }

    @Test
    void shouldDeleteSchemeWithRowVersion() throws Exception {
        when(service.delete(101L, 3)).thenReturn(new DeleteResult(101L));

        mockMvc.perform(post("/api/schemes/101/delete").contentType(MediaType.APPLICATION_JSON).content("{\"rowVersion\":3}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.id").value(101));
        verify(service).delete(101L, 3);
    }

    @Test
    void shouldRejectDeleteWithoutRowVersion() throws Exception {
        mockMvc.perform(post("/api/schemes/101/delete").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PARAM_ERROR"));
    }

    @Test
    void shouldReturnAndDeleteDimensionDetail() throws Exception {
        when(service.dimensionDetail(201L)).thenReturn(new DimensionView(201L, 101L, "CITY", "[\"0571\"]", "IN", null, null, 2));
        when(service.deleteDimension(201L, 2)).thenReturn(new DeleteResult(201L));

        mockMvc.perform(get("/api/scheme-dimensions/201"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.dimensionId").value(201));
        mockMvc.perform(post("/api/scheme-dimensions/201/delete").contentType(MediaType.APPLICATION_JSON).content("{\"rowVersion\":2}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.id").value(201));
        verify(service).dimensionDetail(201L);
        verify(service).deleteDimension(201L, 2);
    }
}
