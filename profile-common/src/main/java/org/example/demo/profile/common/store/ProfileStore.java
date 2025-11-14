package org.example.demo.profile.common.store;

public interface ProfileStore extends AutoCloseable {
    void setTag(String userId, String field, String value);
    void setTags(String userId, java.util.Map<String, String> fields);
    @Override
    void close();
}
