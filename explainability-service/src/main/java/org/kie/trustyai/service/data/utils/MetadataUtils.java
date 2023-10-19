package org.kie.trustyai.service.data.utils;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.jboss.logging.Logger;
import org.kie.trustyai.explainability.model.Dataframe;
import org.kie.trustyai.explainability.model.Value;
import org.kie.trustyai.service.data.DataSource;
import org.kie.trustyai.service.payloads.service.Schema;
import org.kie.trustyai.service.payloads.service.SchemaItem;
import org.kie.trustyai.service.payloads.values.DataType;

import javax.print.DocFlavor;

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


    private static SchemaItem populateSchemaItem(String name, int i, Set<Object> values, DataType dataType){
        final SchemaItem schemaItem = new SchemaItem();
        schemaItem.setType(dataType);
        schemaItem.setName(name);
        schemaItem.setIndex(i);
        schemaItem.setValues(values);
        return schemaItem;
    }

    // infer datatype, do not get unique value enumeration
    private static SchemaItem extractRowSchemaNoUniquesNoDatatype(Dataframe dataframe, int i, String name) {
        final Value value = dataframe.getValue(0, i);
        return populateSchemaItem(name, i, null, MetadataUtils.inferType(value));

    }

    // use known datatype, do not get unique value enumeration
    private static SchemaItem extractRowSchemaNoUniquesWithDatatype(Dataframe dataframe, int i, DataType dataType, String name){
        return populateSchemaItem(name, i, null, dataType);
    }

    // infer datatype, get unique value enumeration
    private static SchemaItem extractRowSchemaUniquesNoDatatype(Dataframe dataframe, int i, String name){
        Optional<Set<Object>> uniqueValues = getUniqueValuesShortCircuited(dataframe.getColumn(i));
        final Value value = dataframe.getValue(0, i);
        return populateSchemaItem(name, i, uniqueValues.orElse(null), MetadataUtils.inferType(value));
    }

    // use known datatype, get unique value enumeration
    private static SchemaItem extractRowSchemaUniquesWithDatatype(Dataframe dataframe, int i, DataType dataType, String name){
        Optional<Set<Object>> uniqueValues = getUniqueValuesShortCircuited(dataframe.getColumn(i));
        return populateSchemaItem(name, i, uniqueValues.orElse(null), dataType);
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

    // use specialized function for map inner loop, depending on necessary computations
    private static Schema getGenericSchema(Stream<Integer> intStream, Dataframe dataframe, List<DataType> dataTypes){
        boolean computeUniqueValues = true; //dataframe.getColumnDimension() < 100;
        List<String> dataframeColumnNames = dataframe.getColumnNames();
        Stream<SchemaItem> schemaItemStream;
        if (computeUniqueValues && dataTypes == null){
            LOG.info("case 1");
            schemaItemStream = intStream.map(i -> extractRowSchemaUniquesNoDatatype(dataframe, i, dataframeColumnNames.get(i)));
        } else if (computeUniqueValues && dataTypes != null){
            LOG.info("case 2");
            LOG.info("n cols: " + dataTmes.size());
            schemaItemStream = intStream.map(i -> extractRowSchemaUniquesWithDatatype(dataframe, i, dataTypes.get(i), dataframeColumnNames.get(i)));
        } else if (!computeUniqueValues && dataTypes == null){
            LOG.info("case 3");
            LOG.info("n cols: " + dataframeColumnNames.size());
            schemaItemStream = intStream.map(i -> extractRowSchemaNoUniquesNoDatatype(dataframe, i, dataframeColumnNames.get(i)));
        } else {
            LOG.info("case 4");
            schemaItemStream = intStream.map(i -> extractRowSchemaNoUniquesWithDatatype(dataframe, i, dataTypes.get(i), dataframeColumnNames.get(i)));
        }
        return Schema.from(schemaItemStream.collect(Collectors.toMap(SchemaItem::getName, Function.identity())));
    }

    public static Schema getInputSchema(Dataframe dataframe) {
        return getGenericSchema(dataframe.getInputsIndices().stream(), dataframe, null);

    }

    public static Schema getOutputSchema(Dataframe dataframe) {
        return getGenericSchema(dataframe.getOutputsIndices().stream(), dataframe, null);
    }

    public static Schema getInputSchema(Dataframe dataframe, List<DataType> dataTypes) {
        return getGenericSchema(dataframe.getInputsIndices().stream(), dataframe, dataTypes);

    }

    public static Schema getOutputSchema(Dataframe dataframe, List<DataType> dataTypes) {
        return getGenericSchema(dataframe.getOutputsIndices().stream(), dataframe, dataTypes);
    }
}
