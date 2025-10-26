package com.tp.spoon;

import com.tp.ParserAnalyzer;
import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.*;
import spoon.reflect.code.*;
import spoon.reflect.reference.*;
import java.util.*;

/**
 * Service de calcul de couplage utilisant Spoon.
 * Utilise le ParserAnalyzer JDT pour obtenir le graphe d'appel,
 * puis applique les calculs de couplage avec Spoon.
 */
public class SpoonCouplingService {
    
    private final ParserAnalyzer jdtAnalyzer;
    private Map<String, Map<String, Integer>> couplingMatrix;
    private Map<String, Map<String, Double>> couplingWeights;
    private int totalCalls;
    
    public SpoonCouplingService(ParserAnalyzer jdtAnalyzer) {
        this.jdtAnalyzer = jdtAnalyzer;
    }
    
    /**
     * Calcule la matrice de couplage en utilisant Spoon.
     */
    public void calculateCoupling() {
        // Utiliser le graphe d'appel de JDT
        Map<String, Set<String>> callGraph = jdtAnalyzer.getCallGraph();
        
        // Calculer la matrice de couplage inter-classes
        this.couplingMatrix = countInterClassCalls(callGraph);
        this.totalCalls = totalInterClassEdges(couplingMatrix);
        this.couplingWeights = normalizeToCouplingWeights(couplingMatrix, totalCalls);
    }
    
    /**
     * Compte les appels inter-classes en utilisant Spoon pour la résolution des types.
     */
    private Map<String, Map<String, Integer>> countInterClassCalls(Map<String, Set<String>> callGraph) {
        Map<String, Map<String, Integer>> matrix = new HashMap<>();
        Set<String> uniqueCalls = new HashSet<>();
        
        for (String caller : callGraph.keySet()) {
            String callerClass = extractClassName(caller);
            if (callerClass == null) continue;
            
            for (String callee : callGraph.get(caller)) {
                String calleeClass = extractClassName(callee);
                if (calleeClass == null || callerClass.equals(calleeClass)) continue;
                
                // Utiliser Spoon pour résoudre le type exact
                String resolvedCalleeClass = resolveClassWithSpoon(callee);
                if (resolvedCalleeClass != null && !callerClass.equals(resolvedCalleeClass)) {
                    String uniqueCall = callerClass + " -> " + resolvedCalleeClass + " -> " + extractMethodName(callee);
                    
                    if (uniqueCalls.add(uniqueCall)) {
                        matrix.computeIfAbsent(callerClass, k -> new HashMap<>())
                              .merge(resolvedCalleeClass, 1, Integer::sum);
                    }
                }
            }
        }
        
        return matrix;
    }
    
    /**
     * Utilise Spoon pour résoudre le type exact d'une méthode.
     */
    private String resolveClassWithSpoon(String methodSignature) {
        try {
            // Créer un launcher Spoon pour analyser le projet
            Launcher launcher = new Launcher();
            launcher.addInputResource(jdtAnalyzer.getSourcePath());
            launcher.getEnvironment().setComplianceLevel(17);
            launcher.getEnvironment().setNoClasspath(false);
            launcher.buildModel();
            
            CtModel model = launcher.getModel();
            
            // Chercher la méthode dans le modèle Spoon
            String methodName = extractMethodName(methodSignature);
            String className = extractClassName(methodSignature);
            
            if (className != null) {
                // Utiliser l'API Spoon pour obtenir le type
                for (CtType<?> type : model.getAllTypes()) {
                    if (type.getQualifiedName().equals(className)) {
                        for (CtMethod<?> method : type.getMethods()) {
                            if (method.getSimpleName().equals(methodName)) {
                                return type.getQualifiedName();
                            }
                        }
                    }
                }
            }
            
        } catch (Exception e) {
            // En cas d'erreur, retourner la classe extraite du nom
            return extractClassName(methodSignature);
        }
        
        return extractClassName(methodSignature);
    }
    
    /**
     * Extrait le nom de classe d'une signature de méthode.
     */
    private String extractClassName(String methodSignature) {
        int lastDot = methodSignature.lastIndexOf('.');
        if (lastDot > 0) {
            return methodSignature.substring(0, lastDot);
        }
        return null;
    }
    
    /**
     * Extrait le nom de méthode d'une signature.
     */
    private String extractMethodName(String methodSignature) {
        int lastDot = methodSignature.lastIndexOf('.');
        if (lastDot >= 0 && lastDot < methodSignature.length() - 1) {
            return methodSignature.substring(lastDot + 1);
        }
        return methodSignature;
    }
    
    /**
     * Calcule le nombre total d'arêtes inter-classes.
     */
    private int totalInterClassEdges(Map<String, Map<String, Integer>> matrix) {
        return matrix.values().stream()
                .flatMap(map -> map.values().stream())
                .mapToInt(Integer::intValue)
                .sum();
    }
    
    /**
     * Normalise la matrice de couplage en poids de couplage.
     */
    private Map<String, Map<String, Double>> normalizeToCouplingWeights(
            Map<String, Map<String, Integer>> matrix, int totalCalls) {
        
        Map<String, Map<String, Double>> weights = new HashMap<>();
        
        for (String caller : matrix.keySet()) {
            Map<String, Double> callerWeights = new HashMap<>();
            for (String callee : matrix.get(caller).keySet()) {
                int calls = matrix.get(caller).get(callee);
                double weight = totalCalls > 0 ? (double) calls / totalCalls : 0.0;
                callerWeights.put(callee, weight);
            }
            weights.put(caller, callerWeights);
        }
        
        return weights;
    }
    
    /**
     * Génère un rapport de couplage.
     */
    public String generateCouplingReport() {
        StringBuilder report = new StringBuilder();
        report.append("=== RAPPORT DE COUPLAGE (SPOON) ===\n\n");
        
        // Statistiques générales
        int totalClasses = couplingMatrix.size();
        report.append("INFORMATIONS GÉNÉRALES:\n");
        report.append("- Nombre de classes: ").append(totalClasses).append("\n");
        report.append("- Total des appels inter-classes: ").append(totalCalls).append("\n");
        report.append("- Couplage moyen: ").append(String.format("%.4f", 
            totalCalls > 0 ? (double) totalCalls / (totalClasses * (totalClasses - 1)) : 0.0)).append("\n\n");
        
        // Top 5 couplages les plus forts
        report.append("TOP 5 COUPLAGES LES PLUS FORTS:\n");
        List<Map.Entry<String, Map.Entry<String, Integer>>> sortedCouplings = new ArrayList<>();
        
        for (String caller : couplingMatrix.keySet()) {
            for (Map.Entry<String, Integer> entry : couplingMatrix.get(caller).entrySet()) {
                sortedCouplings.add(new AbstractMap.SimpleEntry<>(caller, entry));
            }
        }
        
        sortedCouplings.sort((a, b) -> b.getValue().getValue() - a.getValue().getValue());
        
        for (int i = 0; i < Math.min(5, sortedCouplings.size()); i++) {
            Map.Entry<String, Map.Entry<String, Integer>> entry = sortedCouplings.get(i);
            String caller = entry.getKey();
            String callee = entry.getValue().getKey();
            int calls = entry.getValue().getValue();
            double weight = couplingWeights.get(caller).get(callee);
            
            report.append(String.format("%d. %s -> %s: %d appels (%.4f)\n", 
                i + 1, caller, callee, calls, weight));
        }
        
        return report.toString();
    }
    
    // Getters
    public Map<String, Map<String, Integer>> getCouplingMatrix() {
        return couplingMatrix;
    }
    
    public Map<String, Map<String, Double>> getCouplingWeights() {
        return couplingWeights;
    }
    
    public int getTotalCalls() {
        return totalCalls;
    }
}
