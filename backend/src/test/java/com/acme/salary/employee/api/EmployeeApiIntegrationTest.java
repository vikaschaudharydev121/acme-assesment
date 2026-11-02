package com.acme.salary.employee.api;

import com.acme.salary.support.IntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.HashMap;
import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class EmployeeApiIntegrationTest extends IntegrationTest {

    @Test
    void managerCreatesReadsUpdatesAndDeletesAnEmployee() throws Exception {
        JsonNode created = create(employee("Asha", "Rao", "asha@acme.example", "IN", 2_400_000));
        long id = created.get("id").asLong();
        assertCode(created.get("employeeCode").asText());

        mvc.perform(get("/api/v1/employees/{id}", id).with(viewer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currency").value("INR"))
                .andExpect(jsonPath("$.createdBy").doesNotExist())
                .andExpect(jsonPath("$.updatedBy").value("manager"));

        Map<String, Object> update = new HashMap<>(employee("Asha", "Rao", "asha@acme.example", "IN", 2_700_000));
        update.put("version", created.get("version").asLong());
        update.put("salaryChangeReason", "Annual review 2026");
        mvc.perform(put("/api/v1/employees/{id}", id).with(manager()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(toJson(update)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.annualSalary").value(2_700_000))
                .andExpect(jsonPath("$.version").value(created.get("version").asLong() + 1));

        mvc.perform(get("/api/v1/employees/{id}/salary-history", id).with(viewer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].reason").value("Annual review 2026"))
                .andExpect(jsonPath("$[0].previousSalary").value(2_400_000))
                .andExpect(jsonPath("$[0].changedBy").value("manager"))
                .andExpect(jsonPath("$[1].reason").value("Initial salary"));

        mvc.perform(delete("/api/v1/employees/{id}", id).with(manager()).with(csrf()))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/employees/{id}", id).with(viewer()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        // History is removed with the employee (FK cascade).
        org.assertj.core.api.Assertions.assertThat(
                jdbc.queryForObject("SELECT COUNT(*) FROM salary_history WHERE employee_id = ?", Long.class, id))
                .isZero();
    }

    @Test
    void createReturnsLocationHeader() throws Exception {
        mvc.perform(post("/api/v1/employees").with(manager()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(employee("Liam", "Smith", "liam@acme.example", "US", 120_000))))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", containsString("/api/v1/employees/")));
    }

    @Test
    void anonymousRequestsAreRejected() throws Exception {
        mvc.perform(get("/api/v1/employees"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void viewerCannotModifyData() throws Exception {
        mvc.perform(post("/api/v1/employees").with(viewer()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(employee("Liam", "Smith", "liam@acme.example", "US", 120_000))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mvc.perform(delete("/api/v1/employees/1").with(viewer()).with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    void invalidInputReturnsFieldErrors() throws Exception {
        Map<String, Object> bad = new HashMap<>(employee("<script>", "", "not-an-email", "usa", -5));
        bad.put("hireDate", "2999-01-01");

        mvc.perform(post("/api/v1/employees").with(manager()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(toJson(bad)))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("Content-Type", startsWith("application/problem+json")))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[*].field", hasItem("firstName")))
                .andExpect(jsonPath("$.errors[*].field", hasItem("lastName")))
                .andExpect(jsonPath("$.errors[*].field", hasItem("email")))
                .andExpect(jsonPath("$.errors[*].field", hasItem("countryCode")))
                .andExpect(jsonPath("$.errors[*].field", hasItem("annualSalary")))
                .andExpect(jsonPath("$.errors[*].field", hasItem("hireDate")));
    }

    @Test
    void serverOwnedFieldsCannotBeMassAssigned() throws Exception {
        Map<String, Object> body = new HashMap<>(employee("Liam", "Smith", "liam@acme.example", "US", 120_000));
        body.put("employeeCode", "EMP-999999");

        mvc.perform(post("/api/v1/employees").with(manager()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(toJson(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void duplicateEmailIsAConflictCaseInsensitively() throws Exception {
        create(employee("Liam", "Smith", "liam@acme.example", "US", 120_000));

        mvc.perform(post("/api/v1/employees").with(manager()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(employee("Other", "Person", "LIAM@acme.example", "US", 90_000))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_EXISTS"));
    }

    @Test
    void concurrentEditWithStaleVersionIsRejected() throws Exception {
        JsonNode created = create(employee("Liam", "Smith", "liam@acme.example", "US", 120_000));
        Map<String, Object> first = new HashMap<>(employee("Liam", "Smith", "liam@acme.example", "US", 120_000));
        first.put("version", created.get("version").asLong());
        first.put("jobTitle", "Senior Software Engineer");
        String body = toJson(first);
        long id = created.get("id").asLong();

        mvc.perform(put("/api/v1/employees/{id}", id).with(manager()).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk());
        mvc.perform(put("/api/v1/employees/{id}", id).with(manager()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STALE_VERSION"));
    }

    @Test
    void searchFiltersSortsAndPages() throws Exception {
        create(employee("Asha", "Rao", "asha@acme.example", "IN", 2_400_000));
        create(employee("Liam", "Smith", "liam@acme.example", "US", 120_000));
        create(employee("Emma", "Smithers", "emma@acme.example", "US", 150_000));

        mvc.perform(get("/api/v1/employees").param("q", "smith").param("sort", "annualSalary,desc").with(viewer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].firstName").value("Emma"))
                .andExpect(jsonPath("$.content[1].firstName").value("Liam"));

        mvc.perform(get("/api/v1/employees").param("country", "IN").with(viewer()))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].email").value("asha@acme.example"));

        mvc.perform(get("/api/v1/employees").param("q", "asha rao").with(viewer()))
                .andExpect(jsonPath("$.totalElements").value(1));

        mvc.perform(get("/api/v1/employees").param("size", "2").param("page", "1").with(viewer()))
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.totalPages").value(2));
    }

    @Test
    void likeWildcardsInSearchAreTreatedLiterally() throws Exception {
        create(employee("Liam", "Smith", "liam@acme.example", "US", 120_000));

        mvc.perform(get("/api/v1/employees").param("q", "%").with(viewer()))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void unsupportedSortAndOversizedPagesAreHandledSafely() throws Exception {
        mvc.perform(get("/api/v1/employees").param("sort", "passwordHash").with(viewer()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_SORT"));

        mvc.perform(get("/api/v1/employees").param("size", "100000").with(viewer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(100));
    }

    @Test
    void referenceDataIsAvailableForForms() throws Exception {
        create(employee("Liam", "Smith", "liam@acme.example", "US", 120_000));

        mvc.perform(get("/api/v1/reference/countries").with(viewer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(8))
                .andExpect(jsonPath("$[*].code", hasItem("IN")));
        mvc.perform(get("/api/v1/reference/departments").with(viewer()))
                .andExpect(jsonPath("$[0]").value("Engineering"));
    }

    private JsonNode create(Map<String, Object> body) throws Exception {
        String response = mvc.perform(post("/api/v1/employees").with(manager()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(toJson(body)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(response);
    }

    private static void assertCode(String code) {
        org.assertj.core.api.Assertions.assertThat(code).matches("^EMP-\\d{6}$");
    }
}
