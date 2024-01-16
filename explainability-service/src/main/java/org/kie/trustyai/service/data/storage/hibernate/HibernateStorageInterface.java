package org.kie.trustyai.service.data.storage.hibernate;

import org.kie.trustyai.explainability.model.Dataframe;
import org.kie.trustyai.service.data.cache.DataCacheKeyGen;
import org.kie.trustyai.service.data.exceptions.StorageReadException;
import org.kie.trustyai.service.data.exceptions.StorageWriteException;
import org.kie.trustyai.service.data.metadata.Metadata;
import org.kie.trustyai.service.data.storage.StorageInterface;

import io.quarkus.cache.CacheResult;

public interface HibernateStorageInterface extends StorageInterface {
    // dataframes
    @CacheResult(cacheName = "dataframe", keyGenerator = DataCacheKeyGen.class)
    Dataframe readData(String modelId) throws StorageReadException;

    Dataframe readData(String modelId, int batchSize) throws StorageReadException;

    void save(Dataframe dataframe, String modelId) throws StorageWriteException;

    // metadata
    Metadata readMetadata(String modelId) throws StorageReadException;

    void saveMetadata(Metadata metadata, String modelId) throws StorageWriteException;

    // appenders
    void append(Dataframe dataframe, String location) throws StorageWriteException;

    // info queries
    boolean dataframeExists(String modelId) throws StorageReadException;

}