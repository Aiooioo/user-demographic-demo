package org.example.demo.profile.collector.flink;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.flink.api.common.eventtime.SerializableTimestampAssigner;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.functions.AggregateFunction;
import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer;
import org.apache.flink.api.common.serialization.SimpleStringSchema;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.datastream.KeyedStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.sink.RichSinkFunction;
import org.apache.flink.streaming.api.functions.windowing.ProcessWindowFunction;
import org.apache.flink.streaming.api.windowing.assigners.SlidingEventTimeWindows;
import org.apache.flink.streaming.api.windowing.time.Time;
import org.apache.flink.streaming.api.windowing.windows.TimeWindow;
import org.apache.flink.util.Collector;
import org.example.demo.profile.common.model.AdEvent;
import org.example.demo.profile.common.model.EventType;
import org.apache.flink.configuration.Configuration;
import org.example.demo.profile.collector.store.RedisProfileStoreFactory;

import java.io.BufferedWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;

public class FlinkRealtimeProfileJob {

    public static void start(String bootstrapServers, String topic, String redisHost, int redisPort, String odsFilePath) throws Exception {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.getConfig().setAutoWatermarkInterval(1000);

        KafkaSource<String> source = KafkaSource.<String>builder()
                .setBootstrapServers(bootstrapServers)
                .setTopics(topic)
                .setGroupId("collector-flink-consumer")
                .setStartingOffsets(OffsetsInitializer.earliest())
                .setValueOnlyDeserializer(new SimpleStringSchema())
                .build();

        ObjectMapper mapper = new ObjectMapper();

        DataStream<String> raw = env.fromSource(source, WatermarkStrategy.noWatermarks(), "kafka-source");
        DataStream<AdEvent> events = raw.map(value -> mapper.readValue(value, AdEvent.class))
                .assignTimestampsAndWatermarks(WatermarkStrategy
                        .<AdEvent>forBoundedOutOfOrderness(Duration.ofSeconds(5))
                        .withTimestampAssigner(ctx -> (element, recordTimestamp) -> element.getTimestamp()));

        events.addSink(new OdsFileSink(odsFilePath));

        KeyedStream<AdEvent, String> keyedClicks = events
                .filter(e -> e.getEventType() == EventType.CLICK)
                .keyBy(AdEvent::getUserId);
        KeyedStream<AdEvent, String> keyedViews = events
                .filter(e -> e.getEventType() == EventType.IMPRESSION)
                .keyBy(AdEvent::getUserId);

        DataStream<Tuple2<String, Long>> clicks24h = keyedClicks
                .window(SlidingEventTimeWindows.of(Time.hours(24), Time.minutes(1)))
                .aggregate(new CountAgg(), new AddKeyProcess());

        DataStream<Tuple2<String, Long>> views5m = keyedViews
                .window(SlidingEventTimeWindows.of(Time.minutes(5), Time.seconds(30)))
                .aggregate(new CountAgg(), new AddKeyProcess());

        RedisProfileStoreFactory factory = new RedisProfileStoreFactory(redisHost, redisPort, "profile:");
        clicks24h.addSink(new TagStoreSink(factory, "clicks_24h"));
        views5m.addSink(new TagStoreSink(factory, "views_5m"));

        env.execute("collector-realtime-profile-job");
    }

    static class CountAgg implements AggregateFunction<AdEvent, Long, Long> {
        @Override
        public Long createAccumulator() { return 0L; }
        @Override
        public Long add(AdEvent value, Long accumulator) { return accumulator + 1; }
        @Override
        public Long getResult(Long accumulator) { return accumulator; }
        @Override
        public Long merge(Long a, Long b) { return a + b; }
    }

    static class AddKeyProcess extends ProcessWindowFunction<Long, Tuple2<String, Long>, String, TimeWindow> {
        @Override
        public void process(String key, Context context, Iterable<Long> elements, Collector<Tuple2<String, Long>> out) {
            Long v = elements.iterator().next();
            out.collect(Tuple2.of(key, v));
        }
    }

    

    static class OdsFileSink extends RichSinkFunction<AdEvent> {
        private final String path;
        private transient BufferedWriter writer;
        private final ObjectMapper mapper = new ObjectMapper();

        OdsFileSink(String path) {
            this.path = path;
        }

        @Override
        public void open(Configuration parameters) throws Exception {
            Path p = Path.of(path).toAbsolutePath();
            Path dir = p.getParent();
            if (dir != null && !Files.exists(dir)) {
                Files.createDirectories(dir);
            }
            writer = Files.newBufferedWriter(p, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        }

        @Override
        public void invoke(AdEvent value, Context context) throws Exception {
            writer.write(mapper.writeValueAsString(value));
            writer.newLine();
            writer.flush();
        }
    }
}
