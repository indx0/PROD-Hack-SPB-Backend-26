package ru.example.prodbackend.demo.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/demo")
@RequiredArgsConstructor
public class DemoController {

    @Operation(
            summary = "ping pong"
    )
    @GetMapping("/ping")
    public ResponseEntity<String> ping(){
        return ResponseEntity.ok("pong");
    }

    @Operation(
            summary = "ping доступный только после авторизации"
    )
    @SecurityRequirement(name = "jwtAuth")
    @GetMapping("/authping")
    public ResponseEntity<String> authping() {
        return ResponseEntity.ok("auth pong");
    }
}
