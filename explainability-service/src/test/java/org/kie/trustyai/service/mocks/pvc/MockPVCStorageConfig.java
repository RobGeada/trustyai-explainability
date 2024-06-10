package org.kie.trustyai.service.mocks.pvc;

import java.util.Optional;

import org.kie.trustyai.service.config.storage.StorageConfig;

public class MockPVCStorageConfig implements StorageConfig {
    @Override
    public Optional<String> dataFilename() {
        return Optional.of("data.csv");
    }

    @Override
    public Optional<String> dataFolder() {
        return Optional.of("/tmp");
    }
}
