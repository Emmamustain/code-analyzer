package com.tp;


public class Analyzer {
    public static void main(String[] args) throws Exception {
        // Récupère le chemin du projet à analyser depuis les arguments
        // ou utilise un chemin par défaut pour les tests
        String projectPath = (args.length > 0)
                ? args[0]
                : "/Users/clstialdsn/eclipse-workspace/company-app/src/main/java";

        ParserAnalyzer pa = new ParserAnalyzer(projectPath);
        pa.analyze();
        
        // X=3 : affiche les classes avec plus de 3 méthodes (point 11 du TP)
        pa.printStatistics(3); 
        pa.printCallGraph();
    }
}