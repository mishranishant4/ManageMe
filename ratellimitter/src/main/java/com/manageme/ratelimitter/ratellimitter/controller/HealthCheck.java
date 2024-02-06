package com.manageme.ratelimitter.ratellimitter.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

@RestController("v1")
public class HealthCheck {

    @GetMapping("/healthcheck")
    public Mono<ResponseEntity> healthCheck() {
        return Mono.just(ResponseEntity.ok("UP"));
    }
}
