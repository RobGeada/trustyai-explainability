package org.kie.trustyai.connectors.kserve.v2;

import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.kie.trustyai.connectors.kserve.v2.grpc.InferTensorContents;
import org.kie.trustyai.connectors.kserve.v2.grpc.ModelInferRequest;
import org.kie.trustyai.connectors.kserve.v2.grpc.ModelInferResponse;
import org.kie.trustyai.explainability.model.*;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TensorConverterTest {
    @Test
    void modelInferResponseToPredictionOutputSingle() {

        final Random random = new Random();
        final double value = random.nextDouble();

        InferTensorContents.Builder contents = InferTensorContents.newBuilder()
                .addFp64Contents(value);

        ModelInferResponse.InferOutputTensor outputTensor = ModelInferResponse.InferOutputTensor.newBuilder()
                .setDatatype("FP64")
                .addShape(1).addShape(1).setContents(contents).build();

        final ModelInferResponse response = ModelInferResponse.newBuilder().addOutputs(outputTensor).build();
        PredictionOutput predictionOutput = TensorConverter.parseKserveModelInferResponse(response).get(0);

        assertEquals(1, predictionOutput.getOutputs().size());
        assertEquals(value, predictionOutput.getOutputs().get(0).getValue().asNumber());
    }

    @Test
    void modelInferResponseToPredictionOutputMulti() {

        final Random random = new Random();
        final List<Double> values = random.doubles(3).boxed().collect(Collectors.toList());
        InferTensorContents.Builder contents = InferTensorContents.newBuilder()
                .addFp64Contents(values.get(0))
                .addFp64Contents(values.get(1))
                .addFp64Contents(values.get(2));

        ModelInferResponse.InferOutputTensor outputTensor = ModelInferResponse.InferOutputTensor.newBuilder()
                .setDatatype("FP64")
                .addShape(1).addShape(3).setContents(contents).build();

        final ModelInferResponse response = ModelInferResponse.newBuilder().addOutputs(outputTensor).build();
        final List<Output> predictionOutput = TensorConverter.parseKserveModelInferResponse(response).get(0).getOutputs();

        assertEquals(3, predictionOutput.size());
        for (int i = 0; i < 3; i++) {
            assertEquals(values.get(i), predictionOutput.get(i).getValue().asNumber());
        }
    }

    @Test
    void modelInferResponseToPredictionOutputFp32() {

        final Random random = new Random();
        final List<Float> values = List.of(random.nextFloat(), random.nextFloat(), random.nextFloat());
        InferTensorContents.Builder contents = InferTensorContents.newBuilder()
                .addFp32Contents(values.get(0))
                .addFp32Contents(values.get(1))
                .addFp32Contents(values.get(2));

        ModelInferResponse.InferOutputTensor outputTensor = ModelInferResponse.InferOutputTensor.newBuilder()
                .setDatatype("FP32")
                .addShape(1).addShape(3).setContents(contents).build();
        final ModelInferResponse response = ModelInferResponse.newBuilder().addOutputs(outputTensor).build();
        final List<Output> predictionOutput = TensorConverter.parseKserveModelInferResponse(response).get(0).getOutputs();

        assertEquals(3, predictionOutput.size());
        for (int i = 0; i < 3; i++) {
            assertEquals(values.get(i), Double.valueOf(predictionOutput.get(i).getValue().asNumber()).floatValue());
        }
    }

    @Test
    void modelInferResponseToPredictionOutputFp64() {

        final Random random = new Random();
        final List<Double> values = List.of(random.nextDouble(), random.nextDouble(), random.nextDouble());
        InferTensorContents.Builder contents = InferTensorContents.newBuilder()
                .addFp64Contents(values.get(0))
                .addFp64Contents(values.get(1))
                .addFp64Contents(values.get(2));

        ModelInferResponse.InferOutputTensor outputTensor = ModelInferResponse.InferOutputTensor.newBuilder()
                .setDatatype("FP64")
                .addShape(1).addShape(3).setContents(contents).build();

        final ModelInferResponse response = ModelInferResponse.newBuilder().addOutputs(outputTensor).build();

        final List<Output> predictionOutput = TensorConverter.parseKserveModelInferResponse(response).get(0).getOutputs();

        assertEquals(3, predictionOutput.size());
        for (int i = 0; i < 3; i++) {
            assertEquals(values.get(i), predictionOutput.get(i).getValue().asNumber());
        }
    }

    @Test
    void modelInferResponseToPredictionInputFp32() {

        final Random random = new Random();
        final List<Float> values = List.of(random.nextFloat(), random.nextFloat(), random.nextFloat());
        InferTensorContents.Builder contents = InferTensorContents.newBuilder()
                .addFp32Contents(values.get(0))
                .addFp32Contents(values.get(1))
                .addFp32Contents(values.get(2));

        ModelInferRequest.InferInputTensor tensor = ModelInferRequest.InferInputTensor.newBuilder()
                .setDatatype("FP32")
                .addShape(1).addShape(3).setContents(contents).build();

        final ModelInferRequest request = ModelInferRequest.newBuilder().addInputs(tensor).build();
        final List<Feature> predictionInput = TensorConverter.parseKserveModelInferRequest(request).get(0).getFeatures();

        assertEquals(3, predictionInput.size());
        for (int i = 0; i < 3; i++) {
            assertEquals(values.get(i), Double.valueOf(predictionInput.get(i).getValue().asNumber()).floatValue());
        }
    }

    @Test
    void modelInferResponseToPredictionInputFp64() {

        final Random random = new Random();
        final List<Double> values = List.of(random.nextDouble(), random.nextDouble(), random.nextDouble());
        InferTensorContents.Builder contents = InferTensorContents.newBuilder()
                .addFp64Contents(values.get(0))
                .addFp64Contents(values.get(1))
                .addFp64Contents(values.get(2));

        ModelInferRequest.InferInputTensor tensor = ModelInferRequest.InferInputTensor.newBuilder()
                .setDatatype("FP64")
                .addShape(1).addShape(3).setContents(contents).build();

        final ModelInferRequest request = ModelInferRequest.newBuilder().addInputs(tensor).build();
        final List<Feature> predictionInput = TensorConverter.parseKserveModelInferRequest(request).get(0).getFeatures();

        assertEquals(3, predictionInput.size());
        for (int i = 0; i < 3; i++) {
            assertEquals(values.get(i), predictionInput.get(i).getValue().asNumber());
        }
    }

    @Test
    void singlePredictionInputToModelInferRequestArrayCodec() {
        final Prediction prediction = PayloadUtils.createDummy1PredictionAllNumeric();
        final TensorDataframe tdf = TensorDataframe.createFromInputs(List.of(prediction.getInput()));

        final ModelInferRequest.InferInputTensor.Builder tensor = tdf.rowAsSingleArrayInputTensor(0, "predict");
        final ModelInferRequest request = ModelInferRequest.newBuilder().addInputs(tensor).build();
        final List<Feature> features = TensorConverter.parseKserveModelInferRequest(request).get(0).getFeatures();
        assertEquals(prediction.getInput().getFeatures().size(), features.size());
    }

    @Test
    void singlePredictionInputToModelInferRequestDataframeCodec() {
        final Prediction prediction = PayloadUtils.createDummy1PredictionMixedTypes();
        final TensorDataframe tdf = TensorDataframe.createFromInputs(List.of(prediction.getInput()));

        final List<ModelInferRequest.InferInputTensor> tensors = tdf.rowAsSingleDataframeInputTensor(0).stream().map(ModelInferRequest.InferInputTensor.Builder::build).collect(Collectors.toList());
        final ModelInferRequest request = ModelInferRequest.newBuilder().addAllInputs(tensors).build();
        final List<Feature> features = TensorConverter.parseKserveModelInferRequest(request).get(0).getFeatures();
        assertEquals(prediction.getInput().getFeatures().size(), features.size());
    }
}