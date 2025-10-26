package com.tp.analysis;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Service pour calculer les métriques de couplage entre classes
 */
public class CouplingService {

  private static Set<String> projectPackages = new HashSet<>();
  
  /**
   * Compte les appels inter-classes à partir du graphe d'appel
   */
  public static Map<String, Map<String, Integer>> countInterClassCalls(
      Map<String, Set<String>> callGraph) {

    Map<String, Map<String, Integer>> counts = new HashMap<>();
    detectProjectPackages(callGraph);
    Set<String> uniqueCalls = new HashSet<>();

    for (var e : callGraph.entrySet()) {
      String callerClass = classOf(e.getKey(), callGraph);
      if (!isProjectClass(callerClass)) continue;

      for (String calleeMethod : e.getValue()) {
        String calleeClass = classOf(calleeMethod, callGraph);
        if (calleeClass == null) continue;
        if (!isProjectClass(calleeClass)) continue;
        if (callerClass.equals(calleeClass)) continue;

        String a = callerClass.compareTo(calleeClass) <= 0 ? callerClass : calleeClass;
        String b = callerClass.compareTo(calleeClass) <= 0 ? calleeClass : callerClass;
        
        String uniqueCall = a + " -> " + b + " -> " + calleeMethod;
        
        if (!uniqueCalls.contains(uniqueCall)) {
          uniqueCalls.add(uniqueCall);
          counts.computeIfAbsent(a, k -> new HashMap<>())
                .merge(b, 1, Integer::sum);
        }
      }
    }
    return counts;
  }

  /**
   * Calcule le nombre total d'arêtes inter-classes
   */
  public static int totalInterClassEdges(Map<String, Map<String, Integer>> counts) {
    int sum = 0;
    for (var m : counts.values()) {
      for (int v : m.values()) sum += v;
    }
    return sum;
  }

  /**
   * Normalise les comptes en poids de couplage
   */
  public static Map<String, Map<String, Double>> normalizeToCouplingWeights(
      Map<String, Map<String, Integer>> counts, int totalInterClassEdges) {

    Map<String, Map<String, Double>> weights = new HashMap<>();
    if (totalInterClassEdges <= 0) return weights;

    for (var aEntry : counts.entrySet()) {
      String a = aEntry.getKey();
      for (var bEntry : aEntry.getValue().entrySet()) {
        String b = bEntry.getKey();
        double w = bEntry.getValue() / (double) totalInterClassEdges;
        weights.computeIfAbsent(a, k -> new HashMap<>()).put(b, w);
      }
    }
    return weights;
  }

  private static String classOf(String fullMethod, Map<String, Set<String>> callGraph) {
    if (fullMethod == null) return null;
    int lastDot = fullMethod.lastIndexOf('.');
    if (lastDot <= 0) return null;
    
    String className = fullMethod.substring(0, lastDot);
    
    if (isSimpleClassName(className)) {
      for (String methodName : callGraph.keySet()) {
        String methodClassName = extractClassNameFromMethod(methodName);
        if (methodClassName != null) {
          String shortClassName = getShortClassName(methodClassName);
          if (shortClassName.equalsIgnoreCase(className)) {
            return methodClassName;
          }
        }
      }
    }
    
    return className;
  }
  
  private static boolean isSimpleClassName(String className) {
    return className != null && !className.contains(".");
  }
  
  private static String extractClassNameFromMethod(String methodName) {
    if (methodName == null) return null;
    int lastDot = methodName.lastIndexOf('.');
    if (lastDot <= 0) return null;
    return methodName.substring(0, lastDot);
  }
  
  private static String getShortClassName(String fullClassName) {
    if (fullClassName == null) return null;
    int lastDot = fullClassName.lastIndexOf('.');
    return lastDot >= 0 ? fullClassName.substring(lastDot + 1) : fullClassName;
  }

  private static void detectProjectPackages(Map<String, Set<String>> callGraph) {
    projectPackages.clear();
    
    for (var entry : callGraph.entrySet()) {
      String callerClass = classOf(entry.getKey(), callGraph);
      if (callerClass != null) {
        String packageName = getPackageName(callerClass);
        if (packageName != null && !packageName.isEmpty()) {
          projectPackages.add(packageName);
        }
      }
      
      for (String calleeMethod : entry.getValue()) {
        String calleeClass = classOf(calleeMethod, callGraph);
        if (calleeClass != null) {
          String packageName = getPackageName(calleeClass);
          if (packageName != null && !packageName.isEmpty()) {
            projectPackages.add(packageName);
          }
        }
      }
    }
  }
  
  private static String getPackageName(String fullClassName) {
    if (fullClassName == null) return null;
    int lastDot = fullClassName.lastIndexOf('.');
    if (lastDot <= 0) return null;
    return fullClassName.substring(0, lastDot);
  }
  
  private static boolean isProjectClass(String className) {
    if (className == null) return false;
    
    String packageName = getPackageName(className);
    if (packageName == null) return false;
    
    for (String projectPackage : projectPackages) {
      if (packageName.equals(projectPackage) || 
          packageName.startsWith(projectPackage + ".") ||
          projectPackage.startsWith(packageName + ".")) {
        return true;
      }
    }
    
    return false;
  }
  
  public static Set<String> getDetectedPackages() {
    return new HashSet<>(projectPackages);
  }
  
  public static void resetPackageDetection() {
    projectPackages.clear();
  }
  
}