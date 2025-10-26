package com.tp.analysis;

import com.tp.ParserAnalyzer;

import java.util.Map;
import java.util.Set;

/**
 * Service utilitaire pour générer des graphes de couplage à partir d'un ParserAnalyzer.
 * Facilite l'intégration avec l'interface graphique.
 */
public class GraphService {
    
    /**
     * Génère tous les formats de graphe à partir d'un ParserAnalyzer.
     * @param analyzer L'analyseur contenant les données
     * @param outputDir Répertoire de sortie pour les fichiers
     * @param minWeight Seuil minimum de poids pour inclure une arête
     * @param maxNodes Nombre maximum de nœuds à afficher
     * @return Résumé textuel de la génération
     */
    public static String generateAllGraphs(ParserAnalyzer analyzer, String outputDir, 
                                         double minWeight, int maxNodes) {
        try {
            // Calculer les métriques de couplage
            Map<String, Set<String>> callGraph = analyzer.getCallGraph();
            Map<String, Map<String, Integer>> counts = CouplingService.countInterClassCalls(callGraph);
            int total = CouplingService.totalInterClassEdges(counts);
            Map<String, Map<String, Double>> weights = CouplingService.normalizeToCouplingWeights(counts, total);
            
            // Créer le générateur
            GraphGenerator graphGen = new GraphGenerator(weights, counts, total);
            
            // Générer les fichiers
            String dotFile = outputDir + "/coupling_graph.dot";
            String jsonFile = outputDir + "/coupling_graph.json";
            String csvFile = outputDir + "/coupling_report.csv";
            
            graphGen.generateDotGraph(dotFile, minWeight, maxNodes);
            graphGen.generateJsonGraph(jsonFile, minWeight);
            graphGen.generateCsvReport(csvFile, minWeight);
            
            // Retourner le résumé
            return graphGen.generateTextSummary(minWeight) + 
                   "\n=== FICHIERS GÉNÉRÉS ===\n" +
                   "• " + dotFile + " (format Graphviz)\n" +
                   "• " + jsonFile + " (format JSON)\n" +
                   "• " + csvFile + " (rapport CSV)\n";
                   
        } catch (Exception e) {
            return "Erreur lors de la génération des graphes : " + e.getMessage();
        }
    }
    
    /**
     * Génère uniquement le graphe DOT avec des paramètres par défaut.
     */
    public static String generateDotGraph(ParserAnalyzer analyzer, String outputFile) {
        return generateDotGraph(analyzer, outputFile, 0.001, 50);
    }
    
    /**
     * Génère le graphe DOT avec des paramètres personnalisés.
     */
    public static String generateDotGraph(ParserAnalyzer analyzer, String outputFile, 
                                        double minWeight, int maxNodes) {
        try {
            Map<String, Set<String>> callGraph = analyzer.getCallGraph();
            Map<String, Map<String, Integer>> counts = CouplingService.countInterClassCalls(callGraph);
            int total = CouplingService.totalInterClassEdges(counts);
            Map<String, Map<String, Double>> weights = CouplingService.normalizeToCouplingWeights(counts, total);
            
            GraphGenerator graphGen = new GraphGenerator(weights, counts, total);
            graphGen.generateDotGraph(outputFile, minWeight, maxNodes);
            
            return "Graphe DOT généré avec succès : " + outputFile;
        } catch (Exception e) {
            return "Erreur : " + e.getMessage();
        }
    }
    
    /**
     * Retourne les statistiques de couplage sous forme de texte.
     */
    public static String getCouplingStatistics(ParserAnalyzer analyzer) {
        try {
            Map<String, Set<String>> callGraph = analyzer.getCallGraph();
            Map<String, Map<String, Integer>> counts = CouplingService.countInterClassCalls(callGraph);
            int total = CouplingService.totalInterClassEdges(counts);
            Map<String, Map<String, Double>> weights = CouplingService.normalizeToCouplingWeights(counts, total);
            
            GraphGenerator graphGen = new GraphGenerator(weights, counts, total);
            return graphGen.generateTextSummary(0.001);
        } catch (Exception e) {
            return "Erreur lors du calcul des statistiques : " + e.getMessage();
        }
    }
}
