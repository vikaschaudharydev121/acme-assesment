package com.acme.salary.insights;

import com.acme.salary.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class InsightsApiIntegrationTest extends IntegrationTest {

    @BeforeEach
    void givenEmployees() throws Exception {
        create(employee("Liam", "Smith", "liam@acme.example", "US", 100_000));
        create(employee("Emma", "Jones", "emma@acme.example", "US", 140_000));
        create(employee("Asha", "Rao", "asha@acme.example", "IN", 2_000_000));
    }

    @Test
    void summaryAggregatesInUsd() throws Exception {
        mvc.perform(get("/api/v1/insights/summary").with(viewer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.headcount").value(3))
                .andExpect(jsonPath("$.countries").value(2))
                // 2,000,000 INR * 0.012 = 24,000 USD
                .andExpect(jsonPath("$.totalPayrollUsd").value(264_000))
                .andExpect(jsonPath("$.salaryUsd.median").value(100_000));
    }

    @Test
    void byCountryShowsLocalCurrency() throws Exception {
        mvc.perform(get("/api/v1/insights/by-country").with(viewer()))
                .andExpect(jsonPath("$[0].countryCode").value("US"))
                .andExpect(jsonPath("$[0].salaryLocal.median").value(120_000))
                .andExpect(jsonPath("$[1].currency").value("INR"))
                .andExpect(jsonPath("$[1].medianUsd").value(24_000));
    }

    @Test
    void answersWhatWePayAJobTitleInACountry() throws Exception {
        mvc.perform(get("/api/v1/insights/by-job-title").param("country", "US").with(viewer()))
                .andExpect(jsonPath("$.currency").value("USD"))
                .andExpect(jsonPath("$.groups[0].name").value("Software Engineer"))
                .andExpect(jsonPath("$.groups[0].salary.count").value(2))
                .andExpect(jsonPath("$.groups[0].salary.median").value(120_000));
    }

    @Test
    void invalidCountryFilterIsRejected() throws Exception {
        mvc.perform(get("/api/v1/insights/by-department").param("country", "usa").with(viewer()))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/insights/by-department").param("country", "ZZ").with(viewer()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("UNKNOWN_COUNTRY"));
    }

    @Test
    void insightsRequireAuthentication() throws Exception {
        mvc.perform(get("/api/v1/insights/summary")).andExpect(status().isUnauthorized());
    }

    private void create(java.util.Map<String, Object> body) throws Exception {
        mvc.perform(post("/api/v1/employees").with(manager()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(toJson(body)))
                .andExpect(status().isCreated());
    }
}
