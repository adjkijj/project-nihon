package com.nihon.controller;

import com.nihon.config.ClientIpResolver;
import com.nihon.config.CsrfInterceptor;
import com.nihon.config.ImportSecurityInterceptor;
import com.nihon.config.WebConfig;
import com.nihon.service.excel.ImportService;
import com.nihon.service.excel.ImportSessionStorage;
import com.nihon.service.excel.RateLimiterService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Kiểm thử tính năng vô hiệu hóa Import: Khi app.import.enabled=false trả về HTTP 404 Not Found.
 */
@WebMvcTest(ImportController.class)
@Import({WebConfig.class, CsrfInterceptor.class, ImportSecurityInterceptor.class, ClientIpResolver.class, ImportSessionStorage.class, RateLimiterService.class})
@TestPropertySource(properties = {"app.import.enabled=false"})
class ImportDisabledTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ImportService importService;

    @MockitoBean
    private RateLimiterService rateLimiterService;

    @MockitoBean
    private ImportSessionStorage sessionStorage;

    @Test
    @DisplayName("app.import.enabled=false trả về HTTP 404 Not Found khi truy cập GET /import")
    void importDisabled_getImport_returns404NotFound() throws Exception {
        mockMvc.perform(get("/import"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("app.import.enabled=false trả về HTTP 404 Not Found khi truy cập GET /import/template")
    void importDisabled_getTemplate_returns404NotFound() throws Exception {
        mockMvc.perform(get("/import/template"))
                .andExpect(status().isNotFound());
    }
}
