package com.tp.visitors;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.TypeDeclaration;

/**
 * Visiteur AST pour collecter toutes les déclarations de types (classes, interfaces).
 * Utilisé pour calculer le nombre total de classes (point 1 du TP).
 */
public class TypeDeclarationVisitor extends ASTVisitor {
    private final List<TypeDeclaration> types = new ArrayList<>();

    @Override
    public boolean visit(TypeDeclaration node) {
        types.add(node);
        return super.visit(node);
    }

    public List<TypeDeclaration> getTypes() {
        return types;
    }
}