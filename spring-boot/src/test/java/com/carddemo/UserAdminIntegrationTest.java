package com.carddemo;

import com.carddemo.api.CobolMessages;
import com.carddemo.model.SecurityUser;
import com.carddemo.repository.SecurityUserRepository;
import com.carddemo.service.AdminUserService;
import com.carddemo.service.AdminUserService.AddForm;
import com.carddemo.service.AdminUserService.AddScreen;
import com.carddemo.service.AdminUserService.DeleteForm;
import com.carddemo.service.AdminUserService.DeleteScreen;
import com.carddemo.service.AdminUserService.Form;
import com.carddemo.service.AdminUserService.Page;
import com.carddemo.service.AdminUserService.Row;
import com.carddemo.service.AdminUserService.State;
import com.carddemo.service.AdminUserService.UpdateForm;
import com.carddemo.service.AdminUserService.UpdateScreen;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Store-level parity checks for COUSR00C–COUSR03C: the keyed browse both
 * directions, write/rewrite/delete persistence and the verbatim message
 * paths against a real users table (JPA + H2, same repository contract as
 * Postgres).
 */
@SpringBootTest
@TestPropertySource(properties = {
        "carddemo.seed.data-dir=classpath:seed",
        "spring.datasource.generate-unique-name=true",
        "spring.datasource.url=jdbc:h2:mem:useradmintest;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class UserAdminIntegrationTest {

    @Autowired private AdminUserService service;
    @Autowired private SecurityUserRepository userRepository;

    @BeforeEach
    void seedUsers() {
        userRepository.deleteAllInBatch();
        IntStream.rangeClosed(1, 12).mapToObj(seq -> {
            SecurityUser user = new SecurityUser();
            user.setUserId("U%07d".formatted(seq));
            user.setFirstName("FIRST%03d".formatted(seq));
            user.setLastName("LAST%03d".formatted(seq));
            user.setPassword("PASS%04d".formatted(seq));
            user.setUserType(seq % 2 == 0 ? "U" : "A");
            return user;
        }).forEach(userRepository::save);
    }

    private static Form carry(Page page, String searchId) {
        return new Form(searchId,
                List.of("", "", "", "", "", "", "", "", "", ""),
                page.rows(), page.pageDisplay(), page.firstId(), page.lastId(),
                Long.toString(page.pageNum()), page.nextPage() ? "Y" : "");
    }

    @Test
    void browseCyclesBothWays_frS1201_frS1207_frS1209_frS1210_frS1212() {
        Page page1 = service.firstDisplay();
        assertThat(page1.pageNum()).isEqualTo(1);
        assertThat(page1.firstId()).isEqualTo("U0000001");
        assertThat(page1.lastId()).isEqualTo("U0000010");
        assertThat(page1.nextPage()).isTrue();

        Page page2 = service.pf8(carry(page1, ""));
        assertThat(page2.pageNum()).isEqualTo(2);
        assertThat(page2.rows().stream().filter(r -> !r.isBlank()).toList())
                .hasSize(2);
        assertThat(page2.nextPage()).isFalse();
        assertThat(page2.message()).isEqualTo(CobolMessages.USER_REACHED_BOTTOM);

        Page back = service.pf7(carry(page2, ""));
        assertThat(back.pageNum()).isEqualTo(1);
        assertThat(back.firstId()).isEqualTo("U0000001");
        assertThat(back.nextPage()).isTrue();
        assertThat(back.message()).isEqualTo(CobolMessages.USER_REACHED_TOP);
    }

    @Test
    void enterRepositionsOnSearchKey_frS1202() {
        Page page = service.enter(carry(Page.unchanged(Form.blank(),
                State.fresh(), null), "U0000005")).page();
        assertThat(page.firstId()).isEqualTo("U0000005");
        assertThat(page.pageNum()).isEqualTo(1);
    }

    @Test
    void enterBeyondEveryKeyShowsTop_frS1213() {
        Page page = service.enter(carry(Page.unchanged(Form.blank(),
                State.fresh(), null), "ZZZ")).page();
        assertThat(page.rows()).allMatch(Row::isBlank);
        assertThat(page.message()).isEqualTo(CobolMessages.USER_AT_TOP);
        assertThat(page.pageNum()).isEqualTo(0);
    }

    @Test
    void addPersistsAndDuplicateKeepsFields_frS1218_frS1219() {
        AddScreen added = service.addEnter(
                new AddForm("NEWUSER1", "NEW", "USER", "NEWPASS", "U"));
        assertThat(added.message()).isEqualTo("User NEWUSER1 has been added ...");
        assertThat(userRepository.findById("NEWUSER1")).isPresent()
                .get().extracting(SecurityUser::getPassword).isEqualTo("NEWPASS");

        AddScreen dup = service.addEnter(
                new AddForm("NEWUSER1", "OTHER", "NAME", "P", "A"));
        assertThat(dup.message()).isEqualTo(CobolMessages.USER_EXISTS);
        assertThat(dup.firstName()).isEqualTo("OTHER");
    }

    @Test
    void updateFetchAndSavePersist_frS1226_frS1231_frS1227_frS1232() {
        UpdateScreen fetched = service.updateFetch(new UpdateForm(
                "U0000003", "", "", "", ""));
        assertThat(fetched.password()).isEqualTo("PASS0003");
        assertThat(fetched.message()).isEqualTo(CobolMessages.USER_SAVE_PROMPT);

        UpdateScreen saved = service.updateSave(new UpdateForm(
                "U0000003", "RENAMED", "LAST0003", "PASS0003", "A"));
        assertThat(saved.message()).isEqualTo("User U0000003 has been updated ...");
        assertThat(userRepository.findById("U0000003")).isPresent()
                .get().extracting(SecurityUser::getFirstName).isEqualTo("RENAMED");

        UpdateScreen unchanged = service.updateSave(new UpdateForm(
                "U0000003", "RENAMED", "LAST0003", "PASS0003", "A"));
        assertThat(unchanged.message()).isEqualTo(CobolMessages.USER_MODIFY_TO_UPDATE);

        assertThat(service.updateFetch(new UpdateForm("GHOST001", "", "", "", ""))
                .message()).isEqualTo(CobolMessages.USER_ID_NOT_FOUND);
        assertThat(service.updateSave(new UpdateForm(
                "GHOST001", "F", "L", "P", "A")).message())
                .isEqualTo(CobolMessages.USER_ID_NOT_FOUND);
    }

    @Test
    void userTypeOutsideDomainFailsLikeOtherError_frS1232() {
        // S12-B1: the CHECK keeps 'A'/'U'; a violation maps to the OTHER path.
        UpdateScreen screen = service.updateSave(new UpdateForm(
                "U0000004", "FIRST0004", "LAST0004", "PASS0004", "X"));
        assertThat(screen.message()).isEqualTo(CobolMessages.USER_UPDATE_FAILED);
        assertThat(userRepository.findById("U0000004")).isPresent()
                .get().extracting(SecurityUser::getUserType).isEqualTo("U");
    }

    @Test
    void deleteRoundTripPersists_frS1238_frS1239() {
        DeleteScreen fetched = service.deleteFetch(new DeleteForm(
                "U0000006", "", "", ""));
        assertThat(fetched.message()).isEqualTo(CobolMessages.USER_DELETE_CONFIRM);

        DeleteScreen deleted = service.deleteDelete(new DeleteForm(
                "U0000006", "", "", ""));
        assertThat(deleted.message()).isEqualTo("User U0000006 has been deleted ...");
        assertThat(userRepository.findById("U0000006")).isEmpty();

        assertThat(service.deleteDelete(new DeleteForm("GHOST001", "", "", ""))
                .message()).isEqualTo(CobolMessages.USER_ID_NOT_FOUND);
    }
}
