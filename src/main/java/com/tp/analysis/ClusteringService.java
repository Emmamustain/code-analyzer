package com.tp.analysis;

import java.util.*;

/**
 * Service principal pour le clustering hiérarchique et l'identification des modules.
 * Orchestre tout le processus de regroupement des classes.
 */
public class ClusteringService {
    
    private final Map<String, Map<String, Integer>> couplingMatrix;
    private final Map<String, Map<String, Double>> couplingWeights;
    private DendrogramNode dendrogram;
    private List<ModuleIdentifier.Module> modules;
    
    public ClusteringService(Map<String, Map<String, Integer>> couplingMatrix,
                            Map<String, Map<String, Double>> couplingWeights) {
        this.couplingMatrix = couplingMatrix;
        this.couplingWeights = couplingWeights;
    }
    
    /**
     * Exécute le processus complet de clustering et d'identification des modules.
     */
    public ClusteringResult performCompleteClustering(double minCoupling) {
        System.out.println("=== DÉBUT PROCESSUS DE CLUSTERING COMPLET ===");
        
        // Étape 1: Clustering hiérarchique
        System.out.println("\n1. CLUSTERING HIÉRARCHIQUE");
        HierarchicalClustering clustering = new HierarchicalClustering(couplingMatrix, couplingWeights);
        this.dendrogram = clustering.performClustering();
        
        // Afficher le dendrogramme
        clustering.printDendrogram(dendrogram);
        
        // Étape 2: Identification des modules
        System.out.println("\n2. IDENTIFICATION DES MODULES");
        // Calculer le nombre total de classes à partir du dendrogramme
        int totalClasses = dendrogram.getClassCount();
        System.out.println("Nombre total de classes détectées: " + totalClasses);
        ModuleIdentifier moduleIdentifier = new ModuleIdentifier(dendrogram, totalClasses, minCoupling, couplingWeights);
        this.modules = moduleIdentifier.identifyModules();
        
        // Étape 3: Génération du rapport
        System.out.println("\n3. GÉNÉRATION DU RAPPORT");
        ClusteringResult result = new ClusteringResult(dendrogram, modules, couplingMatrix, couplingWeights);
        
        System.out.println("\n=== FIN PROCESSUS DE CLUSTERING COMPLET ===");
        return result;
    }
    
    /**
     * Retourne le dendrogramme généré.
     */
    public DendrogramNode getDendrogram() {
        return dendrogram;
    }
    
    /**
     * Retourne les modules identifiés.
     */
    public List<ModuleIdentifier.Module> getModules() {
        return modules;
    }
    
    /**
     * Génère un rapport textuel du clustering.
     */
    public String generateTextReport() {
        if (dendrogram == null || modules == null) {
            return "Aucun clustering effectué. Exécutez d'abord performCompleteClustering().";
        }
        
        StringBuilder report = new StringBuilder();
        report.append("=== RAPPORT DE CLUSTERING HIÉRARCHIQUE ===\n\n");
        
        // Informations générales
        report.append("INFORMATIONS GÉNÉRALES:\n");
        report.append("- Nombre total de classes: ").append(dendrogram.getClassCount()).append("\n");
        report.append("- Nombre de modules identifiés: ").append(modules.size()).append("\n");
        report.append("- Couplage minimum requis: ").append(String.format("%.3f", getMinCouplingUsed())).append("\n\n");
        
        // Détails des modules
        report.append("MODULES IDENTIFIÉS:\n");
        for (int i = 0; i < modules.size(); i++) {
            ModuleIdentifier.Module module = modules.get(i);
            report.append(String.format("%d. %s\n", i + 1, module));
        }
        
        report.append("\n=== STATISTIQUES DÉTAILLÉES ===\n");
        
        // Statistiques par module
        for (int i = 0; i < modules.size(); i++) {
            ModuleIdentifier.Module module = modules.get(i);
            report.append(String.format("\nModule %d: %s\n", i + 1, module.getId()));
            report.append(String.format("  - Nombre de classes: %d\n", module.getClassCount()));
            report.append(String.format("  - Couplage moyen: %.3f\n", module.getAverageCoupling()));
            report.append(String.format("  - Classes: %s\n", module.getClasses()));
        }
        
        return report.toString();
    }
    
    /**
     * Génère un rapport CSV des modules.
     */
    public String generateCSVReport() {
        if (modules == null) {
            return "Aucun module identifié.";
        }
        
        StringBuilder csv = new StringBuilder();
        csv.append("Module_ID,Class_Count,Average_Coupling,Classes\n");
        
        for (int i = 0; i < modules.size(); i++) {
            ModuleIdentifier.Module module = modules.get(i);
            csv.append(module.getId()).append(",");
            csv.append(module.getClassCount()).append(",");
            csv.append(String.format("%.3f", module.getAverageCoupling())).append(",");
            csv.append("\"").append(String.join(";", module.getClasses())).append("\"\n");
        }
        
        return csv.toString();
    }
    
    /**
     * Obtient le couplage minimum utilisé pour l'identification des modules.
     */
    private double getMinCouplingUsed() {
        if (modules == null || modules.isEmpty()) {
            return 0.0;
        }
        
        double minCoupling = Double.MAX_VALUE;
        for (ModuleIdentifier.Module module : modules) {
            if (module.getAverageCoupling() < minCoupling) {
                minCoupling = module.getAverageCoupling();
            }
        }
        return minCoupling;
    }
    
    /**
     * Classe pour encapsuler les résultats du clustering.
     */
    public static class ClusteringResult {
        private final DendrogramNode dendrogram;
        private final List<ModuleIdentifier.Module> modules;
        private final Map<String, Map<String, Integer>> couplingMatrix;
        private final Map<String, Map<String, Double>> couplingWeights;
        
        public ClusteringResult(DendrogramNode dendrogram, 
                              List<ModuleIdentifier.Module> modules,
                              Map<String, Map<String, Integer>> couplingMatrix,
                              Map<String, Map<String, Double>> couplingWeights) {
            this.dendrogram = dendrogram;
            this.modules = modules;
            this.couplingMatrix = couplingMatrix;
            this.couplingWeights = couplingWeights;
        }
        
        public DendrogramNode getDendrogram() { return dendrogram; }
        public List<ModuleIdentifier.Module> getModules() { return modules; }
        public Map<String, Map<String, Integer>> getCouplingMatrix() { return couplingMatrix; }
        public Map<String, Map<String, Double>> getCouplingWeights() { return couplingWeights; }
    }
}
