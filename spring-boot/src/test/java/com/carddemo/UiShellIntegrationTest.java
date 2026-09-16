package com.carddemo;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The Thymeleaf shell is the wave-1 UI seam: the base layout (header with app
 * title and current date/time, message line, body block) must render.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "carddemo.seed.data-dir=classpath:seed",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class UiShellIntegrationTest {

    @Autowired private MockMvc mockMvc;

    @Test
    void shellRendersHeaderDateTimeMessageLineAndBody() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("CardDemo")))
                .andExpect(content().string(containsString("screen-header")))
                .andExpect(content().string(containsString("message-line")))
                .andExpect(content().string(containsString("/css/carddemo.css")))
                .andExpect(content().string(containsString("later waves")));
    }
}
