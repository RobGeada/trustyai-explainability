package org.kie.trustyai.service.payloads.service.readable;

import java.util.Set;
import java.util.stream.Collectors;

import org.kie.trustyai.explainability.model.UnderlyingObject;
import org.kie.trustyai.service.payloads.service.SchemaItem;
import org.kie.trustyai.service.payloads.values.DataType;

public class ReadableSchemaItem {
    private DataType type;
    private String name;
    private Set<Object> columnValues;
    private int columnIndex;

    public ReadableSchemaItem() {
    }

    public ReadableSchemaItem(SchemaItem schemaItem) {
        type = schemaItem.getType();
        name = schemaItem.getName();
        if (null == schemaItem.getColumnValues()) {
            columnValues = null;
        } else {
            columnValues = schemaItem.getColumnValues().stream().map(UnderlyingObject::getObject).collect(Collectors.toSet());
        }
        columnIndex = schemaItem.getColumnIndex();
    }

    public DataType getType() {
        return type;
    }

    public void setType(DataType type) {
        this.type = type;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Set<Object> getColumnValues() {
        return columnValues;
    }

    public void setColumnValues(Set<Object> columnValues) {
        this.columnValues = columnValues;
    }

    public int getColumnIndex() {
        return columnIndex;
    }

    public void setColumnIndex(int columnIndex) {
        this.columnIndex = columnIndex;
    }
}
