package org.example.demo.profile.service.controller;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class ProfileController {

    private final StringRedisTemplate redisTemplate;

    public ProfileController(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @GetMapping("/profile/{userId}")
    public Map<Object, Object> getProfile(@PathVariable String userId) {
        String key = "profile:" + userId;
        return redisTemplate.opsForHash().entries(key);
    }
}
