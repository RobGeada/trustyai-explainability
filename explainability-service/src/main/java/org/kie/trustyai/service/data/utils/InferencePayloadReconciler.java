package org.kie.trustyai.service.data.utils;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.jboss.logging.Logger;
import org.kie.trustyai.explainability.model.Prediction;
import org.kie.trustyai.service.data.exceptions.DataframeCreateException;
import org.kie.trustyai.service.data.exceptions.InvalidSchemaException;
import org.kie.trustyai.service.payloads.consumer.InferencePartialPayload;
import org.kie.trustyai.service.payloads.consumer.PartialPayload;
import org.kie.trustyai.service.payloads.values.DataType;

public abstract class InferencePayloadReconciler<T extends PartialPayload, U extends PartialPayload> {

    protected final Map<String, T> unreconciledInputs = new ConcurrentHashMap<>();
    protected final Map<String, U> unreconciledOutputs = new ConcurrentHashMap<>();
    private static final Logger LOG = Logger.getLogger(InferencePayloadReconciler.class);

    /**
     * Add a {@link InferencePartialPayload} input to the (yet) unreconciled mapping.
     * If there is a corresponding (based on unique id) output {@link InferencePartialPayload},
     * both are saved to storage and removed from the unreconciled mapping.
     *
     * @param input
     */
    public void addUnreconciledInput(T input) throws InvalidSchemaException, DataframeCreateException {
        LOG.info("entering input add");
        final String id = input.getId();
        unreconciledInputs.put(id, input);
        if (unreconciledOutputs.containsKey(id)) {
            save(id, input.getModelId());
        }
        LOG.info("exiting input add");
    }

    /**
     * Add a {@link InferencePartialPayload} output to the (yet) unreconciled mapping.
     * If there is a corresponding (based on unique id) input {@link InferencePartialPayload},
     * both are saved to storage and removed from the unreconciled mapping.
     * 
     * @param output
     */
    public void addUnreconciledOutput(U output) throws InvalidSchemaException, DataframeCreateException {
        LOG.info("entering output add");
        final String id = output.getId();
        unreconciledOutputs.put(id, output);
        if (unreconciledInputs.containsKey(id)) {
            save(id, output.getModelId());
        }
        LOG.info("exiting output add");
    }


    protected class OptionallyTypedPredictionList {
       List<DataType> inputTypes;
       List<DataType> outputTypes;
       List<Prediction> predictions;

        public OptionallyTypedPredictionList(List<DataType> inputTypes, List<DataType> outputTypes, List<Prediction> predictions) {
            this.inputTypes = inputTypes;
            this.outputTypes = outputTypes;
            this.predictions = predictions;
        }

        public OptionallyTypedPredictionList(List<Prediction> predictions){
            this.predictions = predictions;
            inputTypes = null;
            outputTypes = null;
        }
    }

    abstract protected void save(String id, String modelId) throws InvalidSchemaException, DataframeCreateException;

    public abstract OptionallyTypedPredictionList payloadToPrediction(T inputPayload, U outputPayload, String id, Map<String, String> metadata) throws DataframeCreateException;
}
