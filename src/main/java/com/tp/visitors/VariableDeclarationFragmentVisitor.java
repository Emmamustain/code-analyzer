package com.tp.visitors;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;

/**
 * Visiteur AST pour collecter les fragments de déclaration de variables.
 * Utile pour analyser les variables locales dans les méthodes.
 */
public class VariableDeclarationFragmentVisitor extends ASTVisitor {
    private final List<VariableDeclarationFragment> variables = new ArrayList<>();

    @Override
    public boolean visit(VariableDeclarationFragment node) {
        variables.add(node);
        return super.visit(node);
    }

    public List<VariableDeclarationFragment> getVariables() {
        return variables;
    }
}