package com.tp;

import com.tp.analysis.CouplingService;
import com.tp.analysis.GraphGenerator;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class Analyzer {
  public static void main(String[] args) throws Exception {
    String projectPath =
        (args.length > 0)
            ? args[0]
            : "/Users/clstialdsn/eclipse-workspace/company-app/src/main/java";

    ParserAnalyzer pa = new ParserAnalyzer(projectPath);
    pa.analyze();

    // Optional: method stats and call graph
    pa.printStatistics(3);
    // pa.printCallGraph();

    // Exo 1 — Couplage entre classes
    Map<String, Set<String>> callGraph = pa.getCallGraph();
    var counts = CouplingService.countInterClassCalls(callGraph);
    int total = CouplingService.totalInterClassEdges(counts);
    var weights = CouplingService.normalizeToCouplingWeights(counts, total);

    record Pair(String a, String b, double w) {}
    List<Pair> list = new ArrayList<>();
    for (var a : weights.keySet()) {
      for (var b : weights.get(a).keySet()) {
        list.add(new Pair(a, b, weights.get(a).get(b)));
      }
    }
    list.sort((p1, p2) -> Double.compare(p2.w, p1.w));

    System.out.println("== Résumé couplage entre classes ==");
    System.out.println("Total inter-class calls = " + total);
    for (int i = 0; i < Math.min(20, list.size()); i++) {
      var p = list.get(i);
      System.out.printf("%2d) %s -- %s : %.4f%n", i + 1, p.a, p.b, p.w);
    }

    // Génération du graphe de couplage pondéré
    System.out.println("\n=== GÉNÉRATION DU GRAPHE DE COUPLAGE ===");
    GraphGenerator graphGen = new GraphGenerator(weights, counts, total);
    
    // Paramètres configurables
    double minWeight = 0.001; // Seuil minimum de poids (0.1%)
    int maxNodes = 50;        // Nombre maximum de nœuds à afficher
    
    try {
      // Génération des différents formats
      graphGen.generateDotGraph("coupling_graph.dot", minWeight, maxNodes);
      System.out.println("Graphe DOT généré : coupling_graph.dot");
      
      graphGen.generateJsonGraph("coupling_graph.json", minWeight);
      System.out.println("Graphe JSON généré : coupling_graph.json");
      
      graphGen.generateCsvReport("coupling_report.csv", minWeight);
      System.out.println("Rapport CSV généré : coupling_report.csv");
      
      // Affichage du résumé textuel
      System.out.println("\n" + graphGen.generateTextSummary(minWeight));
      
      System.out.println("\n=== INSTRUCTIONS POUR VISUALISER LE GRAPHE ===");
      System.out.println("Pour visualiser le graphe DOT avec Graphviz :");
      System.out.println("  dot -Tpng coupling_graph.dot -o coupling_graph.png");
      System.out.println("  dot -Tsvg coupling_graph.dot -o coupling_graph.svg");
      System.out.println("\nOu utilisez un visualiseur en ligne comme :");
      System.out.println("  https://dreampuf.github.io/GraphvizOnline/");
      
    } catch (Exception e) {
      System.err.println("Erreur lors de la génération du graphe : " + e.getMessage());
      e.printStackTrace();
    }
  }
}