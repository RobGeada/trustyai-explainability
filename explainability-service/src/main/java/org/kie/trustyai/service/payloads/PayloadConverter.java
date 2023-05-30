package org.kie.trustyai.service.payloads;

import org.kie.trustyai.explainability.model.Type;
import org.kie.trustyai.explainability.model.Value;
import org.kie.trustyai.service.payloads.values.DataType;
import org.kie.trustyai.service.payloads.values.TypedValue;

import com.fasterxml.jackson.databind.JsonNode;

import static org.kie.trustyai.service.payloads.values.DataType.*;

public class PayloadConverter {
    private PayloadConverter() {
    }

    public static Value convertToValue(TypedValue node) {
        DataType type = node.getType();
        if (type == BOOL) {
            return new Value(node.getValue().asBoolean());
        } else if (type == FLOAT || type == DOUBLE) {
            return new Value(node.getValue().asDouble());
        } else if (type == INT32) {
            return new Value(node.getValue().asInt());
        } else if (type == INT64) {
            return new Value(node.getValue().asLong());
        } else if (type == STRING) {
            return new Value(node.getValue().asText());
        } else {
            return new Value(null);
        }
    }

    public static boolean checkValueType(DataType type, JsonNode v) {
        if (type == BOOL) {
            return v.isBoolean();
        } else if (type == FLOAT) {
            return v.isFloat();
        } else if (type == DOUBLE) {
            return v.isDouble();
        } else if (type == INT32) {
            return v.isInt();
        } else if (type == INT64) {
            return v.isLong();
        } else if (type == STRING) {
            return v.isTextual();
        } else {
            return false;
        }
    }

    public static DataType getNodeType(JsonNode v) {
        if (v.isBoolean()) {
            return BOOL;
        } else if (v.isFloat()) {
            return FLOAT;
        } else if (v.isDouble()) {
            return DOUBLE;
        } else if (v.isInt()) {
            return INT32;
        } else if (v.isLong()) {
            return INT64;
        } else if (v.isTextual()) {
            return STRING;
        } else {
            return UNKNOWN;
        }
    }

    public static Type convertToType(DataType type) {
        try {
            if (type == BOOL) {
                return Type.BOOLEAN;
            } else if (type == FLOAT || type == DOUBLE || type == INT32 || type == INT64) {
                return Type.NUMBER;
            } else if (type == STRING) {
                return Type.CATEGORICAL;
            } else {
                return Type.UNDEFINED;
            }
        } catch (IllegalArgumentException e) {
            return Type.UNDEFINED;
        }
    }

}
