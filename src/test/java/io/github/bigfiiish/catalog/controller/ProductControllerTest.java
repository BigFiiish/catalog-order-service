package io.github.bigfiiish.catalog.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties =
        "spring.datasource.url=jdbc:h2:mem:controllerdb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE"
)
@AutoConfigureMockMvc
class ProductControllerTest {

    private static final String API_KEY_HEADER = "X-API-Key";
    private static final String API_KEY = "test-key";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void rejectsRequestWithoutApiKey() throws Exception {
        mockMvc.perform(get("/api/products"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message")
                        .value("Missing or invalid API key"));
    }

    @Test
    void returnsPaginatedProducts() throws Exception {
        mockMvc.perform(
                        get("/api/products")
                                .header(API_KEY_HEADER, API_KEY)
                                .param("page", "0")
                                .param("size", "2")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].id").value(1))
                .andExpect(jsonPath("$.items[1].id").value(2))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.totalElements").value(5))
                .andExpect(jsonPath("$.totalPages").value(3));
    }

    @Test
    void filtersProductsByCategory() throws Exception {
        mockMvc.perform(
                        get("/api/products")
                                .header(API_KEY_HEADER, API_KEY)
                                .param("category", "Home")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].name")
                        .value("Desk Lamp"))
                .andExpect(jsonPath("$.items[1].name")
                        .value("Standing Desk"));
    }

    @Test
    void returnsProductById() throws Exception {
        mockMvc.perform(
                        get("/api/products/3")
                                .header(API_KEY_HEADER, API_KEY)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(3))
                .andExpect(jsonPath("$.name").value("Desk Lamp"))
                .andExpect(jsonPath("$.stockQuantity").value(0));
    }

    @Test
    void returnsNotFoundForMissingProduct() throws Exception {
        mockMvc.perform(
                        get("/api/products/999")
                                .header(API_KEY_HEADER, API_KEY)
                )
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message")
                        .value("Product 999 was not found"))
                .andExpect(jsonPath("$.path")
                        .value("/api/products/999"));
    }

    @Test
    void rejectsInvalidPagination() throws Exception {
        mockMvc.perform(
                        get("/api/products")
                                .header(API_KEY_HEADER, API_KEY)
                                .param("page", "-1")
                                .param("size", "20")
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message")
                        .value("page must be zero or greater"));
    }
}