package org.example.demo.profile.collector;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.example.demo.profile.collector.flink.FlinkRealtimeProfileJob;

@Component
public class FlinkJobRunner implements ApplicationRunner {

    @Value("${collector.kafka.bootstrapServers}")
    private String bootstrapServers;

    @Value("${collector.kafka.topic}")
    private String topic;

    @Value("${collector.redis.host}")
    private String redisHost;

    @Value("${collector.redis.port}")
    private int redisPort;

    @Value("${collector.ods.file}")
    private String odsFilePath;

    @Override
    public void run(ApplicationArguments args) {
        Thread t = new Thread(() -> {
            try {
                FlinkRealtimeProfileJob.start(bootstrapServers, topic, redisHost, redisPort, odsFilePath);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }, "flink-realtime-profile-job-thread");
        t.setDaemon(true);
        t.start();
    }
}
