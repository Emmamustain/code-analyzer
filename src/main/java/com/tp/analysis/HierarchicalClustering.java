package com.tp.analysis;

import java.util.*;

/**
 * Clustering hiérarchique agglomératif pour regrouper les classes selon leur couplage
 */
public class HierarchicalClustering {
    
    private final Map<String, Map<String, Integer>> couplingMatrix;
    private final Map<String, Map<String, Double>> couplingWeights;
    private final List<String> classes;
    
    public HierarchicalClustering(Map<String, Map<String, Integer>> couplingMatrix, 
                                 Map<String, Map<String, Double>> couplingWeights) {
        this.couplingMatrix = couplingMatrix;
        this.couplingWeights = couplingWeights;
        this.classes = extractClasses(couplingMatrix);
    }
    
    private List<String> extractClasses(Map<String, Map<String, Integer>> couplingMatrix) {
        Set<String> classSet = new HashSet<>();
        for (String source : couplingMatrix.keySet()) {
            classSet.add(source);
            for (String target : couplingMatrix.get(source).keySet()) {
                classSet.add(target);
            }
        }
        return new ArrayList<>(classSet);
    }
    
    /**
     * Algorithme principal de clustering hiérarchique
     */
    public DendrogramNode performClustering() {
        System.out.println("=== DÉBUT CLUSTERING HIÉRARCHIQUE ===");
        System.out.println("Classes à regrouper: " + classes.size());
        
        List<DendrogramNode> clusters = new ArrayList<>();
        for (String className : classes) {
            clusters.add(new DendrogramNode(className));
        }
        
        int iteration = 0;
        
        // Algorithme agglomératif
        while (clusters.size() > 1) {
            iteration++;
            System.out.println("\n--- Itération " + iteration + " ---");
            System.out.println("Clusters restants: " + clusters.size());
            
            // Trouver les deux clusters les plus couplés
            ClusterPair closestPair = findClosestClusters(clusters);
            
            if (closestPair == null) {
                System.out.println("Aucune paire de clusters couplée trouvée. Arrêt du clustering.");
                break;
            }
            
            System.out.println("Clusters les plus couplés: " + 
                             closestPair.cluster1.getId() + " et " + 
                             closestPair.cluster2.getId() + 
                             " (couplage: " + String.format("%.3f", closestPair.coupling) + ")");
            
            // Créer un nouveau cluster en fusionnant les deux plus proches
            String newClusterId = "Cluster_" + iteration;
            DendrogramNode newCluster = new DendrogramNode(
                newClusterId, 
                closestPair.cluster1, 
                closestPair.cluster2, 
                closestPair.coupling,
                iteration
            );
            
            // Mettre à jour la liste des clusters
            clusters.remove(closestPair.cluster1);
            clusters.remove(closestPair.cluster2);
            clusters.add(newCluster);
            
            System.out.println("Nouveau cluster créé: " + newCluster.toDetailedString());
        }
        
        // Retourner le cluster final (racine du dendrogramme)
        DendrogramNode root = clusters.get(0);
        System.out.println("\n=== FIN CLUSTERING HIÉRARCHIQUE ===");
        System.out.println("Dendrogramme complet: " + root.toDetailedString());
        
        return root;
    }
    
    /**
     * Trouve les deux clusters les plus couplés.
     */
    private ClusterPair findClosestClusters(List<DendrogramNode> clusters) {
        ClusterPair bestPair = null;
        double maxCoupling = -1.0;
        
        for (int i = 0; i < clusters.size(); i++) {
            for (int j = i + 1; j < clusters.size(); j++) {
                DendrogramNode cluster1 = clusters.get(i);
                DendrogramNode cluster2 = clusters.get(j);
                
                double coupling = calculateClusterCoupling(cluster1, cluster2);
                
                if (coupling > maxCoupling) {
                    maxCoupling = coupling;
                    bestPair = new ClusterPair(cluster1, cluster2, coupling);
                }
            }
        }
        
        return bestPair;
    }
    
    /**
     * Calcule le couplage entre deux clusters.
     * Utilise la moyenne des couplages entre toutes les paires de classes des deux clusters.
     */
    private double calculateClusterCoupling(DendrogramNode cluster1, DendrogramNode cluster2) {
        double totalCoupling = 0.0;
        int pairCount = 0;
        
        for (String class1 : cluster1.getClasses()) {
            for (String class2 : cluster2.getClasses()) {
                double coupling = getCouplingBetweenClasses(class1, class2);
                totalCoupling += coupling;
                pairCount++;
            }
        }
        
        return pairCount > 0 ? totalCoupling / pairCount : 0.0;
    }
    
    /**
     * Obtient le couplage entre deux classes spécifiques.
     */
    private double getCouplingBetweenClasses(String class1, String class2) {
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
     * Classe interne pour représenter une paire de clusters.
     */
    private static class ClusterPair {
        final DendrogramNode cluster1;
        final DendrogramNode cluster2;
        final double coupling;
        
        ClusterPair(DendrogramNode cluster1, DendrogramNode cluster2, double coupling) {
            this.cluster1 = cluster1;
            this.cluster2 = cluster2;
            this.coupling = coupling;
        }
    }
    
    /**
     * Affiche le dendrogramme de manière hiérarchique.
     */
    public void printDendrogram(DendrogramNode root) {
        System.out.println("\n=== DENDROGRAMME ===");
        printDendrogramRecursive(root, 0);
    }
    
    /**
     * Affichage récursif du dendrogramme.
     */
    private void printDendrogramRecursive(DendrogramNode node, int depth) {
        String indent = "  ".repeat(depth);
        
        if (node.isLeaf()) {
            System.out.println(indent + "└─ " + node.getId());
        } else {
            System.out.println(indent + "├─ " + node.getId() + 
                             " (couplage: " + String.format("%.3f", node.getCoupling()) + 
                             ", classes: " + node.getClassCount() + ")");
            printDendrogramRecursive(node.getLeft(), depth + 1);
            printDendrogramRecursive(node.getRight(), depth + 1);
        }
    }
}
