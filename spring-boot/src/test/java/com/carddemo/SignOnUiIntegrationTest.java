package com.carddemo;

import com.carddemo.model.SecurityUser;
import com.carddemo.repository.SecurityUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * COSGN00C web surface (wave 2): the sign-on screen, its AID-key handling and
 * its auth outcomes must behave exactly like COSGN0A. Test names carry the
 * FR-S01 row each covers; cites live in the program FR doc.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "carddemo.seed.data-dir=classpath:seed",
        "spring.datasource.generate-unique-name=true",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class SignOnUiIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private SecurityUserRepository userRepository;

    @BeforeEach
    void addRegularUser() {
        SecurityUser user = new SecurityUser();
        user.setUserId("USER0001");
        user.setFirstName("REGULAR");
        user.setLastName("USER");
        user.setPassword("PASSWORD");
        user.setUserType("U");
        userRepository.save(user);
    }

    @Test
    void signonFormRendersCogn0aFieldsAndKeyHints() throws Exception {
        mockMvc.perform(get("/signon"))
                .andExpect(status().isOk())
                .andExpect(view().name("signon"))
                .andExpect(content().string(containsString("CC00")))
                .andExpect(content().string(containsString("COSGN00C")))
                .andExpect(content().string(containsString(
                        "Type your User ID and Password, then press ENTER:")))
                .andExpect(content().string(containsString("name=\"userid\"")))
                .andExpect(content().string(containsString("type=\"password\"")))
                .andExpect(content().string(containsString("ENTER=Sign-on  F3=Exit")));
    }

    @Test
    void blankUserIdShowsUserIdRequiredMessage_frS0101() throws Exception {
        mockMvc.perform(post("/signon").param("aid", "ENTER")
                        .param("userid", "").param("passwd", "PASSWORD"))
                .andExpect(status().isOk())
                .andExpect(view().name("signon"))
                .andExpect(model().attribute("message", "Please enter User ID ..."));
    }

    @Test
    void blankPasswordShowsPasswordRequiredMessage_frS0102() throws Exception {
        mockMvc.perform(post("/signon").param("aid", "ENTER")
                        .param("userid", "USER0001").param("passwd", ""))
                .andExpect(status().isOk())
                .andExpect(view().name("signon"))
                .andExpect(model().attribute("message", "Please enter Password ..."));
    }

    @Test
    void unknownUserShowsUserNotFoundMessage_frS0103() throws Exception {
        mockMvc.perform(post("/signon").param("aid", "ENTER")
                        .param("userid", "NOSUCH01").param("passwd", "PASSWORD"))
                .andExpect(status().isOk())
                .andExpect(view().name("signon"))
                .andExpect(model().attribute("message", "User not found. Try again ..."))
                .andExpect(content().string(containsString("NOSUCH01")));
    }

    @Test
    void wrongPasswordShowsMessageAndClearsPassword_frS0104() throws Exception {
        mockMvc.perform(post("/signon").param("aid", "ENTER")
                        .param("userid", "USER0001").param("passwd", "WRONGPWD"))
                .andExpect(status().isOk())
                .andExpect(view().name("signon"))
                .andExpect(model().attribute("message", "Wrong Password. Try again ..."))
                .andExpect(content().string(containsString("USER0001")))
                .andExpect(content().string(not(containsString("WRONGPWD"))));
    }

    @Test
    void adminSignonRedirectsToAdminMenuWithAdminSession_frS0105() throws Exception {
        MockHttpSession session = signon("ADMIN001", "PASSWORD", "/admin/menu");
        SecurityContext context = (SecurityContext) session.getAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
        assertThat(context.getAuthentication().getName()).isEqualTo("ADMIN001");
        assertThat(context.getAuthentication().getAuthorities().stream()
                .map(granted -> granted.getAuthority()))
                .containsExactly("ROLE_ADMIN");
    }

    @Test
    void regularSignonRedirectsToMainMenuWithUserSession_frS0106() throws Exception {
        MockHttpSession session = signon("USER0001", "PASSWORD", "/menu");
        SecurityContext context = (SecurityContext) session.getAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
        assertThat(context.getAuthentication().getName()).isEqualTo("USER0001");
        assertThat(context.getAuthentication().getAuthorities().stream()
                .map(granted -> granted.getAuthority()))
                .containsExactly("ROLE_USER");
    }

    @Test
    void pf3ShowsFarewellTextAndEndsSession_frS0108() throws Exception {
        MockHttpSession session = signon("USER0001", "PASSWORD", "/menu");
        mockMvc.perform(post("/signon").session(session).param("aid", "PF3"))
                .andExpect(status().isOk())
                .andExpect(view().name("exit"))
                .andExpect(model().attribute("message",
                        "Thank you for using CardDemo application..."));
        assertThat(session.isInvalid()).isTrue();
    }

    @Test
    void lowerCaseCredentialsAreUpperCasedBeforeAuth_frS0109() throws Exception {
        signon("admin001", "password", "/admin/menu");
    }

    @Test
    void unmappedAidShowsInvalidKeyMessage_frS0120() throws Exception {
        mockMvc.perform(post("/signon").param("aid", "F7")
                        .param("userid", "USER0001").param("passwd", "PASSWORD"))
                .andExpect(status().isOk())
                .andExpect(view().name("signon"))
                .andExpect(model().attribute("message",
                        "Invalid key pressed. Please see below..."));
    }

    @Test
    void unsignedMenuRequestsRedirectToSignon() throws Exception {
        mockMvc.perform(get("/menu"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/signon"));
        mockMvc.perform(get("/admin/menu"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/signon"));
    }

    @Test
    void regularUserIsDeniedAdminMenuPage() throws Exception {
        MockHttpSession session = signon("USER0001", "PASSWORD", "/menu");
        mockMvc.perform(get("/admin/menu").session(session))
                .andExpect(status().isForbidden());
    }

    private MockHttpSession signon(String userId, String password, String landing) throws Exception {
        MvcResult result = mockMvc.perform(post("/signon").param("aid", "ENTER")
                        .param("userid", userId).param("passwd", password))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(landing))
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }
}
