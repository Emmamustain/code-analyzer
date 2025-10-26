package com.tp.analysis;

import java.util.*;

/**
 * Identifie les modules à partir du dendrogramme en respectant les contraintes
 */
public class ModuleIdentifier {
    
    private final DendrogramNode dendrogram;
    private final int maxModules;
    private final double minCoupling;
    private final Map<String, Map<String, Double>> couplingWeights;
    
    public ModuleIdentifier(DendrogramNode dendrogram, int totalClasses, double minCoupling) {
        this.dendrogram = dendrogram;
        this.maxModules = totalClasses / 2;
        this.minCoupling = minCoupling;
        this.couplingWeights = null;
    }
    
    public ModuleIdentifier(DendrogramNode dendrogram, int totalClasses, double minCoupling, 
                           Map<String, Map<String, Double>> couplingWeights) {
        this.dendrogram = dendrogram;
        this.maxModules = totalClasses / 2;
        this.minCoupling = minCoupling;
        this.couplingWeights = couplingWeights;
    }
    
    /**
     * Identifie les modules en respectant les contraintes CP et M/2
     */
    public List<Module> identifyModules() {
        System.out.println("=== IDENTIFICATION DES MODULES ===");
        System.out.println("Contraintes:");
        System.out.println("- Nombre maximum de modules: " + maxModules);
        System.out.println("- Couplage minimum par module: " + minCoupling);
        
        List<Module> modules = new ArrayList<>();
        cutDendrogram(dendrogram, modules);
        
        System.out.println("\n=== RÉSULTATS ===");
        System.out.println("Nombre de modules identifiés: " + modules.size());
        for (int i = 0; i < modules.size(); i++) {
            System.out.println("Module " + (i + 1) + ": " + modules.get(i));
        }
        
        // Vérifier les contraintes
        verifyConstraints(modules);
        
        return modules;
    }
    
    /**
     * Effectue une coupe top-down du dendrogramme.
     * Descend récursivement tant que le couplage >= minCoupling et qu'on ne dépasse pas maxModules.
     */
    private void cutDendrogram(DendrogramNode node, List<Module> modules) {
        if (modules.size() >= maxModules) {
            // Limite atteinte, on arrête de découper et on garde le cluster actuel
            System.out.println("Limite de modules atteinte (" + maxModules + "), arrêt de la coupe.");
            if (!node.isLeaf()) {
                addModuleIfValid(node, modules);
            }
            return;
        }
        
        if (node.isLeaf()) {
            // Feuille : on l'ajoute comme module individuel uniquement si elle a un couplage valide
            addModuleIfValid(node, modules);
            return;
        }
        
        // Vérifier si ce cluster satisfait le couplage minimum
        double avgCoupling = calculateAverageCoupling(node);
        
        if (avgCoupling >= minCoupling) {
            // Le cluster satisfait CP, on l'ajoute comme module
            System.out.println("Cluster satisfait CP: " + node.getId() + 
                             " (couplage: " + String.format("%.3f", avgCoupling) + ")");
            addModuleIfValid(node, modules);
        } else {
            // Le cluster ne satisfait pas CP, on le scinde en ses deux fils
            if (node.getLeft() != null && node.getRight() != null) {
                System.out.println("Cluster ne satisfait pas CP: " + node.getId() + 
                                 " (couplage: " + String.format("%.3f", avgCoupling) + 
                                 "), scission en sous-clusters");
                cutDendrogram(node.getLeft(), modules);
                cutDendrogram(node.getRight(), modules);
            } else {
                // Pas de fils, on accepte quand même ce cluster
                addModuleIfValid(node, modules);
            }
        }
    }
    
    /**
     * Ajoute un module s'il est valide (non vide, couplage >= minCoupling).
     */
    private void addModuleIfValid(DendrogramNode node, List<Module> modules) {
        double avgCoupling = calculateAverageCoupling(node);
        
        if (node.getClassCount() > 0 && avgCoupling >= minCoupling) {
            Module module = new Module(node, avgCoupling);
            modules.add(module);
            System.out.println("Module ajouté: " + module);
        } else if (node.getClassCount() > 0) {
            System.out.println("Cluster rejeté (couplage insuffisant): " + 
                             node.getId() + " (couplage: " + 
                             String.format("%.3f", avgCoupling) + ")");
        }
    }
    
    /**
     * Calcule le couplage moyen entre toutes les paires de classes du module.
     */
    private double calculateAverageCoupling(DendrogramNode branch) {
        Set<String> classes = branch.getClasses();
        if (classes.size() < 2) {
            // Module de taille 1 : pas de couplage interne
            return 0.0;
        }
        
        double totalCoupling = 0.0;
        int pairCount = 0;
        
        List<String> classList = new ArrayList<>(classes);
        for (int i = 0; i < classList.size(); i++) {
            for (int j = i + 1; j < classList.size(); j++) {
                String class1 = classList.get(i);
                String class2 = classList.get(j);
                
                double coupling = getCouplingBetweenClasses(class1, class2);
                totalCoupling += coupling;
                pairCount++;
            }
        }
        
        return pairCount > 0 ? totalCoupling / pairCount : 0.0;
    }
    
    /**
     * Obtient le couplage entre deux classes.
     */
    private double getCouplingBetweenClasses(String class1, String class2) {
        if (couplingWeights == null) {
            return 0.0;
        }
        
        // Vérifier dans les deux directions
        if (couplingWeights.containsKey(class1) && couplingWeights.get(class1).containsKey(class2)) {
            return couplingWeights.get(class1).get(class2);
        }
        if (couplingWeights.containsKey(class2) && couplingWeights.get(class2).containsKey(class1)) {
            return couplingWeights.get(class2).get(class1);
        }
        return 0.0;
    }
    
    /**
     * Vérifie les contraintes sur les modules identifiés.
     */
    private void verifyConstraints(List<Module> modules) {
        System.out.println("\n=== VÉRIFICATION DES CONTRAINTES ===");
        
        boolean constraintM2 = modules.size() <= maxModules;
        boolean constraintCP = modules.stream().allMatch(m -> m.getAverageCoupling() >= minCoupling);
        
        System.out.println("- Contrainte M/2: " + modules.size() + " modules (max: " + maxModules + ") " + 
                         (constraintM2 ? "OK" : "ECHEC"));
        System.out.println("- Contrainte CP: Tous les modules respectent CP=" + minCoupling + " " + 
                         (constraintCP ? "OK" : "ECHEC"));
    }
    
    /**
     * Représente un module (groupe de classes).
     */
    public static class Module {
        private final String id;
        private final Set<String> classes;
        private final double averageCoupling;
        
        public Module(DendrogramNode branch, double avgCoupling) {
            this.id = "Module_" + branch.getId();
            this.classes = new HashSet<>(branch.getClasses());
            this.averageCoupling = avgCoupling;
        }
        
        public String getId() {
            return id;
        }
        
        public Set<String> getClasses() {
            return new HashSet<>(classes);
        }
        
        public double getAverageCoupling() {
            return averageCoupling;
        }
        
        public int getClassCount() {
            return classes.size();
        }
        
        @Override
        public String toString() {
            return id + " (classes: " + classes.size() + ", couplage moyen: " + 
                   String.format("%.3f", averageCoupling) + ", classes: " + classes + ")";
        }
    }
}
