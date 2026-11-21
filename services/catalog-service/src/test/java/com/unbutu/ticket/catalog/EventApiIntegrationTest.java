package com.unbutu.ticket.catalog;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = "spring.flyway.locations=classpath:db/migration,classpath:db/seed-local")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class EventApiIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Test
    void listsUpcomingOnSaleEventsSoonestFirst() throws Exception {
        // Seed: concert (sớm nhất có vé đang bán) + sport; phim chỉ có hạng vé nháp → ẩn.
        mvc.perform(get("/api/catalog/events"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("public")))
                .andExpect(jsonPath("$.items", hasSize(2)))
                .andExpect(jsonPath("$.items[0].type").value("CONCERT"))
                .andExpect(jsonPath("$.items[0].priceFrom").value(500000))
                .andExpect(jsonPath("$.hasNext").value(false));
    }

    @Test
    void returns404ForUnknownEvent() throws Exception {
        mvc.perform(get("/api/catalog/events/00000000-0000-0000-0000-000000000000"))
                .andExpect(status().isNotFound());
    }
}
