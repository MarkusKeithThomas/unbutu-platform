package com.unbutu.ticket.catalog;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(DemoController.class)
class DemoControllerTest {

    @Autowired
    MockMvc mvc;

    @Test
    void bichPhuongReturnsGreetingInUtf8() throws Exception {
        mvc.perform(get("/api/catalog-service/bich-phuong"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(new MediaType("text", "plain", StandardCharsets.UTF_8)))
                .andExpect(content().string("Xin chào người đẹp Bích Phượng"));
    }
}
