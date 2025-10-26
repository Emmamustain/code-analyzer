package com.tp.analysis;

import java.io.FileWriter;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Générateur de graphe de couplage pondéré à partir des résultats d'analyse.
 * Supporte plusieurs formats de sortie : DOT (Graphviz), JSON, et CSV.
 */
public class GraphGenerator {
    
    private final Map<String, Map<String, Double>> couplingWeights;
    private final Map<String, Map<String, Integer>> couplingCounts;
    private final int totalInterClassEdges;
    
    public GraphGenerator(Map<String, Map<String, Double>> couplingWeights,
                         Map<String, Map<String, Integer>> couplingCounts,
                         int totalInterClassEdges) {
        this.couplingWeights = couplingWeights;
        this.couplingCounts = couplingCounts;
        this.totalInterClassEdges = totalInterClassEdges;
    }
    
    /**
     * Génère un graphe au format DOT (Graphviz) avec les poids de couplage.
     * @param outputFile Chemin du fichier de sortie
     * @param minWeight Seuil minimum de poids pour inclure une arête
     * @param maxNodes Nombre maximum de nœuds à afficher
     */
    public void generateDotGraph(String outputFile, double minWeight, int maxNodes) throws IOException {
        try (FileWriter writer = new FileWriter(outputFile)) {
            writer.write("digraph CouplingGraph {\n");
            writer.write("  rankdir=LR;\n");
            writer.write("  node [shape=box, style=filled, fillcolor=lightblue];\n");
            writer.write("  edge [fontsize=10];\n\n");
            
            // Collecter toutes les classes impliquées
            Set<String> allClasses = new HashSet<>();
            for (var entry : couplingWeights.entrySet()) {
                allClasses.add(entry.getKey());
                allClasses.addAll(entry.getValue().keySet());
            }
            
            // Limiter le nombre de nœuds si nécessaire
            List<String> classesToShow = allClasses.stream()
                .sorted()
                .limit(maxNodes)
                .collect(Collectors.toList());
            
            // Ajouter les nœuds
            for (String className : classesToShow) {
                String shortName = getShortClassName(className);
                writer.write(String.format("  \"%s\" [label=\"%s\"];\n", className, shortName));
            }
            writer.write("\n");
            
            // Ajouter les arêtes avec poids
            int edgeCount = 0;
            for (var entry : couplingWeights.entrySet()) {
                String source = entry.getKey();
                if (!classesToShow.contains(source)) continue;
                
                for (var targetEntry : entry.getValue().entrySet()) {
                    String target = targetEntry.getKey();
                    double weight = targetEntry.getValue();
                    
                    if (!classesToShow.contains(target) || weight < minWeight) continue;
                    
                    int count = couplingCounts.get(source).get(target);
                    String label = String.format("%.3f (%d)", weight, count);
                    
                    writer.write(String.format("  \"%s\" -> \"%s\" [label=\"%s\", weight=%.3f];\n", 
                        source, target, label, weight));
                    edgeCount++;
                }
            }
            
            writer.write("\n");
            writer.write(String.format("  // Total edges: %d\n", edgeCount));
            writer.write(String.format("  // Total inter-class calls: %d\n", totalInterClassEdges));
            writer.write("}\n");
        }
    }
    
    /**
     * Génère un graphe au format JSON avec métadonnées complètes.
     * @param outputFile Chemin du fichier de sortie
     * @param minWeight Seuil minimum de poids pour inclure une arête
     */
    public void generateJsonGraph(String outputFile, double minWeight) throws IOException {
        try (FileWriter writer = new FileWriter(outputFile)) {
            writer.write("{\n");
            writer.write("  \"metadata\": {\n");
            writer.write(String.format("    \"totalInterClassEdges\": %d,\n", totalInterClassEdges));
            writer.write(String.format("    \"minWeight\": %.4f,\n", minWeight));
            writer.write(String.format("    \"generatedAt\": \"%s\"\n", new Date().toString()));
            writer.write("  },\n");
            
            writer.write("  \"nodes\": [\n");
            Set<String> allClasses = new HashSet<>();
            for (var entry : couplingWeights.entrySet()) {
                allClasses.add(entry.getKey());
                allClasses.addAll(entry.getValue().keySet());
            }
            
            List<String> sortedClasses = allClasses.stream().sorted().collect(Collectors.toList());
            for (int i = 0; i < sortedClasses.size(); i++) {
                String className = sortedClasses.get(i);
                String shortName = getShortClassName(className);
                writer.write(String.format("    {\n"));
                writer.write(String.format("      \"id\": \"%s\",\n", className));
                writer.write(String.format("      \"label\": \"%s\",\n", shortName));
                writer.write(String.format("      \"package\": \"%s\"\n", getPackageName(className)));
                writer.write(String.format("    }%s\n", i < sortedClasses.size() - 1 ? "," : ""));
            }
            writer.write("  ],\n");
            
            writer.write("  \"edges\": [\n");
            List<String> edges = new ArrayList<>();
            for (var entry : couplingWeights.entrySet()) {
                String source = entry.getKey();
                for (var targetEntry : entry.getValue().entrySet()) {
                    String target = targetEntry.getKey();
                    double weight = targetEntry.getValue();
                    
                    if (weight < minWeight) continue;
                    
                    int count = couplingCounts.get(source).get(target);
                    edges.add(String.format("    {\n" +
                        "      \"source\": \"%s\",\n" +
                        "      \"target\": \"%s\",\n" +
                        "      \"weight\": %.6f,\n" +
                        "      \"count\": %d\n" +
                        "    }", source, target, weight, count));
                }
            }
            
            for (int i = 0; i < edges.size(); i++) {
                writer.write(edges.get(i));
                writer.write(i < edges.size() - 1 ? ",\n" : "\n");
            }
            
            writer.write("  ]\n");
            writer.write("}\n");
        }
    }
    
    /**
     * Génère un fichier CSV avec les statistiques de couplage.
     * @param outputFile Chemin du fichier de sortie
     * @param minWeight Seuil minimum de poids pour inclure une arête
     */
    public void generateCsvReport(String outputFile, double minWeight) throws IOException {
        try (FileWriter writer = new FileWriter(outputFile)) {
            writer.write("Source,Target,Weight,Count,Percentage\n");
            
            List<CouplingEdge> edges = new ArrayList<>();
            for (var entry : couplingWeights.entrySet()) {
                String source = entry.getKey();
                for (var targetEntry : entry.getValue().entrySet()) {
                    String target = targetEntry.getKey();
                    double weight = targetEntry.getValue();
                    
                    if (weight < minWeight) continue;
                    
                    int count = couplingCounts.get(source).get(target);
                    edges.add(new CouplingEdge(source, target, weight, count));
                }
            }
            
            // Trier par poids décroissant
            edges.sort((e1, e2) -> Double.compare(e2.weight, e1.weight));
            
            for (CouplingEdge edge : edges) {
                double percentage = edge.weight * 100;
                writer.write(String.format("%s,%s,%.6f,%d,%.2f%%\n",
                    edge.source, edge.target, edge.weight, edge.count, percentage));
            }
        }
    }
    
    /**
     * Génère un résumé textuel des statistiques de couplage.
     */
    public String generateTextSummary(double minWeight) {
        StringBuilder sb = new StringBuilder();
        
        sb.append("=== RÉSUMÉ DU GRAPHE DE COUPLAGE ===\n\n");
        sb.append(String.format("Total des appels inter-classes: %d\n", totalInterClassEdges));
        sb.append(String.format("Seuil de poids minimum: %.4f\n", minWeight));
        
        // Compter les arêtes au-dessus du seuil
        int edgesAboveThreshold = 0;
        double totalWeightAboveThreshold = 0.0;
        
        for (var entry : couplingWeights.entrySet()) {
            for (var targetEntry : entry.getValue().entrySet()) {
                double weight = targetEntry.getValue();
                if (weight >= minWeight) {
                    edgesAboveThreshold++;
                    totalWeightAboveThreshold += weight;
                }
            }
        }
        
        sb.append(String.format("Arêtes au-dessus du seuil: %d\n", edgesAboveThreshold));
        sb.append(String.format("Poids total au-dessus du seuil: %.4f\n", totalWeightAboveThreshold));
        
        // Top 10 des couplages les plus forts
        sb.append("\n=== TOP 10 DES COUPLAGES LES PLUS FORTS ===\n");
        List<CouplingEdge> topEdges = new ArrayList<>();
        
        for (var entry : couplingWeights.entrySet()) {
            String source = entry.getKey();
            for (var targetEntry : entry.getValue().entrySet()) {
                String target = targetEntry.getKey();
                double weight = targetEntry.getValue();
                int count = couplingCounts.get(source).get(target);
                topEdges.add(new CouplingEdge(source, target, weight, count));
            }
        }
        
        topEdges.sort((e1, e2) -> Double.compare(e2.weight, e1.weight));
        
        for (int i = 0; i < Math.min(10, topEdges.size()); i++) {
            CouplingEdge edge = topEdges.get(i);
            sb.append(String.format("%2d) %s -> %s: %.4f (%d appels)\n",
                i + 1, getShortClassName(edge.source), getShortClassName(edge.target),
                edge.weight, edge.count));
        }
        
        return sb.toString();
    }
    
    /**
     * Extrait le nom court de la classe (sans le package).
     */
    private String getShortClassName(String fullClassName) {
        int lastDot = fullClassName.lastIndexOf('.');
        return lastDot >= 0 ? fullClassName.substring(lastDot + 1) : fullClassName;
    }
    
    /**
     * Extrait le nom du package d'une classe.
     */
    private String getPackageName(String fullClassName) {
        int lastDot = fullClassName.lastIndexOf('.');
        return lastDot >= 0 ? fullClassName.substring(0, lastDot) : "";
    }
    
    /**
     * Classe interne pour représenter une arête de couplage.
     */
    private static class CouplingEdge {
        final String source;
        final String target;
        final double weight;
        final int count;
        
        CouplingEdge(String source, String target, double weight, int count) {
            this.source = source;
            this.target = target;
            this.weight = weight;
            this.count = count;
        }
    }
}
