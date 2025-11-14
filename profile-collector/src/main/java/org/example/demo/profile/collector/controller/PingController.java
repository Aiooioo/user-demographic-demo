package org.example.demo.profile.collector.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PingController {

    @GetMapping("/collector/ping")
    public String ping() {
        return "ok";
    }
}
