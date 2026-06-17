package com.example.demo;

import org.springframework.stereotype.Repository;

/** Implements Loader => IMPLEMENTS + OVERRIDES (load overrides Loader.load). */
@Repository
public class DemoRepository implements Loader {

    @Override
    public String load(Long id) {
        return "row-" + id;
    }
}
