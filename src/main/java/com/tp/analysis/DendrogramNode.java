package com.tp.analysis;

import java.util.*;

/**
 * Représente un nœud dans le dendrogramme de clustering hiérarchique.
 * Un nœud peut être soit une feuille (classe individuelle) soit un cluster (groupe de classes).
 */
public class DendrogramNode {
    private final String id;
    private final Set<String> classes;
    private final DendrogramNode left;
    private final DendrogramNode right;
    private final double coupling;
    private final int level;
    
    /**
     * Constructeur pour une feuille (classe individuelle).
     */
    public DendrogramNode(String className) {
        this.id = className;
        this.classes = new HashSet<>();
        this.classes.add(className);
        this.left = null;
        this.right = null;
        this.coupling = 0.0;
        this.level = 0;
    }
    
    /**
     * Constructeur pour un cluster (nœud interne).
     */
    public DendrogramNode(String id, DendrogramNode left, DendrogramNode right, double coupling, int level) {
        this.id = id;
        this.classes = new HashSet<>();
        this.classes.addAll(left.getClasses());
        this.classes.addAll(right.getClasses());
        this.left = left;
        this.right = right;
        this.coupling = coupling;
        this.level = level;
    }
    
    /**
     * Vérifie si ce nœud est une feuille (classe individuelle).
     */
    public boolean isLeaf() {
        return left == null && right == null;
    }
    
    /**
     * Retourne l'ID du nœud.
     */
    public String getId() {
        return id;
    }
    
    /**
     * Retourne l'ensemble des classes contenues dans ce nœud.
     */
    public Set<String> getClasses() {
        return new HashSet<>(classes);
    }
    
    /**
     * Retourne le nœud gauche.
     */
    public DendrogramNode getLeft() {
        return left;
    }
    
    /**
     * Retourne le nœud droit.
     */
    public DendrogramNode getRight() {
        return right;
    }
    
    /**
     * Retourne le couplage entre les deux sous-clusters.
     */
    public double getCoupling() {
        return coupling;
    }
    
    /**
     * Retourne le niveau dans la hiérarchie.
     */
    public int getLevel() {
        return level;
    }
    
    /**
     * Retourne le nombre de classes dans ce nœud (compte toutes les classes feuilles uniquement).
     */
    public int getClassCount() {
        if (isLeaf()) {
            return 1;
        }
        
        Set<String> allClasses = new HashSet<>();
        collectLeaves(this, allClasses);
        return allClasses.size();
    }
    
    /**
     * Collecte toutes les classes feuilles du dendrogramme.
     */
    private void collectLeaves(DendrogramNode node, Set<String> classes) {
        if (node.isLeaf()) {
            classes.add(node.getId());
        } else {
            if (node.getLeft() != null) {
                collectLeaves(node.getLeft(), classes);
            }
            if (node.getRight() != null) {
                collectLeaves(node.getRight(), classes);
            }
        }
    }
    
    /**
     * Retourne une représentation textuelle du nœud.
     */
    @Override
    public String toString() {
        if (isLeaf()) {
            return "Leaf(" + id + ")";
        } else {
            return "Cluster(" + id + ", coupling=" + String.format("%.3f", coupling) + ", classes=" + classes.size() + ")";
        }
    }
    
    /**
     * Retourne une représentation détaillée du nœud.
     */
    public String toDetailedString() {
        if (isLeaf()) {
            return "Leaf(" + id + ")";
        } else {
            return "Cluster(" + id + ", coupling=" + String.format("%.3f", coupling) + 
                   ", level=" + level + ", classes=" + classes.size() + 
                   ", classes=" + classes + ")";
        }
    }
}
