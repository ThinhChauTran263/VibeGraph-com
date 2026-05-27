package com.example;

/**
 * Sample entity for parser testing.
 * Tests should extract: fields with types and visibility.
 */
public class SampleUser {
    private Long id;
    private String name;
    private String email;
    private boolean active = true;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
