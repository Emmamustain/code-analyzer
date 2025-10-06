package com.tp.model;

public class MethodMetrics {
    private String name;
    private int lineCount;
    private int paramCount;

    public MethodMetrics(String name, int lineCount, int paramCount) {
        this.name = name;
        this.lineCount = lineCount;
        this.paramCount = paramCount;
    }

    public String getName() { return name; }
    public int getLineCount() { return lineCount; }
    public int getParamCount() { return paramCount; }

    @Override
    public String toString() {
        return name + " [LOC=" + lineCount + ", params=" + paramCount + "]";
    }
}