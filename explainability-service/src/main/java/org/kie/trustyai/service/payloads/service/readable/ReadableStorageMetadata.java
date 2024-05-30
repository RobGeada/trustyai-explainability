package org.kie.trustyai.service.payloads.service.readable;

import org.kie.trustyai.service.data.metadata.StorageMetadata;

public class ReadableStorageMetadata {
    private ReadableSchema inputSchema;
    private ReadableSchema outputSchema;
    private String inputTensorName;
    private String outputTensorName;
    private int observations;
    private String modelId;

    public ReadableStorageMetadata() {
    }

    public ReadableStorageMetadata(StorageMetadata sm) {
        inputSchema = new ReadableSchema(sm.getInputSchema());
        outputSchema = new ReadableSchema(sm.getOutputSchema());
        inputTensorName = sm.getInputTensorName();
        outputTensorName = sm.getOutputTensorName();
        observations = sm.getObservations();
        modelId = sm.getModelId();
    }

    public ReadableSchema getInputSchema() {
        return inputSchema;
    }

    public ReadableSchema getOutputSchema() {
        return outputSchema;
    }

    public String getInputTensorName() {
        return inputTensorName;
    }

    public String getOutputTensorName() {
        return outputTensorName;
    }

    public int getObservations() {
        return observations;
    }

    public String getModelId() {
        return modelId;
    }

    public void setInputSchema(ReadableSchema inputSchema) {
        this.inputSchema = inputSchema;
    }

    public void setOutputSchema(ReadableSchema outputSchema) {
        this.outputSchema = outputSchema;
    }

    public void setInputTensorName(String inputTensorName) {
        this.inputTensorName = inputTensorName;
    }

    public void setOutputTensorName(String outputTensorName) {
        this.outputTensorName = outputTensorName;
    }

    public void setObservations(int observations) {
        this.observations = observations;
    }

    public void setModelId(String modelId) {
        this.modelId = modelId;
    }
}
