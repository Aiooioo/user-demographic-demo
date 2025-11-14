package org.example.demo.profile.collector.controller;

import org.example.demo.profile.collector.flink.FlinkOfflineProfileJob;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Files;
import java.nio.file.Path;

@RestController
public class BatchTriggerController {

    @Value("${collector.ods.file}")
    private String odsFilePath;

    @Value("${collector.redis.host}")
    private String redisHost;

    @Value("${collector.redis.port}")
    private int redisPort;

    @PostMapping("/collect/batch")
    public String trigger() {
        try {
            Path p = Path.of(odsFilePath).toAbsolutePath();
            Path dir = p.getParent();
            if (dir != null && !Files.exists(dir)) {
                Files.createDirectories(dir);
            }
            if (!Files.exists(p)) {
                Files.createFile(p);
            }
            Thread t = new Thread(() -> {
                try {
                    FlinkOfflineProfileJob.run(odsFilePath, redisHost, redisPort);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }, "flink-offline-profile-job-thread");
            t.setDaemon(true);
            t.start();
            return "started";
        } catch (Exception e) {
            return "error";
        }
    }
}
