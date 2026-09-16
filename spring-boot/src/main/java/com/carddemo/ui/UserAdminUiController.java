package com.carddemo.ui;

import com.carddemo.service.AdminUserService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Thymeleaf surface for the four user admin programs under /admin (ROLE_ADMIN
 * is enforced by SecurityConfig — S12-B5). The 3270 AID key arrives as the
 * form field {@code aid} and each map's fields echo back as form inputs;
 * the COMMAREA slices ride along as hidden fields and the {@code userId} /
 * {@code from} route params (S12-B4 — CDEMO-CU0n-USR-SELECTED /
 * CDEMO-FROM-PROGRAM). Paragraph cites are app/cbl/COUSR0*.cbl.
 */
@Controller
public class UserAdminUiController {
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("MM/dd/yy");
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final AdminUserService service;

    public UserAdminUiController(AdminUserService service) {
        this.service = service;
    }

    @ModelAttribute
    void addHeaderFields(Model model) {
        LocalDateTime now = LocalDateTime.now();
        model.addAttribute("currentDate", now.format(DATE_FORMAT));
        model.addAttribute("currentTime", now.format(TIME_FORMAT));
    }

    // ===================== COUSR00C — user list =====================

    // First display is an ENTER on an empty map — page 1 browsed from the
    // start of the file.
    @GetMapping("/admin/users")
    public String userList(Model model) {
        return renderList(model, service.firstDisplay());
    }

    // Pseudo-conversational turn: ENTER runs the selection scan and forward
    // browse, PF7/PF8 page backward/forward, PF3 transfers to the admin
    // menu (CDEMO-FROM-PROGRAM), anything else is an invalid key.
    @PostMapping("/admin/users")
    public String submitUserList(
            @RequestParam(name = "aid", defaultValue = "ENTER") String aid,
            @RequestParam(name = "usrIdIn", required = false) String usrIdIn,
            @RequestParam(name = "sel", required = false) List<String> sels,
            @RequestParam(name = "usrId", required = false) List<String> usrIds,
            @RequestParam(name = "fname", required = false) List<String> fnames,
            @RequestParam(name = "lname", required = false) List<String> lnames,
            @RequestParam(name = "utype", required = false) List<String> utypes,
            @RequestParam(name = "pageDisplay", required = false) String pageDisplay,
            @RequestParam(name = "firstId", required = false) String firstId,
            @RequestParam(name = "lastId", required = false) String lastId,
            @RequestParam(name = "pageNum", required = false) String pageNum,
            @RequestParam(name = "nextPage", required = false) String nextPage,
            Model model) {
        if ("PF3".equals(aid)) {
            return "redirect:/admin/menu";
        }
        AdminUserService.Form form = new AdminUserService.Form(
                usrIdIn == null ? "" : usrIdIn,
                AdminUserService.Form.selections(sels),
                AdminUserService.Form.rows(usrIds, fnames, lnames, utypes),
                pageDisplay == null ? "" : pageDisplay,
                firstId, lastId, pageNum, nextPage);
        AdminUserService.Page page;
        switch (aid) {
            case "ENTER" -> {
                AdminUserService.EnterOutcome outcome = service.enter(form);
                if (outcome.selectedId() != null) {
                    // XCTL to COUSR02C/COUSR03C (S12-B4): userId carries
                    // CDEMO-CU0n-USR-SELECTED, from=list is FROM-PROGRAM.
                    String target = "U".equalsIgnoreCase(outcome.selection())
                            ? "/admin/users/update" : "/admin/users/delete";
                    return "redirect:" + target + "?userId="
                            + URLEncoder.encode(outcome.selectedId(), StandardCharsets.UTF_8)
                            + "&from=list";
                }
                page = outcome.page();
            }
            case "PF7" -> page = service.pf7(form);
            case "PF8" -> page = service.pf8(form);
            default -> page = service.invalidAid(form);
        }
        return renderList(model, page);
    }

    private String renderList(Model model, AdminUserService.Page page) {
        model.addAttribute("page", page);
        model.addAttribute("message", page.message());
        model.addAttribute("messageStyle", page.info() ? "info" : null);
        return "user-list";
    }

    // ===================== COUSR01C — add user =====================

    @GetMapping("/admin/users/add")
    public String userAdd(Model model) {
        return renderAdd(model, AdminUserService.AddScreen.blank());
    }

    // AID map: ENTER validates and writes, PF3 exits to COADM01C, PF4 clears
    // the map, anything else (incl. PF12 despite the footer) is invalid.
    @PostMapping("/admin/users/add")
    public String submitUserAdd(
            @RequestParam(name = "aid", defaultValue = "ENTER") String aid,
            @RequestParam(name = "fname", required = false) String fname,
            @RequestParam(name = "lname", required = false) String lname,
            @RequestParam(name = "userId", required = false) String userId,
            @RequestParam(name = "passwd", required = false) String passwd,
            @RequestParam(name = "usrtype", required = false) String usrtype,
            Model model) {
        if ("PF3".equals(aid)) {
            return "redirect:/admin/menu";
        }
        if ("PF4".equals(aid)) {
            return renderAdd(model, service.addClear());
        }
        AdminUserService.AddForm form = new AdminUserService.AddForm(
                nvl(userId), nvl(fname), nvl(lname), nvl(passwd), nvl(usrtype));
        AdminUserService.AddScreen screen = switch (aid) {
            case "ENTER" -> service.addEnter(form);
            default -> service.addInvalidAid(form);
        };
        return renderAdd(model, screen);
    }

    private String renderAdd(Model model, AdminUserService.AddScreen screen) {
        model.addAttribute("screen", screen);
        model.addAttribute("message", screen.message());
        model.addAttribute("messageStyle", screen.messageStyle());
        return "user-add";
    }

    // ===================== COUSR02C — update user =====================

    // A populated userId param (CDEMO-CU02-USR-SELECTED from the list, or a
    // direct entry) runs ENTER processing at once; a bare entry shows the
    // empty map.
    @GetMapping("/admin/users/update")
    public String userUpdate(@RequestParam(name = "userId", required = false) String userId,
                             @RequestParam(name = "from", required = false) String from,
                             Model model) {
        model.addAttribute("from", from == null ? "" : from);
        return renderUpdate(model, service.updateEntry(userId));
    }

    // AID map: ENTER fetches, PF5 saves and stays, PF3 saves then exits to
    // the caller regardless of the save outcome, PF4 clears, PF12 exits to
    // COADM01C without saving, anything else is an invalid key.
    @PostMapping("/admin/users/update")
    public String submitUserUpdate(
            @RequestParam(name = "aid", defaultValue = "ENTER") String aid,
            @RequestParam(name = "usrIdIn", required = false) String usrIdIn,
            @RequestParam(name = "fname", required = false) String fname,
            @RequestParam(name = "lname", required = false) String lname,
            @RequestParam(name = "passwd", required = false) String passwd,
            @RequestParam(name = "usrtype", required = false) String usrtype,
            @RequestParam(name = "from", required = false) String from,
            Model model) {
        if ("PF12".equals(aid)) {
            return "redirect:/admin/menu";
        }
        if ("PF4".equals(aid)) {
            model.addAttribute("from", from == null ? "" : from);
            return renderUpdate(model, service.updateClear());
        }
        AdminUserService.UpdateForm form = new AdminUserService.UpdateForm(
                nvl(usrIdIn), nvl(fname), nvl(lname), nvl(passwd), nvl(usrtype));
        AdminUserService.UpdateScreen screen;
        switch (aid) {
            case "ENTER" -> screen = service.updateFetch(form);
            case "PF5" -> screen = service.updateSave(form);
            case "PF3" -> {
                service.updateSave(form);   // outcome discarded; PF3 always exits
                return "redirect:" + fromRoute(from);
            }
            default -> screen = service.updateInvalidAid(form);
        }
        model.addAttribute("from", from == null ? "" : from);
        return renderUpdate(model, screen);
    }

    private String renderUpdate(Model model, AdminUserService.UpdateScreen screen) {
        model.addAttribute("screen", screen);
        model.addAttribute("message", screen.message());
        model.addAttribute("messageStyle", screen.messageStyle());
        return "user-update";
    }

    // ===================== COUSR03C — delete user =====================

    // Same entry convention as update: userId triggers an immediate fetch.
    @GetMapping("/admin/users/delete")
    public String userDelete(@RequestParam(name = "userId", required = false) String userId,
                             @RequestParam(name = "from", required = false) String from,
                             Model model) {
        model.addAttribute("from", from == null ? "" : from);
        return renderDelete(model, service.deleteEntry(userId));
    }

    // AID map: ENTER fetches, PF5 deletes, PF3 exits to the caller WITHOUT
    // deleting (no DELETE-USER-INFO call in the source), PF4 clears, PF12
    // exits to COADM01C, anything else is an invalid key.
    @PostMapping("/admin/users/delete")
    public String submitUserDelete(
            @RequestParam(name = "aid", defaultValue = "ENTER") String aid,
            @RequestParam(name = "usrIdIn", required = false) String usrIdIn,
            @RequestParam(name = "fname", required = false) String fname,
            @RequestParam(name = "lname", required = false) String lname,
            @RequestParam(name = "usrtype", required = false) String usrtype,
            @RequestParam(name = "from", required = false) String from,
            Model model) {
        if ("PF12".equals(aid)) {
            return "redirect:/admin/menu";
        }
        if ("PF3".equals(aid)) {
            return "redirect:" + fromRoute(from);
        }
        if ("PF4".equals(aid)) {
            model.addAttribute("from", from == null ? "" : from);
            return renderDelete(model, service.deleteClear());
        }
        AdminUserService.DeleteForm form = new AdminUserService.DeleteForm(
                nvl(usrIdIn), nvl(fname), nvl(lname), nvl(usrtype));
        AdminUserService.DeleteScreen screen = switch (aid) {
            case "ENTER" -> service.deleteFetch(form);
            case "PF5" -> service.deleteDelete(form);
            default -> service.deleteInvalidAid(form);
        };
        model.addAttribute("from", from == null ? "" : from);
        return renderDelete(model, screen);
    }

    private String renderDelete(Model model, AdminUserService.DeleteScreen screen) {
        model.addAttribute("screen", screen);
        model.addAttribute("message", screen.message());
        model.addAttribute("messageStyle", screen.messageStyle());
        return "user-delete";
    }

    // S12-B4: the caller route is 'list' (COUSR00C) or absent — the admin
    // menu (COADM01C) is the FROM-PROGRAM default.
    private String fromRoute(String from) {
        return "list".equals(from) ? "/admin/users" : "/admin/menu";
    }

    private static String nvl(String value) {
        return value == null ? "" : value;
    }
}
