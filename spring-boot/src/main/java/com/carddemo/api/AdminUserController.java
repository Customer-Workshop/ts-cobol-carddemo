package com.carddemo.api;

import com.carddemo.service.AdminUserService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController {
    private final AdminUserService service;

    public AdminUserController(AdminUserService service) {
        this.service = service;
    }

    @GetMapping
    public AdminUserListResponse list(@RequestParam(required = false) String userId,
                                      @RequestParam(defaultValue = "0") int page) {
        return service.list(userId, page);
    }

    @PostMapping
    public AdminUserResponse add(@RequestBody AdminUserRequest request) {
        return service.add(request);
    }

    @PutMapping("/{userId}")
    public AdminUserResponse update(@PathVariable String userId,
                                    @RequestBody AdminUserRequest request) {
        return service.update(userId, request);
    }

    @DeleteMapping("/{userId}")
    public void delete(@PathVariable String userId) {
        service.delete(userId);
    }
}
