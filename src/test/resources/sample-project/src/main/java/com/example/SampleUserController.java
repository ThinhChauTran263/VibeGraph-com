package com.example;

import org.springframework.web.bind.annotation.*;

/**
 * Sample Controller for parser testing.
 * Tests should detect: CONTROLLER layer, route paths, HTTP methods.
 */
@RestController
@RequestMapping("/api/users")
public class SampleUserController {

    private final SampleUserService userService;

    public SampleUserController(SampleUserService userService) {
        this.userService = userService;
    }

    @GetMapping("/{id}")
    public SampleUser getUser(@PathVariable Long id) {
        return userService.findById(id);
    }

    @PostMapping
    public SampleUser createUser(@RequestBody CreateUserRequest request) {
        return userService.create(request.name(), request.email());
    }

    @DeleteMapping("/{id}")
    public void deleteUser(@PathVariable Long id) {
        userService.deleteById(id);
    }

    public record CreateUserRequest(String name, String email) {}
}
