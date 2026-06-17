package com.example.demo;

/** In-project interface so DemoRepository yields IMPLEMENTS + OVERRIDES. */
public interface Loader {
    String load(Long id);
}
