package org.example.demo.profile.collector.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.demo.profile.common.model.AdEvent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

@RestController
public class EventIngestController {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper mapper = new ObjectMapper();

    @Value("${collector.kafka.topic}")
    private String topic;

    @Value("${collector.ods.file}")
    private String odsFilePath;

    public EventIngestController(KafkaTemplate<String, String> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    @PostMapping("/collect/event")
    public String collect(@RequestBody AdEvent event) throws Exception {
        String payload = mapper.writeValueAsString(event);
        kafkaTemplate.send(topic, event.getUserId(), payload);

        Path p = Path.of(odsFilePath).toAbsolutePath();
        Path dir = p.getParent();
        if (dir != null && !Files.exists(dir)) {
            Files.createDirectories(dir);
        }
        Files.writeString(p, payload + System.lineSeparator(), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        return "accepted";
    }
}
