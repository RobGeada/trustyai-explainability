package org.kie.trustyai.connectors.kserve.v2;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import com.google.protobuf.ByteString;
import org.kie.trustyai.connectors.kserve.v2.grpc.InferTensorContents;
import org.kie.trustyai.connectors.kserve.v2.grpc.ModelInferRequest;
import org.kie.trustyai.connectors.kserve.v2.grpc.ModelInferResponse;
import org.kie.trustyai.explainability.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.processing.SupportedSourceVersion;

import static org.kie.trustyai.explainability.model.Type.*;

public class TensorConverter {

    private static final Logger logger = LoggerFactory.getLogger(TensorConverter.class);

    public static KServeDatatype trustyToKserveType(Type type, Value value) throws IllegalArgumentException {
        final Object object = value.getUnderlyingObject();
        if (type == NUMBER) {
            if (object instanceof Integer) {
                return KServeDatatype.INT32;
            } else if (object instanceof Double) {
                return KServeDatatype.FP64;
            } else if (object instanceof Long) {
                return KServeDatatype.INT64;
            } else {
                throw new IllegalArgumentException("Unsupported object type: " + object.getClass().getName());
            }
        } else if (type == BOOLEAN) {
            return KServeDatatype.BOOL;
        } else if (type == CATEGORICAL) {
            return KServeDatatype.BYTES;
        } else {
            throw new IllegalArgumentException("Unsupported TrustyAI type: " + type);
        }
    }

    static Feature contentsToFeature(InferTensorContents tensorContents, String kserveDatatype, String name, int index) {
        final KServeDatatype type;
        try {
            type = KServeDatatype.valueOf(kserveDatatype);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Currently unsupported type for Tensor input, type=" + kserveDatatype);
        }


        switch (type) {
            case BOOL:
                return FeatureFactory.newBooleanFeature(name, tensorContents.getBoolContents(index));
            case INT8:
            case INT16:
            case INT32:
                return FeatureFactory.newNumericalFeature(name, tensorContents.getIntContents(index));
            case INT64:
                return FeatureFactory.newNumericalFeature(name, tensorContents.getInt64Contents(index));
            case FP32:
                return FeatureFactory.newNumericalFeature(name, tensorContents.getFp32Contents(index));
            case FP64:
                return FeatureFactory.newNumericalFeature(name, tensorContents.getFp64Contents(index));
            case BYTES:
                return FeatureFactory.newCategoricalFeature(name, String.valueOf(tensorContents.getBytesContents(index)));
            default:
                throw new IllegalArgumentException("Currently unsupported type for Tensor input, type=" + kserveDatatype);
        }
    }

    static Output contentsToOutput(InferTensorContents tensorContents, String kserveDatatype, String name, int index) {
        final KServeDatatype type;

        try {
            type = KServeDatatype.valueOf(kserveDatatype);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Currently unsupported type for Tensor input, type=" + kserveDatatype);
        }
        switch (type) {
            case BOOL:
                return new Output(name, Type.BOOLEAN, new Value(tensorContents.getBoolContents(index)), 1.0);
            case INT8:
            case INT16:
            case INT32:
                return new Output(name, Type.NUMBER, new Value(tensorContents.getIntContents(index)), 1.0);
            case INT64:
                return new Output(name, Type.NUMBER, new Value(tensorContents.getInt64Contents(index)), 1.0);
            case FP32:
                return new Output(name, Type.NUMBER, new Value(tensorContents.getFp32Contents(index)), 1.0);
            case FP64:
                return new Output(name, Type.NUMBER, new Value(tensorContents.getFp64Contents(index)), 1.0);
            case BYTES:
                return new Output(name, Type.CATEGORICAL, new Value(String.valueOf(tensorContents.getBytesContents(index))), 1.0);
            default:
                throw new IllegalArgumentException("Currently unsupported type for Tensor input, type=" + kserveDatatype);
        }
    }

    public static List<PredictionInput> parseKserveModelInferRequest(ModelInferRequest data) {
        return parseKserveModelInferRequest(data, Optional.empty(), false);
    }

    public static List<PredictionInput> parseKserveModelInferRequest(ModelInferRequest data, Optional<List<String>> featureNames) {
        return parseKserveModelInferRequest(data, featureNames, false);
    }

    public static List<PredictionInput> parseKserveModelInferRequest(ModelInferRequest data, Optional<List<String>> featureNames, boolean isBatch) {
        final int count = data.getInputsCount();
        System.out.println("== REQUEST ==");

        if (count == 1) { // The NP codec case
            final ModelInferRequest.InferInputTensor tensor = data.getInputs(0);
            ByteString rawContents = null;
            if (data.getRawInputContentsCount() > 0) {
                rawContents = data.getRawInputContents(0);
            }
            final List<Long> shape = tensor.getShapeList();
            final int firstShape = shape.get(0).intValue();
            System.out.println("raw contents count=1:"+ rawContents);
            if (firstShape < 2) {
                if (shape.size() >= 2) {
                    int secondShape = 1;
                    for (int i = 1; i < shape.size(); i++) {
                        secondShape *= shape.get(i).intValue();
                    }
                    // NP features, no batch

                    final InferTensorContents contents = tensor.getContents();

                    if (isBatch) {
                        if (data.getRawInputContentsCount() > 0) {
                            List<String> names = List.of(tensor.getName());
                            System.out.println("batch");
                            return new ArrayList<>(List.of(PayloadParser.rawContentToPredictionInput(data, names)));
                        }
                        logger.debug("Using NP codec (batch)");
                        return IntStream.range(0, secondShape)
                                .mapToObj(i -> new PredictionInput(new ArrayList<>(List.of(contentsToFeature(contents, tensor.getDatatype(), tensor.getName(), i)))))
                                .collect(Collectors.toCollection(ArrayList::new));
                    } else {
                        if (featureNames.isPresent()) {
                            if (data.getRawInputContentsCount() > 0) {
                                System.out.println("no-batch+feature");
                                PredictionInput pi = PayloadParser.rawContentToPredictionInput(data, featureNames.get());
                                System.out.println("returning");
                                return new ArrayList<>(List.of(pi));
                            }
                            final List<Feature> features = IntStream.range(0, secondShape)
                                    .mapToObj(i -> contentsToFeature(contents, tensor.getDatatype(), featureNames.get().get(i), i))
                                    .collect(Collectors.toCollection(ArrayList::new));
                            logger.debug("Using NP codec (no batch, provided names)");
                            return new ArrayList<>(List.of(new PredictionInput(features)));
                        } else {
                            if (data.getRawInputContentsCount() > 0) {
                                System.out.println("no-batch+feature");
                                List<String> names =  IntStream.range(0, secondShape).mapToObj(i -> tensor.getName() + "-" + i).collect(Collectors.toCollection(ArrayList::new));
                                return new ArrayList<>(List.of(PayloadParser.rawContentToPredictionInput(data, names)));
                            }
                            final List<Feature> features = IntStream.range(0, secondShape)
                                    .mapToObj(i -> contentsToFeature(contents, tensor.getDatatype(), tensor.getName() + "-" + i, i))
                                    .collect(Collectors.toCollection(ArrayList::new));
                            logger.debug("Using NP codec (no batch)");
                            return new ArrayList<>(List.of(new PredictionInput(features)));
                        }
                    }
                } else if (shape.size() == 1) {
                    // A single element feature, no batch. PD or NP irrelevant
                    final InferTensorContents contents = tensor.getContents();
                    final Feature feature = contentsToFeature(contents, tensor.getDatatype(), tensor.getName(), 0);
                    logger.debug("Using NP codec (single input, no batch");
                    return new ArrayList<>(List.of(new PredictionInput(new ArrayList<>(List.of(feature)))));
                } else {
                    System.out.println("Input shape size:"+ shape);
                    throw new IllegalArgumentException("Shape size not supported for tabular data");
                }
            } else {
                // NP-batch
                final int secondShape = shape.get(1).intValue();
                final InferTensorContents contents = tensor.getContents();
                final List<PredictionInput> predictionInputs = new ArrayList<>();
                for (int batch = 0; batch < firstShape; batch++) {
                    final List<Feature> features = new ArrayList<>();
                    for (int featureIndex = 0; featureIndex < secondShape; featureIndex++) {
                        if (featureNames.isPresent()) {
                            features.add(contentsToFeature(contents, tensor.getDatatype(), featureNames.get().get(featureIndex), secondShape * batch + featureIndex));
                        } else {
                            features.add(contentsToFeature(contents, tensor.getDatatype(), tensor.getName() + "-" + featureIndex, secondShape * batch + featureIndex));
                        }
                    }
                    predictionInputs.add(new PredictionInput(features));
                }
                logger.debug("Using NP codec (batch)");
                return predictionInputs;
            }

        } else if (count > 1) { // The PD codec case
            final List<Long> shape = data.getInputs(0).getShapeList();
            System.out.println("raw contents count > 1:" + data.getRawInputContentsList());
            if (shape.size() < 2) {
                // Multi-feature PD, no batch
                logger.debug("Using PD codec (no batch)");
                final List<ModelInferRequest.InferInputTensor> tensors = data.getInputsList();
                final List<Feature> features = tensors.stream().map(tensor -> {
                    final InferTensorContents contents = tensor.getContents();
                    return contentsToFeature(contents, tensor.getDatatype(), tensor.getName(), 0);
                }).collect(Collectors.toCollection(ArrayList::new));
                return new ArrayList<>(List.of(new PredictionInput(features)));
            } else {
                // Multi-feature PD, batch
                logger.debug("Using NP codec (batch)");
                final int secondShape = shape.get(1).intValue();
                final List<ModelInferRequest.InferInputTensor> tensors = data.getInputsList();
                final List<List<Feature>> features = tensors.stream().map(tensor -> {
                    final InferTensorContents contents = tensor.getContents();
                    return IntStream.range(0, secondShape)
                            .mapToObj(i -> contentsToFeature(contents, tensor.getDatatype(), tensor.getName(), i))
                            .collect(Collectors.toCollection(ArrayList::new));
                }).collect(Collectors.toCollection(ArrayList::new));
                // Transpose the features
                final List<PredictionInput> predictionInputs = new ArrayList<>();
                for (int batch = 0; batch < secondShape; batch++) {
                    final List<Feature> batchFeatures = new ArrayList<>();
                    for (int featureIndex = 0; featureIndex < tensors.size(); featureIndex++) {
                        batchFeatures.add(features.get(featureIndex).get(batch));
                    }
                    predictionInputs.add(new PredictionInput(batchFeatures));
                }
                return predictionInputs;
            }
        } else {
            throw new IllegalArgumentException("Data inputs count not supported: " + count);
        }
    }

    public static List<PredictionOutput> parseKserveModelInferResponse(ModelInferResponse data) {
        return parseKserveModelInferResponse(data, Optional.empty(), false);
    }

    public static List<PredictionOutput> parseKserveModelInferResponse(ModelInferResponse data, Optional<List<String>> featureNames) {
        return parseKserveModelInferResponse(data, featureNames, false);
    }

    public static List<PredictionOutput> parseKserveModelInferResponse(ModelInferResponse data, Optional<List<String>> featureNames, boolean isBatch) {
        final int count = data.getOutputsCount();
        System.out.println("== RESPONSE ==");
        ByteString rawContents = null;
        if (data.getRawOutputContentsCount() > 0) {
            rawContents = data.getRawOutputContents(0);
        }

        if (count == 1) { // The NP codec case
            final ModelInferResponse.InferOutputTensor tensor = data.getOutputs(0);
            final List<Long> shape = tensor.getShapeList();
            final int firstShape = shape.get(0).intValue();

            if (firstShape < 2) {
                if (shape.size() >= 2) {
                    int secondShape = 1;
                    for (int i = 1; i < shape.size(); i++) {
                        secondShape *= shape.get(i).intValue();
                    }
                    System.out.println("fp32 contents"+tensor.getContents().getFp32ContentsList());

                    final InferTensorContents contents = tensor.getContents();
                    if (isBatch) {
                        logger.debug("Using NP codec (batch)");
                        if (data.getRawOutputContentsCount() > 0) {
                            List<String> names = List.of(tensor.getName());
                            System.out.println("batch");
                            return new ArrayList<>(List.of(PayloadParser.rawContentToPredictionOutput(data, names)));
                        }
                        return IntStream.range(0, secondShape)
                                .mapToObj(i -> new PredictionOutput(List.of(contentsToOutput(contents, tensor.getDatatype(), tensor.getName(), i))))
                                .collect(Collectors.toCollection(ArrayList::new));
                    } else {
                        if (featureNames.isPresent()) {
                            if (data.getRawOutputContentsCount() > 0) {
                                return new ArrayList<>(List.of(PayloadParser.rawContentToPredictionOutput(data, featureNames.get())));
                            }
                            final List<Output> output = IntStream.range(0, secondShape)
                                    .mapToObj(i -> contentsToOutput(contents, tensor.getDatatype(), featureNames.get().get(i), i))
                                    .collect(Collectors.toCollection(ArrayList::new));
                            logger.debug("Using NP codec (no batch, provided names)");
                            return List.of(new PredictionOutput(output));

                        } else {
                            if (data.getRawOutputContentsCount() > 0) {
                                List<String> names =  IntStream.range(0, secondShape).mapToObj(i -> tensor.getName() + "-" + i).collect(Collectors.toCollection(ArrayList::new));
                                return new ArrayList<>(List.of(PayloadParser.rawContentToPredictionOutput(data, names)));
                            }
                            final List<Output> output = IntStream.range(0, secondShape)
                                    .mapToObj(i -> contentsToOutput(contents, tensor.getDatatype(), tensor.getName() + "-" + i, i))
                                    .collect(Collectors.toCollection(ArrayList::new));
                            logger.debug("Using NP codec (no batch)");
                            return List.of(new PredictionOutput(output));
                        }
                    }
                } else if (shape.size() == 1) {
                    // A single element feature, no batch. PD or NP irrelevant
                    final InferTensorContents contents = tensor.getContents();
                    final Output output = contentsToOutput(contents, tensor.getDatatype(), tensor.getName(), 0);
                    logger.debug("Using NP codec (single input, no batch");
                    return List.of(new PredictionOutput(List.of(output)));
                } else {
                    System.out.println("Output shape size:"+ shape);
                    throw new IllegalArgumentException("Shape size not supported for tabular data");
                }
            } else {
                // NP-batch
                final int secondShape = shape.get(1).intValue();
                final InferTensorContents contents = tensor.getContents();
                final List<PredictionOutput> predictionOutputs = new ArrayList<>();
                for (int batch = 0; batch < firstShape; batch++) {
                    final List<Output> outputs = new ArrayList<>();
                    for (int featureIndex = 0; featureIndex < secondShape; featureIndex++) {
                        if (featureNames.isPresent()) {
                            outputs.add(contentsToOutput(contents, tensor.getDatatype(), featureNames.get().get(featureIndex), secondShape * batch + featureIndex));
                        } else {
                            outputs.add(contentsToOutput(contents, tensor.getDatatype(), tensor.getName() + "-" + featureIndex, secondShape * batch + featureIndex));
                        }

                    }
                    predictionOutputs.add(new PredictionOutput(outputs));
                }
                logger.debug("Using NP codec (batch)");
                return predictionOutputs;
            }

        } else if (count > 1) { // The PD codec case
            final List<Long> shape = data.getOutputs(0).getShapeList();
            if (shape.size() < 2) {
                // Multi-feature PD, no batch
                logger.debug("Using PD codec (no batch)");
                final List<ModelInferResponse.InferOutputTensor> tensors = data.getOutputsList();
                final List<Output> outputs = tensors.stream().map(tensor -> {
                    final InferTensorContents contents = tensor.getContents();
                    return contentsToOutput(contents, tensor.getDatatype(), tensor.getName(), 0);
                }).collect(Collectors.toCollection(ArrayList::new));
                return List.of(new PredictionOutput(outputs));
            } else {
                // Multi-feature PD, batch
                logger.debug("Using NP codec (batch)");
                System.out.println("np shape: "+ shape);

                final List<ModelInferResponse.InferOutputTensor> tensors = data.getOutputsList();
                List<Integer> perTensorSecondShape = new ArrayList<>();

                for (int tensorIDX=0; tensorIDX<tensors.size(); tensorIDX++) {
                    List<Long> perTensorShape = tensors.get(tensorIDX).getShapeList();
                    System.out.println("shape: " + tensorIDX+ ": "+perTensorShape);
                    perTensorSecondShape.add(1);
                    for (int i = 1; i < perTensorShape.size(); i++) {
                        perTensorSecondShape.set(tensorIDX, perTensorSecondShape.get(tensorIDX) * perTensorShape.get(i).intValue());
                    }
                }


                if (data.getRawOutputContentsCount() > 0) {
                    final List<Output> outputs = IntStream.range(0, tensors.size()).mapToObj(tensorIDX -> {
                        List<String> names = IntStream.range(0, perTensorSecondShape.get(tensorIDX)).mapToObj(i -> tensors.get(tensorIDX).getName() + "-" + i)
                                .collect(Collectors.toCollection(ArrayList::new));
                        System.out.println(tensorIDX + ": " + tensors.get(tensorIDX).getName());
                        System.out.println(names.get(0));
                        return PayloadParser.rawContentToPredictionOutput(data, names, tensorIDX).getOutputs();
                    }).flatMap(Collection::stream).collect(Collectors.toList());
                    System.out.println(outputs.get(outputs.size()-1));
                    return List.of(new PredictionOutput(outputs));
                }
                final List<List<Output>> outputs = IntStream.range(0, tensors.size()).mapToObj(tensorIDX -> {
                    final InferTensorContents contents = tensors.get(tensorIDX).getContents();
                    return IntStream.range(0, perTensorSecondShape.get(tensorIDX))
                            .mapToObj(i -> contentsToOutput(contents, tensors.get(tensorIDX).getDatatype(), tensors.get(tensorIDX).getName(), i))
                            .collect(Collectors.toCollection(ArrayList::new));
                }).collect(Collectors.toCollection(ArrayList::new));


                // Transpose the features
                final List<PredictionOutput> predictionOutputs = new ArrayList<>();
                for (int batch = 0; batch < perTensorSecondShape.get(0); batch++) {
                    final List<Output> batchOutputs = new ArrayList<>();
                    for (int outputIndex = 0; outputIndex < tensors.size(); outputIndex++) {
                        batchOutputs.add(outputs.get(outputIndex).get(batch));
                    }
                    predictionOutputs.add(new PredictionOutput(batchOutputs));
                }
                return predictionOutputs;
            }
        } else {
            throw new IllegalArgumentException("Data inputs count not supported: " + count);
        }
    }

}
