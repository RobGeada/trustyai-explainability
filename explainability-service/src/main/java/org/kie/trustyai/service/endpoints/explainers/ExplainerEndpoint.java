package org.kie.trustyai.service.endpoints.explainers;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.kie.trustyai.connectors.kserve.v2.KServeConfig;
import org.kie.trustyai.connectors.kserve.v2.KServeV2GRPCPredictionProvider;
import org.kie.trustyai.explainability.model.PredictionProvider;
import org.kie.trustyai.explainability.model.dataframe.DataframeMetadata;
import org.kie.trustyai.service.payloads.explainers.ModelConfig;

public abstract class ExplainerEndpoint {

    public static final String BIAS_IGNORE_PARAM = "bias-ignore";

    protected PredictionProvider getModel(ModelConfig modelConfig) {
        Map<String, String> map = new HashMap<>();
        map.put(BIAS_IGNORE_PARAM, "true");
        KServeConfig kServeConfig = KServeConfig.create(modelConfig.getTarget(), modelConfig.getName(), modelConfig.getVersion());
        return KServeV2GRPCPredictionProvider.forTarget(kServeConfig, DataframeMetadata.DEFAULT_INPUT_TENSOR_NAME, List.of(DataframeMetadata.DEFAULT_OUTPUT_TENSOR_NAME), map);
    }

    protected PredictionProvider getModel(ModelConfig modelConfig, String inputTensorName) {
        Map<String, String> map = new HashMap<>();
        map.put(BIAS_IGNORE_PARAM, "true");
        KServeConfig kServeConfig = KServeConfig.create(modelConfig.getTarget(), modelConfig.getName(), modelConfig.getVersion());
        return KServeV2GRPCPredictionProvider.forTarget(kServeConfig, inputTensorName, List.of(DataframeMetadata.DEFAULT_OUTPUT_TENSOR_NAME), map);
    }
}
