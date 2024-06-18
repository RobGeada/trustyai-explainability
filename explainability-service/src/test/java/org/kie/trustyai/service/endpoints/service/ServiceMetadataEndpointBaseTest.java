package org.kie.trustyai.service.endpoints.service;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.hamcrest.Matchers;
import org.jboss.resteasy.reactive.RestResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.kie.trustyai.explainability.model.dataframe.Dataframe;
import org.kie.trustyai.service.data.datasources.DataSource;
import org.kie.trustyai.service.payloads.metrics.fairness.group.GroupMetricRequest;
import org.kie.trustyai.service.payloads.service.DataTagging;
import org.kie.trustyai.service.payloads.service.NameMapping;
import org.kie.trustyai.service.payloads.service.ServiceMetadata;
import org.kie.trustyai.service.payloads.values.reconcilable.ReconcilableFeature;
import org.kie.trustyai.service.payloads.values.reconcilable.ReconcilableOutput;
import org.kie.trustyai.service.utils.DataframeGenerators;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.node.IntNode;

import io.restassured.common.mapper.TypeRef;
import io.restassured.http.ContentType;

import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.*;

abstract class ServiceMetadataEndpointBaseTest {

    private static final String MODEL_ID = "example1";
    private final String metadataUrl = "/info";

    @Inject
    Instance<DataSource> datasource;

    public abstract void resetDatasource() throws JsonProcessingException;

    public abstract void saveDataframe(Dataframe dataframe, String modelId);

    @Test
    void getTwoObservations() throws JsonProcessingException {
        final Dataframe dataframe = DataframeGenerators.generateRandomDataframe(2);
        saveDataframe(dataframe, MODEL_ID);

        final List<ServiceMetadata> serviceMetadata = given()
                .when().get(metadataUrl)
                .then()
                .statusCode(200)
                .extract()
                .body().as(new TypeRef<List<ServiceMetadata>>() {
                });

        assertEquals(1, serviceMetadata.size());
        assertEquals(0, serviceMetadata.get(0).getMetrics().scheduledMetadata.getCount("DIR"));
        assertEquals(0, serviceMetadata.get(0).getMetrics().scheduledMetadata.getCount("SPD"));
        assertEquals(2, serviceMetadata.get(0).getData().getObservations());
        assertEquals(2, serviceMetadata.get(0).getData().getInputSchema().getItems().get("age").getColumnValues().size());
        assertFalse(serviceMetadata.get(0).getData().getOutputSchema().getItems().isEmpty());
        assertFalse(serviceMetadata.get(0).getData().getInputSchema().getItems().isEmpty());
        assertEquals(new HashSet<>(dataframe.getInputNames()),
                serviceMetadata.get(0).getData().getInputSchema().getItems().keySet());
        assertEquals(
                new HashSet<>(dataframe.getOutputNames()),
                serviceMetadata.get(0).getData().getOutputSchema().getItems().keySet());
    }

    @Test
    void getThousandObservations() throws JsonProcessingException {
        final Dataframe dataframe = DataframeGenerators.generateRandomDataframe(1000, 50, false);
        saveDataframe(dataframe, MODEL_ID);

        final List<ServiceMetadata> serviceMetadata = given()
                .when().get(metadataUrl)
                .then()
                .statusCode(200)
                .extract()
                .body().as(new TypeRef<List<ServiceMetadata>>() {
                });

        assertEquals(1, serviceMetadata.size());
        assertEquals(0, serviceMetadata.get(0).getMetrics().scheduledMetadata.getCount("DIR"));
        assertEquals(0, serviceMetadata.get(0).getMetrics().scheduledMetadata.getCount("SPD"));
        assertEquals(1000, serviceMetadata.get(0).getData().getObservations());

        // check column values
        assertEquals(50, serviceMetadata.get(0).getData().getInputSchema().getItems().get("age").getColumnValues().size());
        assertEquals(2, serviceMetadata.get(0).getData().getInputSchema().getItems().get("race").getColumnValues().size());
        assertFalse(serviceMetadata.get(0).getData().getOutputSchema().getItems().isEmpty());
        assertFalse(serviceMetadata.get(0).getData().getInputSchema().getItems().isEmpty());
    }

    @Test
    void getThousandDiverseObservations() throws JsonProcessingException {
        final Dataframe dataframe = DataframeGenerators.generateRandomDataframe(1000, 1000, false);
        saveDataframe(dataframe, MODEL_ID);

        final List<ServiceMetadata> serviceMetadata = given()
                .when().get(metadataUrl)
                .then()
                .statusCode(200)
                .extract()
                .body().as(new TypeRef<List<ServiceMetadata>>() {
                });

        assertEquals(1, serviceMetadata.size());
        assertEquals(0, serviceMetadata.get(0).getMetrics().scheduledMetadata.getCount("DIR"));
        assertEquals(0, serviceMetadata.get(0).getMetrics().scheduledMetadata.getCount("SPD"));
        assertEquals(1000, serviceMetadata.get(0).getData().getObservations());

        // check column values

        assertEquals(null, serviceMetadata.get(0).getData().getInputSchema().getItems().get("age").getColumnValues());
        assertEquals(2, serviceMetadata.get(0).getData().getInputSchema().getItems().get("race").getColumnValues().size());
        assertNull(serviceMetadata.get(0).getData().getInputSchema().getItems().get("age").getColumnValues());
        assertEquals(2, serviceMetadata.get(0).getData().getInputSchema().getItems().get("race").getColumnValues().size());
        assertFalse(serviceMetadata.get(0).getData().getOutputSchema().getItems().isEmpty());
        assertFalse(serviceMetadata.get(0).getData().getInputSchema().getItems().isEmpty());
    }

    @Test
    void getNoObservations() throws JsonProcessingException {
        resetDatasource();
        final List<ServiceMetadata> serviceMetadata = given()
                .when().get(metadataUrl)
                .then()
                .statusCode(RestResponse.StatusCode.OK)
                .extract()
                .body().as(new TypeRef<List<ServiceMetadata>>() {
                });

        assertEquals(0, serviceMetadata.size());
    }

    @Test
    void setNameMapping() throws JsonProcessingException {
        final Dataframe dataframe = DataframeGenerators.generateRandomDataframe(1000, 10, false);
        saveDataframe(dataframe, MODEL_ID);

        HashMap<String, String> inputMapping = new HashMap<>();
        HashMap<String, String> outputMapping = new HashMap<>();
        inputMapping.put("age", "Age Mapped");
        inputMapping.put("gender", "Gender Mapped");
        inputMapping.put("race", "Race Mapped");

        outputMapping.put("income", "Income Mapped");
        NameMapping nameMapping = new NameMapping(MODEL_ID, inputMapping, outputMapping);

        given()
                .contentType(ContentType.JSON)
                .body(nameMapping)
                .when().post(metadataUrl + "/names")
                .then()
                .statusCode(200)
                .body(is("Feature and output name mapping successfully applied."));

        final List<ServiceMetadata> serviceMetadata = given()
                .when().get(metadataUrl)
                .then()
                .statusCode(RestResponse.StatusCode.OK)
                .extract()
                .body().as(new TypeRef<List<ServiceMetadata>>() {
                });

        for (String value : serviceMetadata.get(0).getData().getInputSchema().getNameMapping().values()) {
            assertTrue(value.contains("Mapped"));
        }

        for (String value : serviceMetadata.get(0).getData().getOutputSchema().getNameMapping().values()) {
            assertTrue(value.contains("Mapped"));
        }
    }

    @Test
    void setNameMappingPartial() throws JsonProcessingException {
        final Dataframe dataframe = DataframeGenerators.generateRandomDataframe(1000, 10, false);
        saveDataframe(dataframe, MODEL_ID);

        HashMap<String, String> inputMapping = new HashMap<>();
        HashMap<String, String> outputMapping = new HashMap<>();
        inputMapping.put("age", "Age Mapped");
        inputMapping.put("gender", "Gender Mapped");

        NameMapping nameMapping = new NameMapping(MODEL_ID, inputMapping, outputMapping);

        given()
                .contentType(ContentType.JSON)
                .body(nameMapping)
                .when().post(metadataUrl + "/names").peek()
                .then()
                .statusCode(200)
                .body(is("Feature and output name mapping successfully applied."));

        final List<ServiceMetadata> serviceMetadata = given()
                .when().get(metadataUrl)
                .then()
                .statusCode(RestResponse.StatusCode.OK)
                .extract()
                .body().as(new TypeRef<List<ServiceMetadata>>() {
                });

        // check that mappings are applied
        for (String value : serviceMetadata.get(0).getData().getInputSchema().getNameMapping().values()) {
            assertTrue(value.contains("Mapped"));
        }

        // make sure non-mapped names don't appear
        for (String value : serviceMetadata.get(0).getData().getInputSchema().getNameMapping().keySet()) {
            assertFalse(value.contains("race"));
        }

        // make sure that overwritten field names don't appear
        Set<String> allInputColNames = serviceMetadata.get(0).getData().getInputSchema().getNameMappedItems().keySet();
        assertFalse(allInputColNames.contains("age"));
        assertTrue(allInputColNames.contains("Age Mapped"));
        assertFalse(allInputColNames.contains("gender"));
        assertTrue(allInputColNames.contains("Gender Mapped"));
        assertTrue(allInputColNames.contains("race"));

        // make sure no output mappings exist
        assertEquals(0, serviceMetadata.get(0).getData().getOutputSchema().getNameMapping().size());
    }

    @Test
    void setNameMappingWrongInputs() throws JsonProcessingException {
        final Dataframe dataframe = DataframeGenerators.generateRandomDataframe(1000, 10, false);
        saveDataframe(dataframe, MODEL_ID);

        HashMap<String, String> inputMapping = new HashMap<>();
        HashMap<String, String> outputMapping = new HashMap<>();
        inputMapping.put("age123", "Age Mapped");
        NameMapping nameMapping = new NameMapping(MODEL_ID, inputMapping, outputMapping);

        given()
                .contentType(ContentType.JSON)
                .body(nameMapping)
                .when().post(metadataUrl + "/names")
                .then()
                .statusCode(RestResponse.StatusCode.BAD_REQUEST)
                .body(Matchers.containsString("No feature found with name=age123"));
    }

    @Test
    void setNameMappingWrongOutputs() throws JsonProcessingException {
        final Dataframe dataframe = DataframeGenerators.generateRandomDataframe(1000, 10, false);
        saveDataframe(dataframe, MODEL_ID);

        HashMap<String, String> inputMapping = new HashMap<>();
        HashMap<String, String> outputMapping = new HashMap<>();
        outputMapping.put("age123", "Age Mapped");
        NameMapping nameMapping = new NameMapping(MODEL_ID, inputMapping, outputMapping);

        given()
                .contentType(ContentType.JSON)
                .body(nameMapping)
                .when().post(metadataUrl + "/names")
                .then()
                .statusCode(RestResponse.StatusCode.BAD_REQUEST)
                .body(Matchers.containsString("No output found with name=age123"));
    }

    @Test
    @DisplayName("Test individual metric request with different counts")
    void testIndividualMetricRequestCountsDifferent() {
        final int modelANobs = 2000;
        final Dataframe dataframeA = DataframeGenerators.generateRandomDataframe(modelANobs);
        final String MODEL_A = "example-model-a";
        saveDataframe(dataframeA, MODEL_A);

        final int modelBNobs = 3000;
        final Dataframe dataframeB = DataframeGenerators.generateRandomDataframe(modelBNobs);
        final String MODEL_B = "example-model-b";
        saveDataframe(dataframeB, MODEL_B);

        final int nRequestsModelA = 3;
        final int nRequestsModelB = 2;

        // Register a different number of metric requests for each model
        IntStream.range(0, nRequestsModelA).forEach(i -> {
            GroupMetricRequest request = new GroupMetricRequest();
            request.setProtectedAttribute("gender");
            request.setFavorableOutcome(new ReconcilableOutput(IntNode.valueOf(1)));
            request.setOutcomeName("income");
            request.setPrivilegedAttribute(new ReconcilableFeature(IntNode.valueOf(1)));
            request.setUnprivilegedAttribute(new ReconcilableFeature(IntNode.valueOf(0)));
            request.setModelId(MODEL_A);
            given()
                    .contentType(ContentType.JSON)
                    .body(request)
                    .when()
                    .post("/metrics/group/fairness/spd/request");
        });
        IntStream.range(0, nRequestsModelB).forEach(i -> {
            GroupMetricRequest request = new GroupMetricRequest();
            request.setProtectedAttribute("gender");
            request.setFavorableOutcome(new ReconcilableOutput(IntNode.valueOf(1)));
            request.setOutcomeName("income");
            request.setPrivilegedAttribute(new ReconcilableFeature(IntNode.valueOf(1)));
            request.setUnprivilegedAttribute(new ReconcilableFeature(IntNode.valueOf(0)));
            request.setModelId(MODEL_B);
            given()
                    .contentType(ContentType.JSON)
                    .body(request)
                    .when()
                    .post("/metrics/group/fairness/spd/request");
        });

        final List<ServiceMetadata> serviceMetadata = given()
                .when().get(metadataUrl)
                .then()
                .statusCode(200)
                .extract()
                .body().as(new TypeRef<List<ServiceMetadata>>() {
                });

        final String info = given()
                .when().get("/info")
                .then()
                .statusCode(200)
                .extract()
                .body().asString();

        assertEquals(2, serviceMetadata.size());
        // Model A
        assertEquals(0, serviceMetadata.get(0).getMetrics().scheduledMetadata.getCount("DIR"));
        assertEquals(nRequestsModelA, serviceMetadata.get(0).getMetrics().scheduledMetadata.getCount("SPD"));
        assertEquals(modelANobs, serviceMetadata.get(0).getData().getObservations());
        assertEquals(100, serviceMetadata.get(0).getData().getInputSchema().getItems().get("age").getColumnValues().size());
        assertFalse(serviceMetadata.get(0).getData().getOutputSchema().getItems().isEmpty());
        assertFalse(serviceMetadata.get(0).getData().getInputSchema().getItems().isEmpty());
        // Model B
        assertEquals(0, serviceMetadata.get(1).getMetrics().scheduledMetadata.getCount("DIR"));
        assertEquals(nRequestsModelB, serviceMetadata.get(1).getMetrics().scheduledMetadata.getCount("SPD"));
        assertEquals(modelBNobs, serviceMetadata.get(1).getData().getObservations());
        assertEquals(100, serviceMetadata.get(1).getData().getInputSchema().getItems().get("age").getColumnValues().size());
        assertFalse(serviceMetadata.get(1).getData().getOutputSchema().getItems().isEmpty());
        assertFalse(serviceMetadata.get(1).getData().getInputSchema().getItems().isEmpty());

    }

    @Test
    @DisplayName("Test individual metric request with same counts")
    void testIndividualMetricRequestCountsSame() {
        final int modelANobs = 2000;
        final Dataframe dataframeA = DataframeGenerators.generateRandomDataframe(modelANobs);
        final String MODEL_A = "example-model-a";
        saveDataframe(dataframeA, MODEL_A);

        final int modelBNobs = 3000;
        final Dataframe dataframeB = DataframeGenerators.generateRandomDataframe(modelBNobs);
        final String MODEL_B = "example-model-b";
        saveDataframe(dataframeB, MODEL_B);

        final int nRequestsModelA = 7;
        final int nRequestsModelB = 7;

        // Register a different number of metric requests for each model
        IntStream.range(0, nRequestsModelA).forEach(i -> {
            GroupMetricRequest request = new GroupMetricRequest();
            request.setProtectedAttribute("gender");
            request.setFavorableOutcome(new ReconcilableOutput(IntNode.valueOf(1)));
            request.setOutcomeName("income");
            request.setPrivilegedAttribute(new ReconcilableFeature(IntNode.valueOf(1)));
            request.setUnprivilegedAttribute(new ReconcilableFeature(IntNode.valueOf(0)));
            request.setModelId(MODEL_A);
            given()
                    .contentType(ContentType.JSON)
                    .body(request)
                    .when()
                    .post("/metrics/group/fairness/spd/request");
        });
        IntStream.range(0, nRequestsModelB).forEach(i -> {
            GroupMetricRequest request = new GroupMetricRequest();
            request.setProtectedAttribute("gender");
            request.setFavorableOutcome(new ReconcilableOutput(IntNode.valueOf(1)));
            request.setOutcomeName("income");
            request.setPrivilegedAttribute(new ReconcilableFeature(IntNode.valueOf(1)));
            request.setUnprivilegedAttribute(new ReconcilableFeature(IntNode.valueOf(0)));
            request.setModelId(MODEL_B);
            given()
                    .contentType(ContentType.JSON)
                    .body(request)
                    .when()
                    .post("/metrics/group/fairness/spd/request");
        });

        final List<ServiceMetadata> serviceMetadata = given()
                .when().get(metadataUrl)
                .then()
                .statusCode(200)
                .extract()
                .body().as(new TypeRef<List<ServiceMetadata>>() {
                });

        final String info = given()
                .when().get("/info")
                .then()
                .statusCode(200)
                .extract()
                .body().asString();

        assertEquals(2, serviceMetadata.size());
        // Model A
        assertEquals(0, serviceMetadata.get(0).getMetrics().scheduledMetadata.getCount("DIR"));
        assertEquals(nRequestsModelA, serviceMetadata.get(0).getMetrics().scheduledMetadata.getCount("SPD"));
        assertEquals(modelANobs, serviceMetadata.get(0).getData().getObservations());
        assertEquals(100, serviceMetadata.get(0).getData().getInputSchema().getItems().get("age").getColumnValues().size());
        assertFalse(serviceMetadata.get(0).getData().getOutputSchema().getItems().isEmpty());
        assertFalse(serviceMetadata.get(0).getData().getInputSchema().getItems().isEmpty());
        // Model B
        assertEquals(0, serviceMetadata.get(1).getMetrics().scheduledMetadata.getCount("DIR"));
        assertEquals(nRequestsModelB, serviceMetadata.get(1).getMetrics().scheduledMetadata.getCount("SPD"));
        assertEquals(modelBNobs, serviceMetadata.get(1).getData().getObservations());
        assertEquals(100, serviceMetadata.get(1).getData().getInputSchema().getItems().get("age").getColumnValues().size());
        assertFalse(serviceMetadata.get(1).getData().getOutputSchema().getItems().isEmpty());
        assertFalse(serviceMetadata.get(1).getData().getInputSchema().getItems().isEmpty());

    }

    @Test
    @DisplayName("Test individual metric request with no requests")
    void testIndividualMetricRequestCountsNone() {
        final int modelANobs = 2000;
        final Dataframe dataframeA = DataframeGenerators.generateRandomDataframe(modelANobs);
        final String MODEL_A = "example-model-a";
        saveDataframe(dataframeA, MODEL_A);

        final int modelBNobs = 3000;
        final Dataframe dataframeB = DataframeGenerators.generateRandomDataframe(modelBNobs);
        final String MODEL_B = "example-model-b";
        saveDataframe(dataframeB, MODEL_B);

        final List<ServiceMetadata> serviceMetadata = given()
                .when().get(metadataUrl)
                .then()
                .statusCode(200)
                .extract()
                .body().as(new TypeRef<List<ServiceMetadata>>() {
                });

        final String info = given()
                .when().get("/info")
                .then()
                .statusCode(200)
                .extract()
                .body().asString();

        assertEquals(2, serviceMetadata.size());
        // Model A
        assertEquals(0, serviceMetadata.get(0).getMetrics().scheduledMetadata.getCount("DIR"));
        assertEquals(0, serviceMetadata.get(0).getMetrics().scheduledMetadata.getCount("SPD"));
        assertEquals(modelANobs, serviceMetadata.get(0).getData().getObservations());
        assertEquals(100, serviceMetadata.get(0).getData().getInputSchema().getItems().get("age").getColumnValues().size());
        assertFalse(serviceMetadata.get(0).getData().getOutputSchema().getItems().isEmpty());
        assertFalse(serviceMetadata.get(0).getData().getInputSchema().getItems().isEmpty());
        // Model B
        assertEquals(0, serviceMetadata.get(1).getMetrics().scheduledMetadata.getCount("DIR"));
        assertEquals(0, serviceMetadata.get(1).getMetrics().scheduledMetadata.getCount("SPD"));
        assertEquals(modelBNobs, serviceMetadata.get(1).getData().getObservations());
        assertEquals(100, serviceMetadata.get(1).getData().getInputSchema().getItems().get("age").getColumnValues().size());
        assertFalse(serviceMetadata.get(1).getData().getOutputSchema().getItems().isEmpty());
        assertFalse(serviceMetadata.get(1).getData().getInputSchema().getItems().isEmpty());

    }

    @Test
    void setDatapointTagging() throws JsonProcessingException {
        final Dataframe dataframe = DataframeGenerators.generateRandomDataframe(50, 10, false);
        saveDataframe(dataframe, MODEL_ID);
        List<String> originalTags = datasource.get().getDataframe(MODEL_ID).getTags();

        List<String> tags = List.of("TRAINING", "SYNTHETIC");
        int idx = 0;
        HashMap<String, List<Integer>> tagIDXGroundTruth = new HashMap<>();
        HashMap<String, List<List<Integer>>> tagMapToPost = new HashMap<>();

        for (String tag : tags) {
            tagMapToPost.put(tag, List.of(List.of(idx, idx + 3), List.of(idx + 5, idx + 7), List.of(idx + 9)));
            tagIDXGroundTruth.put(tag, List.of(idx, idx + 1, idx + 2, idx + 5, idx + 6, idx + 9));
            idx += 10;
        }
        DataTagging dataTagging = new DataTagging(MODEL_ID, tagMapToPost);

        given()
                .contentType(ContentType.JSON)
                .body(dataTagging)
                .when().post(metadataUrl + "/tags")
                .then()
                .statusCode(200)
                .body(is("Datapoints successfully tagged."));

        Dataframe df = datasource.get().getDataframe(MODEL_ID);

        // check that df is not appended
        assertEquals(50, df.getRowDimension());

        for (String tag : tags) {
            Dataframe subDF = df.filterRowsByTagEquals(tag);

            // make sure the correct number of points are filtered
            assertEquals(6, subDF.getRowDimension());

            // check that each datapoint we tagged has the correct tag
            for (Integer i : tagIDXGroundTruth.get(tag)) {
                assertTrue(subDF.getIds().contains(df.getIds().get(i)));
            }
        }
    }

    @Test
    void getDatapointTagging() throws JsonProcessingException {
        int nModels = 3;
        final List<Dataframe> dataframes = IntStream.range(0, nModels).mapToObj(
                i -> DataframeGenerators.generateRandomDataframe(500, 10, false)).collect(Collectors.toList());

        List<String> tags = List.of("A", "B", "C", "D");

        // for each model m, assign n A-tags, 2n B-tags, 3n C-tags, etc, where n=m+10
        for (int modelIdx = 0; modelIdx < nModels; modelIdx++) {
            String modelId = MODEL_ID + "_" + modelIdx;
            saveDataframe(dataframes.get(modelIdx), modelId);
            int idx = 0;
            int spacing = modelIdx + 10;

            HashMap<String, List<List<Integer>>> tagMapToPost = new HashMap<>();

            for (int tagIdx = 0; tagIdx < tags.size(); tagIdx++) {
                int endIdx = idx + (spacing * (tagIdx + 1));
                tagMapToPost.put(tags.get(tagIdx), List.of(List.of(idx, endIdx)));
                idx = endIdx + spacing;
            }

            DataTagging dataTagging = new DataTagging(modelId, tagMapToPost);

            given()
                    .contentType(ContentType.JSON)
                    .body(dataTagging)
                    .when().post(metadataUrl + "/tags")
                    .then()
                    .statusCode(200)
                    .body(is("Datapoints successfully tagged."));
        }

        Map<String, Map<String, Long>> tagCounts = given()
                .get(metadataUrl + "/tags")
                .then()
                .statusCode(200)
                .extract()
                .body().as(new TypeRef<Map<String, Map<String, Long>>>() {
                });

        for (int modelIdx = 0; modelIdx < nModels; modelIdx++) {
            String modelId = MODEL_ID + "_" + modelIdx;
            for (int tagIdx = 0; tagIdx < tags.size(); tagIdx++) {
                assertEquals((10 + modelIdx) * (tagIdx + 1), tagCounts.get(modelId).get(tags.get(tagIdx)));
            }
        }
    }

    @Test
    void getDatapointTaggingMissingModel() throws JsonProcessingException {
        int nModels = 3;
        final List<Dataframe> dataframes = IntStream.range(0, nModels).mapToObj(
                i -> DataframeGenerators.generateRandomDataframe(500, 10, false)).collect(Collectors.toList());

        List<String> tags = List.of("A", "B", "C", "D");

        // for each model m, assign n A-tags, 2n B-tags, 3n C-tags, etc, where n=m+10
        for (int modelIdx = 0; modelIdx < nModels; modelIdx++) {
            String modelId = MODEL_ID + "_" + modelIdx;
            saveDataframe(dataframes.get(modelIdx), modelId);
            int idx = 0;
            int spacing = modelIdx + 10;

            HashMap<String, List<List<Integer>>> tagMapToPost = new HashMap<>();

            for (int tagIdx = 0; tagIdx < tags.size(); tagIdx++) {
                int endIdx = idx + (spacing * (tagIdx + 1));
                tagMapToPost.put(tags.get(tagIdx), List.of(List.of(idx, endIdx)));
                idx = endIdx + spacing;
            }

            DataTagging dataTagging = new DataTagging(modelId, tagMapToPost);

            given()
                    .contentType(ContentType.JSON)
                    .body(dataTagging)
                    .when().post(metadataUrl + "/tags")
                    .then()
                    .statusCode(200)
                    .body(is("Datapoints successfully tagged."));
        }

        // contaminate known models
        datasource.get().addModelToKnown("nonexistant");

        given()
                .get(metadataUrl + "/tags")
                .then()
                .statusCode(200);
    }

}
