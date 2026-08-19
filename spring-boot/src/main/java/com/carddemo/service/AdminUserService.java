package com.carddemo.service;

import com.carddemo.api.*;
import com.carddemo.model.SecurityUser;
import com.carddemo.repository.SecurityUserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
        if (page < 0) throw bad(CobolMessages.USER_TOP);
        Pageable pageable = PageRequest.of(page, PAGE_SIZE,
                Sort.by(Sort.Direction.ASC, "userId"));
        Page<SecurityUser> users = filter == null || filter.isBlank()
                ? repository.findAll(pageable)
                : repository.findByUserIdGreaterThanEqual(
                        filter.trim().toUpperCase(Locale.ROOT), pageable);
        if (users.isEmpty()) throw notFound(CobolMessages.USER_BOTTOM);
        return new AdminUserListResponse(page, PAGE_SIZE, users.hasNext(), page > 0,
                users.getContent().stream().map(this::response).toList());
    }

    @Transactional
    public AdminUserResponse add(AdminUserRequest request) {
        if (request == null) throw bad(CobolMessages.USER_ID_REQUIRED_EDIT);
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
                .orElseThrow(() -> notFound(CobolMessages.USER_ID_NOT_FOUND));
        fill(user, id, request);
        repository.save(user);
        return response(user);
    }

    @Transactional
    public void delete(String rawId, String confirmation) {
        String id = normalizeId(rawId);
        if (repository.findById(id).isEmpty()) throw notFound(CobolMessages.USER_ID_NOT_FOUND);
        if (!"Y".equalsIgnoreCase(confirmation)) throw bad(CobolMessages.USER_DELETE_CONFIRM);
        repository.deleteById(id);
    }

    private void fill(SecurityUser user, String id, AdminUserRequest request) {
        if (request == null) throw bad(CobolMessages.USER_ID_REQUIRED_EDIT);
        if (blank(request.firstName())) throw bad(CobolMessages.FIRST_NAME_REQUIRED);
        if (blank(request.lastName())) throw bad(CobolMessages.LAST_NAME_REQUIRED);
        if (blank(request.userId())) throw bad(CobolMessages.USER_ID_REQUIRED_EDIT);
        if (blank(request.password())) throw bad(CobolMessages.PASSWORD_REQUIRED_EDIT);
        if (blank(request.userType())) throw bad(CobolMessages.USER_TYPE_REQUIRED);
        user.setUserId(id);
        user.setFirstName(request.firstName().trim());
        user.setLastName(request.lastName().trim());
        user.setPassword(request.password().trim().toUpperCase(Locale.ROOT));
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

    private boolean blank(String value) {
        return value == null || value.isBlank();
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
