package com.unbutu.ticket.catalog;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(PingController.class)
class PingControllerTest {

    @Autowired
    MockMvc mvc;

    @Test
    void pingReturnsOk() throws Exception {
        mvc.perform(get("/api/catalog/ping"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.service").value("catalog-service"))
                .andExpect(jsonPath("$.status").value("ok"));
    }
}
