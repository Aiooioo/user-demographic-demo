package org.example.demo.profile.collector.store;

import org.example.demo.profile.common.store.ProfileStore;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import java.util.Map;

public class RedisProfileStore implements ProfileStore {
    private final String host;
    private final int port;
    private final String keyPrefix;
    private JedisPool pool;

    public RedisProfileStore(String host, int port, String keyPrefix) {
        this.host = host;
        this.port = port;
        this.keyPrefix = keyPrefix;
        this.pool = new JedisPool(host, port);
    }

    @Override
    public void setTag(String userId, String field, String value) {
        try (Jedis jedis = pool.getResource()) {
            jedis.hset(keyPrefix + userId, field, value);
        }
    }

    @Override
    public void setTags(String userId, Map<String, String> fields) {
        try (Jedis jedis = pool.getResource()) {
            jedis.hset(keyPrefix + userId, fields);
        }
    }

    @Override
    public void close() {
        if (pool != null) {
            pool.close();
        }
    }
}
