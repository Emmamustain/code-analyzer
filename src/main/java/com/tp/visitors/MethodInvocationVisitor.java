package com.tp.visitors;

import java.util.ArrayList;
import java.util.List;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.SuperMethodInvocation;

/**
 * Visiteur AST pour collecter toutes les invocations de méthodes.
 * Essentiel pour construire le graphe d'appel (Exercice 2.1).
 * Gère à la fois les appels normaux et les appels à super().
 */
public class MethodInvocationVisitor extends ASTVisitor {
    private final List<MethodInvocation> methods = new ArrayList<>();
    private final List<SuperMethodInvocation> superMethods = new ArrayList<>();

    /**
     * Collecte les invocations de méthodes normales (ex: obj.method()).
     */
  @Override
  public boolean visit(MethodInvocation node) {
    methods.add(node);
    return super.visit(node);
  }

    /**
     * Collecte les invocations de méthodes de la super-classe (ex: super.method()).
     * Important pour les classes avec héritage.
     */
    @Override
    public boolean visit(SuperMethodInvocation node) {
        superMethods.add(node);
        return super.visit(node);
    }

    public List<MethodInvocation> getMethods() {
        return methods;
    }

    public List<SuperMethodInvocation> getSuperMethods() {
        return superMethods;
    }
    
}