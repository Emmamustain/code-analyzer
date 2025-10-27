package com.tp.spoon;

import com.tp.ParserAnalyzer;
import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.*;
import spoon.reflect.code.*;
import spoon.reflect.reference.*;
import spoon.reflect.visitor.CtScanner;
import java.util.*;

/**
 * Service de calcul de couplage utilisant Spoon.
 * Génère son propre graphe d'appel avec Spoon et calcule le couplage indépendamment de JDT.
 */
public class SpoonCouplingService {
    
    private final String projectSourcePath;
    private CtModel spoonModel;
    private Map<String, Set<String>> spoonCallGraph;
    private Map<String, Map<String, Integer>> couplingMatrix;
    private Map<String, Map<String, Double>> couplingWeights;
    private int totalCalls;
    
    public SpoonCouplingService(ParserAnalyzer jdtAnalyzer) {
        this.projectSourcePath = jdtAnalyzer.getSourcePath();
    }
    
    /**
     * Calcule la matrice de couplage en utilisant Spoon.
     */
    public void calculateCouplingMatrix() {
        System.out.println("=== CALCUL DU COUPLAGE SPOON ===");
        
        // Étape 1: Construire le modèle Spoon
        buildSpoonModel();
        
        // Étape 2: Générer le graphe d'appel avec Spoon
        generateSpoonCallGraph();
        
        // Étape 3: Calculer la matrice de couplage
        calculateCouplingMatrixFromCallGraph();
        
        System.out.println("Couplage Spoon calculé: " + totalCalls + " appels inter-classes");
    }
    
    /**
     * Construit le modèle Spoon à partir du code source.
     */
    private void buildSpoonModel() {
        try {
            Launcher launcher = new Launcher();
            launcher.addInputResource(projectSourcePath);
            launcher.getEnvironment().setComplianceLevel(17);
            launcher.getEnvironment().setNoClasspath(false);
            launcher.buildModel();
            
            this.spoonModel = launcher.getModel();
            System.out.println("Modèle Spoon construit avec " + spoonModel.getAllTypes().size() + " types");
            
        } catch (Exception e) {
            System.err.println("Erreur lors de la construction du modèle Spoon: " + e.getMessage());
            throw new RuntimeException("Impossible de construire le modèle Spoon", e);
        }
    }
    
    /**
     * Génère le graphe d'appel en utilisant Spoon.
     */
    private void generateSpoonCallGraph() {
        this.spoonCallGraph = new HashMap<>();
        
        // Parcourir tous les types dans le modèle Spoon
        for (CtType<?> type : spoonModel.getAllTypes()) {
            if (type instanceof CtClass) {
                CtClass<?> ctClass = (CtClass<?>) type;
                String className = type.getQualifiedName();
                
                // Filtrer les classes système dès le début
                if (isSystemClass(className)) {
                    continue;
                }
                
                // Parcourir toutes les méthodes de la classe
                for (CtMethod<?> method : ctClass.getMethods()) {
                    String methodSignature = className + "." + method.getSimpleName();
                    Set<String> calledMethods = new HashSet<>();
                    
                    // Parcourir le corps de la méthode pour trouver les appels
                    collectMethodCalls(method, calledMethods);
                    
                    // Ajouter la méthode même si elle n'a pas d'appels
                    spoonCallGraph.put(methodSignature, calledMethods);
                }
            }
        }
        
        System.out.println("Graphe d'appel Spoon généré avec " + spoonCallGraph.size() + " méthodes");
    }
    
    /**
     * Collecte les appels de méthodes dans le corps d'une méthode.
     */
    private void collectMethodCalls(CtMethod<?> method, Set<String> calledMethods) {
        if (method.getBody() == null) return;
        
        // Utiliser un scanner Spoon pour collecter tous les appels de méthodes
        method.getBody().accept(new CtScanner() {
            @Override
            public <T> void visitCtInvocation(spoon.reflect.code.CtInvocation<T> invocation) {
                CtExecutableReference<?> executable = invocation.getExecutable();
                if (executable != null) {
                    String methodSignature = buildMethodSignature(invocation, executable);
                    if (methodSignature != null) {
                        calledMethods.add(methodSignature);
                    }
                }
                super.visitCtInvocation(invocation);
            }
        });
    }
    
    /**
     * Construit la signature complète d'une méthode appelée.
     */
    private String buildMethodSignature(CtInvocation<?> invocation, CtExecutableReference<?> executable) {
        try {
            // Obtenir le type déclarant de la méthode
            CtTypeReference<?> declaringType = executable.getDeclaringType();
            if (declaringType != null) {
                String className = declaringType.getQualifiedName();
                
                // Filtrer les classes système
                if (isSystemClass(className)) {
                    return null;
                }
                
                String methodName = executable.getSimpleName();
                return className + "." + methodName;
            }
        } catch (Exception e) {
            // En cas d'erreur, essayer de construire la signature à partir de l'expression
            CtExpression<?> target = invocation.getTarget();
            if (target != null) {
                String targetType = getExpressionType(target);
                if (targetType != null && !isSystemClass(targetType)) {
                    return targetType + "." + executable.getSimpleName();
                }
            }
        }
        
        return null;
    }
    
    /**
     * Détermine le type d'une expression.
     */
    private String getExpressionType(CtExpression<?> expression) {
        if (expression instanceof CtFieldAccess) {
            CtFieldAccess<?> fieldAccess = (CtFieldAccess<?>) expression;
            CtTypeReference<?> type = fieldAccess.getVariable().getType();
            return type != null ? type.getQualifiedName() : null;
        }
        
        if (expression instanceof CtVariableAccess) {
            CtVariableAccess<?> varAccess = (CtVariableAccess<?>) expression;
            CtTypeReference<?> type = varAccess.getVariable().getType();
            return type != null ? type.getQualifiedName() : null;
        }
        
        if (expression instanceof CtThisAccess) {
            CtThisAccess<?> thisAccess = (CtThisAccess<?>) expression;
            CtTypeReference<?> type = thisAccess.getType();
            return type != null ? type.getQualifiedName() : null;
        }
        
        return null;
    }
    
    /**
     * Calcule la matrice de couplage à partir du graphe d'appel Spoon.
     */
    private void calculateCouplingMatrixFromCallGraph() {
        Map<String, Map<String, Integer>> matrix = new HashMap<>();
        Set<String> uniqueCalls = new HashSet<>();
        
        for (String caller : spoonCallGraph.keySet()) {
            String callerClass = extractClassName(caller);
            if (callerClass == null || isSystemClass(callerClass)) continue;
            
            for (String callee : spoonCallGraph.get(caller)) {
                String calleeClass = extractClassName(callee);
                if (calleeClass == null || callerClass.equals(calleeClass) || isSystemClass(calleeClass)) continue;
                
                // Créer une clé unique pour éviter les doublons
                String uniqueCall = callerClass + " -> " + calleeClass + " -> " + extractMethodName(callee);
                
                if (uniqueCalls.add(uniqueCall)) {
                    matrix.computeIfAbsent(callerClass, k -> new HashMap<>())
                          .merge(calleeClass, 1, Integer::sum);
                }
            }
        }
        
        this.couplingMatrix = matrix;
        this.totalCalls = totalInterClassEdges(couplingMatrix);
        this.couplingWeights = normalizeToCouplingWeights(couplingMatrix, totalCalls);
    }
    
    /**
     * Vérifie si une classe est une classe système (JDK).
     */
    private boolean isSystemClass(String className) {
        if (className == null) return false;
        
        // Classes système communes
        return className.startsWith("java.") ||
               className.startsWith("javax.") ||
               className.startsWith("sun.") ||
               className.startsWith("com.sun.") ||
               className.startsWith("jdk.");
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
