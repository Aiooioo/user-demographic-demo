package org.example.demo.profile.collector.store;

import org.example.demo.profile.common.store.ProfileStore;
import org.example.demo.profile.common.store.ProfileStoreFactory;

public class RedisProfileStoreFactory implements ProfileStoreFactory {
    private final String host;
    private final int port;
    private final String keyPrefix;

    public RedisProfileStoreFactory(String host, int port, String keyPrefix) {
        this.host = host;
        this.port = port;
        this.keyPrefix = keyPrefix;
    }

    @Override
    public ProfileStore create() {
        return new RedisProfileStore(host, port, keyPrefix);
    }
}
