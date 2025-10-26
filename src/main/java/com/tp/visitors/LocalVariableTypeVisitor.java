package com.tp.visitors;

import org.eclipse.jdt.core.dom.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Visitor pour capturer les types des variables locales.
 */
public class LocalVariableTypeVisitor extends ASTVisitor {
    private final Map<String, String> variableTypes = new HashMap<>();
    private String currentPackage = "";

    public Map<String, String> getVariableTypes() {
        return variableTypes;
    }

    @Override
    public boolean visit(CompilationUnit node) {
        PackageDeclaration pkg = node.getPackage();
        if (pkg != null) {
            currentPackage = pkg.getName().getFullyQualifiedName();
        }
        return true;
    }

    @Override
    public boolean visit(VariableDeclarationStatement node) {
        Type type = node.getType();
        String typeName = getTypeName(type);
        
        for (Object fragment : node.fragments()) {
            if (fragment instanceof VariableDeclarationFragment) {
                VariableDeclarationFragment vdf = (VariableDeclarationFragment) fragment;
                            String variableName = vdf.getName().getIdentifier();
                            variableTypes.put(variableName, typeName);
            }
        }
        return true;
    }

    @Override
    public boolean visit(VariableDeclarationExpression node) {
        Type type = node.getType();
        String typeName = getTypeName(type);
        
        for (Object fragment : node.fragments()) {
            if (fragment instanceof VariableDeclarationFragment) {
                VariableDeclarationFragment vdf = (VariableDeclarationFragment) fragment;
                            String variableName = vdf.getName().getIdentifier();
                            variableTypes.put(variableName, typeName);
            }
        }
        return true;
    }

    private String getTypeName(Type type) {
        if (type instanceof SimpleType) {
            SimpleType simpleType = (SimpleType) type;
            String name = simpleType.getName().getFullyQualifiedName();
            // Si c'est un type simple sans package, essayer de le résoudre
            if (!name.contains(".")) {
                // Pour l'instant, on retourne le nom tel quel
                // Dans un vrai parser, on ferait une résolution de type
                return name;
            }
            return name;
        } else if (type instanceof QualifiedType) {
            QualifiedType qualifiedType = (QualifiedType) type;
            return qualifiedType.getName().getFullyQualifiedName();
        } else if (type instanceof ParameterizedType) {
            ParameterizedType paramType = (ParameterizedType) type;
            return getTypeName(paramType.getType());
        }
        return type.toString();
    }
}
