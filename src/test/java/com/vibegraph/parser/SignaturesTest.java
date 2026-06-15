package com.vibegraph.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Signatures")
class SignaturesTest {

    @Test
    @DisplayName("method without params matches existing format")
    void methodWithoutParamsMatchesExistingFormat() {
        String signature = Signatures.method("com.example.UserService", "findAll", List.of());

        assertEquals("com.example.UserService.findAll()", signature);
    }

    @Test
    @DisplayName("method with one param matches existing format")
    void methodWithOneParamMatchesExistingFormat() {
        String signature = Signatures.method("com.example.UserService", "findById", List.of("Long"));

        assertEquals("com.example.UserService.findById(Long)", signature);
    }

    @Test
    @DisplayName("method with many params matches existing format")
    void methodWithManyParamsMatchesExistingFormat() {
        String signature = Signatures.method(
                "com.example.UserService",
                "search",
                List.of("String", "int", "java.util.List<Long>"));

        assertEquals("com.example.UserService.search(String,int,java.util.List<Long>)", signature);
    }
}
