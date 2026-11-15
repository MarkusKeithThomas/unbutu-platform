package com.unbutu.ticket.catalog;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class EventApiIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Test
    void listsSeededEventsNewestFirst() throws Exception {
        mvc.perform(get("/api/catalog/events"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0].type").value("SPORT"));
    }

    @Test
    void returns404ForUnknownEvent() throws Exception {
        mvc.perform(get("/api/catalog/events/00000000-0000-0000-0000-000000000000"))
                .andExpect(status().isNotFound());
    }
}
