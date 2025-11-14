package org.example.demo.profile.collector.flink;

import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.functions.sink.RichSinkFunction;
import org.example.demo.profile.common.store.ProfileStore;
import org.example.demo.profile.common.store.ProfileStoreFactory;

public class TagStoreStringSink extends RichSinkFunction<Tuple2<String, String>> {
    private final ProfileStoreFactory factory;
    private final String field;
    private transient ProfileStore store;

    public TagStoreStringSink(ProfileStoreFactory factory, String field) {
        this.factory = factory;
        this.field = field;
    }

    @Override
    public void open(Configuration parameters) {
        this.store = factory.create();
    }

    @Override
    public void invoke(Tuple2<String, String> value, Context context) {
        store.setTag(value.f0, field, value.f1);
    }

    @Override
    public void close() {
        if (store != null) {
            try { store.close(); } catch (Exception ignored) {}
        }
    }
}
