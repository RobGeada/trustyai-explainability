package org.kie.trustyai.explainability.model;

import java.util.List;
import java.util.stream.Collectors;

import org.kie.trustyai.connectors.kserve.v2.PayloadParser;
import org.kie.trustyai.connectors.kserve.v2.grpc.InferTensorContents;
import org.kie.trustyai.connectors.kserve.v2.grpc.ModelInferRequest;

import com.google.protobuf.ByteString;

public class TensorDataframe {

    private final Dataframe df;

    TensorDataframe(Dataframe dataframe) {
        this.df = dataframe;
    }

    public static TensorDataframe createFromInputs(List<PredictionInput> inputs) {
        return new TensorDataframe(Dataframe.createFromInputs(inputs));
    }

    public static void addValue(InferTensorContents.Builder content, Value value, Type type) {
        final Object object = value.getUnderlyingObject();

        switch (type) {
            case NUMBER:
                if (object instanceof Integer) {
                    content.addIntContents((Integer) object);
                } else if (object instanceof Double) {
                    content.addFp64Contents((Double) object);
                }
                break;
            case BOOLEAN:
                content.addBoolContents((Boolean) object);
                break;
            case CATEGORICAL:
                final byte[] bytes = ((String) object).getBytes();
                content.addBytesContents(ByteString.copyFrom(bytes));
                break;

            default:
                throw new IllegalArgumentException("Unsupported feature type: " + type);
        }
    }

    /**
     * Return a single row as single tensor.
     * Used mainly for numpy style prediction endpoints
     * 
     * @param row
     * @param name
     * @return
     */
    public ModelInferRequest.InferInputTensor.Builder rowAsSingleArrayInputTensor(int row, String name) {
        final ModelInferRequest.InferInputTensor.Builder inferInputTensorBuilder = ModelInferRequest.InferInputTensor.newBuilder();

        final Type trustyType = this.df.getType(0);
        final String kserveType = String.valueOf(PayloadParser.trustyToKserveType(trustyType, this.df.getValue(row, 0).getUnderlyingObject()));
        inferInputTensorBuilder.setDatatype(kserveType);

        final InferTensorContents.Builder contents = InferTensorContents.newBuilder();
        this.df.getRow(row).forEach(value -> addValue(contents, value, trustyType));

        inferInputTensorBuilder.addShape(1);
        inferInputTensorBuilder.addShape(this.df.getInputsCount());
        inferInputTensorBuilder.setContents(contents);
        inferInputTensorBuilder.setNameBytes(ByteString.copyFromUtf8(name));
        inferInputTensorBuilder.setDatatypeBytes(ByteString.copyFromUtf8(kserveType));
        return inferInputTensorBuilder;
    }

    public List<ModelInferRequest.InferInputTensor.Builder> rowAsSingleDataframeInputTensor(int row) {

        return this.df.getInputsIndices().stream().map(column -> {
            final InferTensorContents.Builder contents = InferTensorContents.newBuilder();
            final Value value = this.df.getValue(row, column);
            final Type type = this.df.getType(column);
            final String featureName = this.df.getColumnNames().get(column);
            addValue(contents, value, type);

            final ModelInferRequest.InferInputTensor.Builder tensor = ModelInferRequest.InferInputTensor.newBuilder();
            final String kserveType = String.valueOf(PayloadParser.trustyToKserveType(type, value));
            tensor.setDatatypeBytes(ByteString.copyFromUtf8(kserveType));
            tensor.setNameBytes(ByteString.copyFromUtf8(featureName));
            tensor.addShape(1);
            tensor.setContents(contents);
            return tensor;
        }).collect(Collectors.toUnmodifiableList());
    }

    public List<ModelInferRequest.InferInputTensor.Builder> asBatchDataframeInputTensor() {

        return this.df.getInputsIndices().stream().map(column -> {
            final InferTensorContents.Builder contents = InferTensorContents.newBuilder();
            final Type type = this.df.getType(column);
            final String featureName = this.df.getColumnNames().get(column);
            this.df.getColumn(column).forEach(value -> addValue(contents, value, type));

            final ModelInferRequest.InferInputTensor.Builder tensor = ModelInferRequest.InferInputTensor.newBuilder();
            final String kserveType = String.valueOf(PayloadParser.trustyToKserveType(type, this.df.getValue(0, column)));
            tensor.setDatatypeBytes(ByteString.copyFromUtf8(kserveType));
            tensor.setNameBytes(ByteString.copyFromUtf8(featureName));
            tensor.addShape(1);
            tensor.addShape(this.df.getRowDimension());
            tensor.setContents(contents);
            return tensor;
        }).collect(Collectors.toUnmodifiableList());
    }
}