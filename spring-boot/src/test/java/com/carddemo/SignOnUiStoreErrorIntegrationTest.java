package com.carddemo;

import com.carddemo.repository.SecurityUserRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * FR-S01-07: a security-store failure on the keyed read maps to
 * "Unable to verify the User ..." (COSGN00C.cbl:252-256), matching the
 * RESP "other" branch of READ-USER-SEC-FILE. Kept in its own context so the
 * repository double cannot leak into the real-data tests.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "carddemo.seed.data-dir=classpath:seed",
        "spring.datasource.generate-unique-name=true",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class SignOnUiStoreErrorIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private SecurityUserRepository userRepository;

    @Test
    void storeErrorShowsUnableToVerifyUserMessage_frS0107() throws Exception {
        Mockito.when(userRepository.findById(ArgumentMatchers.anyString()))
                .thenThrow(new DataAccessResourceFailureException("store unavailable"));

        mockMvc.perform(post("/signon").param("aid", "ENTER")
                        .param("userid", "ADMIN001").param("passwd", "PASSWORD"))
                .andExpect(status().isOk())
                .andExpect(view().name("signon"))
                .andExpect(model().attribute("message", "Unable to verify the User ..."));
    }
}
