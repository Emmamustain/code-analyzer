package com.tp.spoon;

import com.tp.analysis.DendrogramNode;
import com.tp.analysis.HierarchicalClustering;
import com.tp.analysis.ModuleIdentifier;
import com.tp.analysis.ClusteringService;
import java.util.*;

/**
 * Service de clustering hiérarchique utilisant Spoon.
 * Utilise les services de couplage Spoon pour effectuer le clustering.
 */
public class SpoonClusteringService {
    
    private final SpoonCouplingService couplingService;
    private DendrogramNode dendrogram;
    private List<ModuleIdentifier.Module> modules;
    private double minCouplingUsed;
    
    public SpoonClusteringService(SpoonCouplingService couplingService) {
        this.couplingService = couplingService;
    }
    
    /**
     * Effectue le clustering hiérarchique complet.
     */
    public ClusteringService.ClusteringResult performCompleteClustering(double minCoupling) {
        this.minCouplingUsed = minCoupling;
        
        System.out.println("=== DÉBUT PROCESSUS DE CLUSTERING SPOON ===");
        
        // Obtenir les données de couplage
        Map<String, Map<String, Integer>> couplingMatrix = couplingService.getCouplingMatrix();
        Map<String, Map<String, Double>> couplingWeights = couplingService.getCouplingWeights();
        
        // Étape 1: Clustering hiérarchique
        System.out.println("\n1. CLUSTERING HIÉRARCHIQUE (SPOON)");
        HierarchicalClustering clustering = new HierarchicalClustering(couplingMatrix, couplingWeights);
        this.dendrogram = clustering.performClustering();
        
        // Afficher le dendrogramme
        clustering.printDendrogram(dendrogram);
        
        // Étape 2: Identification des modules
        System.out.println("\n2. IDENTIFICATION DES MODULES (SPOON)");
        int totalClasses = dendrogram.getClassCount();
        System.out.println("Nombre total de classes détectées: " + totalClasses);
        
        ModuleIdentifier moduleIdentifier = new ModuleIdentifier(dendrogram, totalClasses, minCoupling, couplingWeights);
        this.modules = moduleIdentifier.identifyModules();
        
        // Étape 3: Génération du rapport
        System.out.println("\n3. GÉNÉRATION DU RAPPORT (SPOON)");
        ClusteringService.ClusteringResult result = new ClusteringService.ClusteringResult(
            dendrogram, modules, couplingMatrix, couplingWeights);
        
        System.out.println("\n=== FIN PROCESSUS DE CLUSTERING SPOON ===");
        return result;
    }
    
    /**
     * Génère un rapport textuel du clustering Spoon.
     */
    public String generateTextReport() {
        if (dendrogram == null || modules == null) {
            return "Aucun clustering Spoon effectué. Exécutez d'abord performCompleteClustering().";
        }

        StringBuilder report = new StringBuilder();
        report.append("=== RAPPORT DE CLUSTERING HIÉRARCHIQUE (SPOON) ===\n\n");
        
        // Informations générales
        report.append("INFORMATIONS GÉNÉRALES:\n");
        report.append("- Nombre total de classes: ").append(dendrogram.getClassCount()).append("\n");
        report.append("- Nombre de modules identifiés: ").append(modules.size()).append("\n");
        report.append("- Couplage minimum requis: ").append(String.format("%.3f", getMinCouplingUsed())).append("\n");
        report.append("- Analyseur utilisé: Spoon\n\n");
        
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
     * Génère un rapport CSV des modules Spoon.
     */
    public String generateCSVReport() {
        if (modules == null) {
            return "Module_ID,Class_Count,Average_Coupling,Classes,Analyzer\n";
        }

        StringBuilder csv = new StringBuilder();
        csv.append("Module_ID,Class_Count,Average_Coupling,Classes,Analyzer\n");
        for (ModuleIdentifier.Module module : modules) {
            csv.append(String.format("%s,%d,%.3f,\"%s\",Spoon\n",
                    module.getId(),
                    module.getClassCount(),
                    module.getAverageCoupling(),
                    String.join(";", module.getClasses())));
        }
        return csv.toString();
    }
    
    // Getters
    public DendrogramNode getDendrogram() {
        return dendrogram;
    }
    
    public List<ModuleIdentifier.Module> getModules() {
        return modules;
    }
    
    public double getMinCouplingUsed() {
        return minCouplingUsed;
    }
}
