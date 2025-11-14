package org.example.demo.profile.gateway.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ConfigController {

    @Value("${testNacosKey}")
    String testKey;


    @RequestMapping("/testNacosKey")
    public String test() {
        return testKey;
    }
}
