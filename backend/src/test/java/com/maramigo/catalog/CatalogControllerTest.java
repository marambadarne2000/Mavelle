package com.maramigo.catalog;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.context.annotation.Import;
import com.maramigo.accounts.SecurityConfiguration;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import java.util.List;
import java.util.Optional;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** @author Maram Abbas */
@WebMvcTest(CatalogController.class)
@Import(SecurityConfiguration.class)
class CatalogControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean ProductRepository products;
    @MockitoBean org.springframework.security.core.userdetails.UserDetailsService userDetails;

    @Test void unknownOrInactiveProductIsNotFound() throws Exception {
        when(products.findByIdAndActiveTrue(42L)).thenReturn(Optional.empty());
        mvc.perform(get("/api/products/42")).andExpect(status().isNotFound());
    }

    @Test void searchIsTrimmedAndEmptyResultHasPagination() throws Exception {
        when(products.findByActiveTrueAndNameContainingIgnoreCase(eq("silk"), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of()));
        mvc.perform(get("/api/products").param("search", " silk "))
            .andExpect(status().isOk()).andExpect(jsonPath("$.items").isEmpty())
            .andExpect(jsonPath("$.total").value(0));
        verify(products).findByActiveTrueAndNameContainingIgnoreCase(eq("silk"), any(Pageable.class));
    }

    @Test void paginationLimitsAreValidatedBeforeQueryingDatabase() throws Exception {
        mvc.perform(get("/api/products").param("size", "500")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/products").param("page", "-1")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/products").param("category", "unknown")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/products").param("sort", "unknown")).andExpect(status().isBadRequest());
        verifyNoInteractions(products);
    }

    @Test void publicCatalogDoesNotAcceptWrites() throws Exception {
        mvc.perform(post("/api/products").with(user("customer")).with(csrf()).contentType("application/json").content("{}"))
            .andExpect(status().isMethodNotAllowed());
        verifyNoInteractions(products);
    }
}
