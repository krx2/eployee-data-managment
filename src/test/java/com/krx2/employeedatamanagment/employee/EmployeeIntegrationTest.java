package com.krx2.employeedatamanagment.employee;

import com.krx2.employeedatamanagment.employee.dto.EmployeeCreateRequest;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class EmployeeIntegrationTest {

    @Container
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private EntityManager entityManager;

    @Test
    void createThenGetReturnsEmployeeWithoutPlaintextSsn() throws Exception {
        EmployeeCreateRequest request = new EmployeeCreateRequest(
                "Jan", "Kowalski", LocalDate.of(1990, 1, 1), Gender.MALE, "123-45-6789");

        String createResponseBody = mockMvc.perform(post("/employees")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.maskedSsn").value("***-**-6789"))
                .andReturn().getResponse().getContentAsString();

        UUID id = UUID.fromString(objectMapper.readTree(createResponseBody).get("id").asText());

        String rawColumnValue = (String) entityManager.createNativeQuery(
                        "select ssn_ciphertext from employee where id = ?1")
                .setParameter(1, id)
                .getSingleResult();
        assertThat(rawColumnValue).doesNotContain("123-45-6789");

        mockMvc.perform(get("/employees/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.maskedSsn").value("***-**-6789"));
    }

    @Test
    void getMissingEmployeeReturns404() throws Exception {
        mockMvc.perform(get("/employees/{id}", UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }
}
