package com.tp.visitors;

import org.eclipse.jdt.core.dom.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Visitor pour capturer les appels sur variables locales.
 */
public class LocalVariableCallVisitor extends ASTVisitor {
    private final List<LocalVariableCall> localCalls = new ArrayList<>();
    private final Map<String, String> localVariableTypes;
    private String currentMethod = "";

    public LocalVariableCallVisitor(Map<String, String> localVariableTypes) {
        this.localVariableTypes = localVariableTypes;
    }

    public List<LocalVariableCall> getLocalCalls() {
        return localCalls;
    }

    @Override
    public boolean visit(MethodDeclaration node) {
        currentMethod = node.getName().getIdentifier();
        return true;
    }
    
    @Override
    public boolean visit(TypeDeclaration node) {
        // Ne traiter que les classes Main
        return node.getName().getIdentifier().equals("Main");
    }

    @Override
    public boolean visit(MethodInvocation node) {
        Expression expression = node.getExpression();
        if (expression instanceof SimpleName) {
            String variableName = ((SimpleName) expression).getIdentifier();
            String methodName = node.getName().getIdentifier();
            
            // Vérifier si c'est une variable locale
            if (localVariableTypes.containsKey(variableName)) {
                String variableType = localVariableTypes.get(variableName);
                localCalls.add(new LocalVariableCall(currentMethod, variableName, methodName, variableType));
            }
        }
        return true;
    }

    public static class LocalVariableCall {
        private final String method;
        private final String variable;
        private final String methodName;
        private final String variableType;

        public LocalVariableCall(String method, String variable, String methodName, String variableType) {
            this.method = method;
            this.variable = variable;
            this.methodName = methodName;
            this.variableType = variableType;
        }

        public String getMethod() { return method; }
        public String getVariable() { return variable; }
        public String getMethodName() { return methodName; }
        public String getVariableType() { return variableType; }
    }
}
