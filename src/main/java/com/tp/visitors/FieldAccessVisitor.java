package com.tp.visitors;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.SimpleName;

/**
 * Visiteur AST pour collecter les accès aux champs/variables.
 * Utile pour analyser les dépendances entre attributs.
 */
public class FieldAccessVisitor extends ASTVisitor {
    private final List<SimpleName> fields = new ArrayList<>();

    @Override
    public boolean visit(SimpleName node) {
        if (!node.isDeclaration()
                && node.resolveBinding() instanceof IVariableBinding) {
            fields.add(node);
        }
        return super.visit(node);
    }

    public List<SimpleName> getFields() {
        return fields;
    }
}