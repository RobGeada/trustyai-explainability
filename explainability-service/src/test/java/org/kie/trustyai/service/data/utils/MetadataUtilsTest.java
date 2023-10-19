package org.kie.trustyai.service.data.utils;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.kie.trustyai.explainability.model.Dataframe;
import org.kie.trustyai.service.BaseTestProfile;
import org.kie.trustyai.service.mocks.MockDatasource;
import org.kie.trustyai.service.mocks.MockMemoryStorage;
import org.kie.trustyai.service.payloads.values.DataType;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
@TestProfile(BaseTestProfile.class)
class MetadataUtilsTest {
    @Inject
    Instance<MockDatasource> datasource;

    @Inject
    Instance<MockMemoryStorage> storage;

    @Test
    public void testLargeDataFrameSchema() {
        int ncols = 150_000;
        final Dataframe dataframe = datasource.get().generateRandomNColumnDataframe(1, ncols);
        List<DataType> dataTypeList = Collections.nCopies(ncols, DataType.INT32);


        long startTime = System.currentTimeMillis();
        MetadataUtils.getInputSchema(dataframe);
        long endTime = System.currentTimeMillis();
        System.out.println("done in " + (endTime-startTime)/1000.);

        startTime = System.currentTimeMillis();
        MetadataUtils.getInputSchema(dataframe, dataTypeList);
        endTime = System.currentTimeMillis();
        System.out.println("done in " + (endTime-startTime)/1000.);
    }

    @Test
    public void testGetColumnNames() {
        int ncols = 50_000;
        final Dataframe dataframe = datasource.get().generateRandomNColumnDataframe(1, ncols);
        System.out.println(dataframe.getOutputsIndices());
        long startTime = System.currentTimeMillis();
        for (int i=0; i<ncols; i++) {
            dataframe.getColumnNames();
        }
        long endTime = System.currentTimeMillis();
        System.out.println((endTime-startTime)/1000. + " s");
    }
}