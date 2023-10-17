package org.kie.trustyai.service.data.utils;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.jboss.logging.Logger;
import org.kie.trustyai.explainability.model.Dataframe;
import org.kie.trustyai.explainability.model.Value;
import org.kie.trustyai.service.data.DataSource;
import org.kie.trustyai.service.payloads.service.Schema;
import org.kie.trustyai.service.payloads.service.SchemaItem;
import org.kie.trustyai.service.payloads.values.DataType;

public class MetadataUtils {
    private static final Logger LOG = Logger.getLogger(MetadataUtils.class);

    private MetadataUtils() {

    }

    private static DataType inferType(Value value){
        Object o = value.getUnderlyingObject();
        if (o instanceof Integer) {
            return DataType.INT32;
        } else if (o instanceof Double) {
            return DataType.DOUBLE;
        } else if (o instanceof Float) {
            return DataType.FLOAT;
        } else if (o instanceof Long) {
           return DataType.INT64;
        } else if (o instanceof Boolean) {
            return DataType.BOOL;
        } else if (o instanceof String) {
            return DataType.STRING;
        } else if (o instanceof Map) {
            return DataType.MAP;
        } else {
            return DataType.UNKNOWN;
        }
    }

    public static SchemaItem extractRowSchema(Dataframe dataframe, int i, boolean computeUniqueValues, List<DataType> dataTypes) {
        final SchemaItem schemaItem = new SchemaItem();

        // if we've specified the data types already, no need to infer them
        if (i%500==0) {
            LOG.info("row schema " + i);
        }
        if (dataTypes != null && i < dataTypes.size()){
            schemaItem.setType(dataTypes.get(i));
        } else {
            // otherwise, infer the types
            final Value value = dataframe.getValue(0, i);
            schemaItem.setType(MetadataUtils.inferType(value));
        }
        schemaItem.setName(dataframe.getColumnNames().get(i));

        // grab unique values
        if (computeUniqueValues) {
            Optional<Set<Object>> uniqueValues = getUniqueValuesShortCircuited(dataframe.getColumn(i));
            schemaItem.setValues(uniqueValues.orElse(null));
        } else {
            schemaItem.setValues(null);
        }

        schemaItem.setIndex(i);
        return schemaItem;
    }

    private static Optional<Set<Object>> getUniqueValuesShortCircuited(List<Value> column){
        Set<Object> uniqueValues = new HashSet<>();
        int nUniques = 0;
        for (Value v : column){
            if (uniqueValues.add(v.getUnderlyingObject())) {
                nUniques += 1;
                if (nUniques > 200) {
                    return Optional.empty();
                }
            }
        }
        return Optional.of(uniqueValues);
    }

    public static Schema getInputSchema(Dataframe dataframe) {
        boolean computeUniqueValues = dataframe.getColumnDimension() < 100;
        return Schema.from(dataframe
                .getInputsIndices()
                .stream()
                .map(i -> extractRowSchema(dataframe, i, computeUniqueValues, null))
                .collect(Collectors.toMap(SchemaItem::getName, Function.identity())));
    }

    public static Schema getOutputSchema(Dataframe dataframe) {
        boolean computeUniqueValues = dataframe.getColumnDimension() < 100;
        return Schema.from(dataframe.getOutputsIndices().stream()
                .map(i -> extractRowSchema(dataframe, i, computeUniqueValues, null))
                .collect(Collectors.toMap(SchemaItem::getName, Function.identity())));
    }


    public static Schema getInputSchema(Dataframe dataframe, List<DataType> dataTypes) {
        boolean computeUniqueValues = dataframe.getColumnDimension() < 100;
        return Schema.from(dataframe
                .getInputsIndices()
                .stream()
                .map(i -> extractRowSchema(dataframe, i, computeUniqueValues, dataTypes))
                .collect(Collectors.toMap(SchemaItem::getName, Function.identity())));
    }

    public static Schema getOutputSchema(Dataframe dataframe, List<DataType> dataTypes) {
        boolean computeUniqueValues = dataframe.getColumnDimension() < 100;
        return Schema.from(dataframe.getOutputsIndices().stream()
                .map(i -> extractRowSchema(dataframe, i, computeUniqueValues, dataTypes))
                .collect(Collectors.toMap(SchemaItem::getName, Function.identity())));
    }

}
