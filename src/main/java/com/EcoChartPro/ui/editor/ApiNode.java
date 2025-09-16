package com.EcoChartPro.ui.editor;

import io.github.classgraph.ClassInfo;
import io.github.classgraph.FieldInfo;
import io.github.classgraph.MethodInfo;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * A wrapper class for nodes in the API reference JTree.
 * It holds reflected information and provides a clean toString() for display.
 */
public class ApiNode {
    
    public enum NodeType { CLASS, FIELD, METHOD }

    private final String displayText;
    private final Object apiObject;
    private final NodeType type;

    public ApiNode(ClassInfo classInfo) {
        this.displayText = classInfo.getSimpleName();
        this.apiObject = classInfo;
        this.type = NodeType.CLASS;
    }

    public ApiNode(FieldInfo fieldInfo) {
        this.displayText = fieldInfo.getName() + " : " + fieldInfo.getTypeSignatureOrTypeDescriptor().toStringWithSimpleNames();
        this.apiObject = fieldInfo;
        this.type = NodeType.FIELD;
    }

    public ApiNode(MethodInfo methodInfo) {
        String params = Arrays.stream(methodInfo.getParameterInfo())
                .map(p -> p.getTypeSignatureOrTypeDescriptor().toStringWithSimpleNames())
                .collect(Collectors.joining(", "));
        this.displayText = methodInfo.getName() + "(" + params + ")";
        this.apiObject = methodInfo;
        this.type = NodeType.METHOD;
    }

    public Object getApiObject() {
        return apiObject;
    }

    public NodeType getType() {
        return type;
    }

    @Override
    public String toString() {
        return displayText;
    }
}