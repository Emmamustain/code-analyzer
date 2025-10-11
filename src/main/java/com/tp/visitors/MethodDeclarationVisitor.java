package com.tp.visitors;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.MethodDeclaration;

/**
 * Visiteur AST pour collecter toutes les déclarations de méthodes d'une classe.
 * Utilisé pour calculer le nombre total de méthodes et leurs métriques (LOC, paramètres).
 */
public class MethodDeclarationVisitor extends ASTVisitor {
    private final List<MethodDeclaration> methods = new ArrayList<>();

    @Override
    public boolean visit(MethodDeclaration node) {
        methods.add(node);
        return super.visit(node);
    }

    public List<MethodDeclaration> getMethods() {
        return methods;
    }
}