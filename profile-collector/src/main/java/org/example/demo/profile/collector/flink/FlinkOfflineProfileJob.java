package org.example.demo.profile.collector.flink;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.flink.api.common.RuntimeExecutionMode;
import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.example.demo.profile.common.model.AdEvent;
import org.example.demo.profile.common.model.EventType;
import org.example.demo.profile.collector.store.RedisProfileStoreFactory;

import java.math.BigDecimal;

public class FlinkOfflineProfileJob {

    public static void run(String odsFilePath, String redisHost, int redisPort) throws Exception {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setRuntimeMode(RuntimeExecutionMode.BATCH);

        ObjectMapper mapper = new ObjectMapper();
        DataStream<AdEvent> events = env.readTextFile(odsFilePath).map(s -> mapper.readValue(s, AdEvent.class));

        DataStream<Tuple2<String, BigDecimal>> ltv = events
                .filter(e -> e.getEventType() == EventType.PURCHASE)
                .map(e -> Tuple2.of(e.getUserId(), e.getAmount() == null ? BigDecimal.ZERO : e.getAmount()))
                .keyBy(t -> t.f0)
                .reduce((a, b) -> Tuple2.of(a.f0, a.f1.add(b.f1)));

        RedisProfileStoreFactory factory = new RedisProfileStoreFactory(redisHost, redisPort, "profile:");
        ltv.map(t -> Tuple2.of(t.f0, t.f1.toPlainString())).addSink(new TagStoreStringSink(factory, "ltv_total"));

        ltv.map(t -> Tuple2.of(t.f0, t.f1.compareTo(new BigDecimal("100")) >= 0 ? "high_value_user" : "normal_user"))
                .addSink(new TagStoreStringSink(factory, "segment_id"));

        env.execute("collector-offline-profile-job");
    }

    
}
