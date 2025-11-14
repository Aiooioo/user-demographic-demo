package org.example.demo.profile.common.store;

import java.io.Serializable;

public interface ProfileStoreFactory extends Serializable {
    ProfileStore create();
}
