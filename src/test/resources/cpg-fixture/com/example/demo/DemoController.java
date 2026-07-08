package com.example.demo;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Route entrypoint => HANDLES_ROUTE; extends BaseHandler => EXTENDS + OVERRIDES;
 * constructor-injects DemoService => Constructor node + CALLS into the service
 * (the STEP_IN_FLOW chain: get -> DemoService.find -> DemoRepository.load).
 */
@RestController
@RequestMapping("/api/demo")
public class DemoController extends BaseHandler {

    private final DemoService service;

    public DemoController(DemoService service) {
        this.service = service;
    }

    @Override
    public void handle() {
        // overrides BaseHandler.handle()
    }

    @GetMapping("/{id}")
    public String get(Long id) {
        return service.find(id); // CALLS -> STEP_IN_FLOW
    }
}
