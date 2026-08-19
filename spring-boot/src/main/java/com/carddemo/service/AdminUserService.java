package com.carddemo.service;

import com.carddemo.api.*;
import com.carddemo.model.SecurityUser;
import com.carddemo.repository.SecurityUserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@Service
public class AdminUserService {
    private static final int PAGE_SIZE = 10;
    private final SecurityUserRepository repository;

    public AdminUserService(SecurityUserRepository repository) {
        this.repository = repository;
    }

    public AdminUserListResponse list(String filter, int page) {
        if (page < 0) throw bad(CobolMessages.INVALID_OPTION);
        List<SecurityUser> users = repository.findAll().stream()
                .filter(u -> filter == null || filter.isBlank()
                        || u.getUserId().contains(filter.trim().toUpperCase(Locale.ROOT)))
                .sorted(Comparator.comparing(SecurityUser::getUserId)).toList();
        int from = page * PAGE_SIZE;
        if (from > users.size()) throw notFound(CobolMessages.USER_NOT_FOUND);
        int to = Math.min(from + PAGE_SIZE, users.size());
        return new AdminUserListResponse(page, PAGE_SIZE, to < users.size(), page > 0,
                users.subList(from, to).stream().map(this::response).toList());
    }

    @Transactional
    public AdminUserResponse add(AdminUserRequest request) {
        String id = normalizeId(request.userId());
        if (repository.existsById(id)) throw bad(CobolMessages.USER_EXISTS);
        SecurityUser user = new SecurityUser();
        fill(user, id, request);
        repository.save(user);
        return response(user);
    }

    @Transactional
    public AdminUserResponse update(String rawId, AdminUserRequest request) {
        String id = normalizeId(rawId);
        SecurityUser user = repository.findById(id)
                .orElseThrow(() -> notFound(CobolMessages.USER_NOT_FOUND));
        fill(user, id, request);
        repository.save(user);
        return response(user);
    }

    @Transactional
    public void delete(String rawId) {
        String id = normalizeId(rawId);
        if (repository.findById(id).isEmpty()) throw notFound(CobolMessages.USER_NOT_FOUND);
        repository.deleteById(id);
    }

    private void fill(SecurityUser user, String id, AdminUserRequest request) {
        user.setUserId(id);
        user.setFirstName(trim(request.firstName()));
        user.setLastName(trim(request.lastName()));
        user.setPassword(trim(request.password()));
        String type = trim(request.userType()).toUpperCase(Locale.ROOT);
        if (!type.equals("A") && !type.equals("U")) throw bad(CobolMessages.USER_TYPE_INVALID);
        user.setUserType(type);
    }

    private String normalizeId(String value) {
        if (value == null || value.isBlank()) throw bad(CobolMessages.USER_ID_REQUIRED);
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private AdminUserResponse response(SecurityUser user) {
        return new AdminUserResponse(user.getUserId(), user.getFirstName(),
                user.getLastName(), user.getUserType());
    }

    private CobolApiException bad(String message) {
        return new CobolApiException(HttpStatus.BAD_REQUEST, message);
    }

    private CobolApiException notFound(String message) {
        return new CobolApiException(HttpStatus.NOT_FOUND, message);
    }
}
