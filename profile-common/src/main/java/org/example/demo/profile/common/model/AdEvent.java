package org.example.demo.profile.common.model;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AdEvent {
    private String eventId;
    private String userId;
    private EventType eventType;
    private long timestamp;
    private String category;
    private BigDecimal amount;
}
