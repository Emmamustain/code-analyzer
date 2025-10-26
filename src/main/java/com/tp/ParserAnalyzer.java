package com.tp;

import com.tp.model.ClassMetrics;
import com.tp.model.MethodMetrics;
import com.tp.visitors.*;

import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.*;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.nio.file.Files;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Analyseur principal pour le calcul des métriques et la construction du graphe d'appel.
 * Implémente les exercices 1.1 et 2.1 du TP.
 *
 * Stratégie de construction du graphe d'appel :
 * 1) Si le binding JDT est disponible : ajouter callee fully-qualified "pkg.Class.method".
 * 2) Sinon, tenter d'inférer le callee à partir d'un index des méthodes du projet (simpleName -> 1 classe).
 * 3) Si toujours impossible : fallback simpleName (utile pour la GUI ; ignoré par le couplage).
 */
public class ParserAnalyzer {
  private final String sourcePath;

  // Stocke les métriques de chaque classe analysée
  private final List<ClassMetrics> classes = new ArrayList<>();

  // Graphe d'appel : méthode appelante (FQN) -> ensemble des méthodes appelées
  private final Map<String, Set<String>> callGraph = new HashMap<>();

  // Index project-wide : simple method name -> set of declaring classes (FQN "pkg.Class")
  // Sert à inférer des cibles quand le binding est indisponible.
  private final Map<String, Set<String>> methodIndex = new HashMap<>();
  
  // Toutes les classes analysées (pour l'inférence)
  private final Set<String> allAnalyzedClasses = new HashSet<>();
  
  // Map des variables locales : nom de variable -> type complet
  private final Map<String, String> localVariableTypes = new HashMap<>();
  
  // Map des appels sur variables locales : (caller, variable, method) -> type
  private final Map<String, Map<String, String>> localVariableCalls = new HashMap<>();
  
  // Liste des appels sur variables locales à traiter plus tard
  private final List<LocalVariableCallVisitor.LocalVariableCall> pendingLocalCalls = new ArrayList<>();

  public ParserAnalyzer(String sourcePath) {
    this.sourcePath = sourcePath;
  }

  public Map<String, Set<String>> getCallGraph() {
    return callGraph;
  }
  
  public String getSourcePath() {
    return sourcePath;
  }

  /**
   * Point d'entrée de l'analyse : parcourt tous les fichiers .java du projet.
   */
  public void analyze() throws Exception {
    List<File> files = listJavaFilesForFolder(new File(sourcePath));
    for (File f : files) {
      String content = Files.readString(f.toPath());
      CompilationUnit cu = createCompilationUnit(content.toCharArray());
      collectMetrics(cu);
    }
    
    // Post-traitement : résoudre les appels non résolus
    resolveUnresolvedCalls();
    
    // Optional debug:
    // debugCallGraph(20);
  }

  /**
   * Collecte les métriques (attributs, méthodes, LOC, paramètres)
   * et construit le graphe d'appel pour chaque classe.
   */
  private void collectMetrics(CompilationUnit cu) {
    PackageDeclaration pkg = cu.getPackage();
    String packageName = (pkg != null) ? pkg.getName().getFullyQualifiedName() : "";

    TypeDeclarationVisitor typeVisitor = new TypeDeclarationVisitor();
    LocalVariableTypeVisitor localVarVisitor = new LocalVariableTypeVisitor();
    cu.accept(typeVisitor);
    cu.accept(localVarVisitor);
    
    // Capturer les types des variables locales
    localVariableTypes.putAll(localVarVisitor.getVariableTypes());
    
    // Capturer les appels sur variables locales
    LocalVariableCallVisitor localCallVisitor = new LocalVariableCallVisitor(localVariableTypes);
    cu.accept(localCallVisitor);

    for (TypeDeclaration type : typeVisitor.getTypes()) {
      String className = packageName.isEmpty() ? type.getName().toString() : packageName + "." + type.getName().toString();
      allAnalyzedClasses.add(className);
      
      ClassMetrics cm = new ClassMetrics(packageName, type.getName().toString());

      // Compte les attributs déclarés dans la classe
      int attrCount = type.getFields().length;
      cm.setAttributeCount(attrCount);

      // Analyse chaque méthode déclarée
      for (MethodDeclaration method : type.getMethods()) {
        // Calcul des lignes de code (LOC) de la méthode
        int start = cu.getLineNumber(method.getStartPosition());
        int end = cu.getLineNumber(method.getStartPosition() + method.getLength());
        int loc = (method.getBody() == null) ? 0 : (end - start + 1);
        int params = method.parameters().size();

        MethodMetrics mm = new MethodMetrics(method.getName().toString(), loc, params);
        cm.addMethod(mm);

        // Enregistrer la méthode dans l'index (simpleName -> declaring class FQN)
        String simple = method.getName().toString();
        String declaringClassFqn = cm.getFullName(); // "pkg.Class"
        methodIndex.computeIfAbsent(simple, k -> new HashSet<>()).add(declaringClassFqn);

        // ================================
        // Construction du graphe d'appel
        // ================================
        MethodInvocationVisitor invVisitor = new MethodInvocationVisitor();
        method.accept(invVisitor);

        // Appelant fully-qualified: pkg.Class.method
        String caller = cm.getFullName() + "." + method.getName();
        callGraph.putIfAbsent(caller, new HashSet<>());

        // Invocations normales
        for (MethodInvocation inv : invVisitor.getMethods()) {
          String calleeFqn = resolveQualified(inv);
          if (calleeFqn == null) {
            // Essayer d'inférer via l'index si le nom est unique dans le projet
            String inferred = inferByIndex(inv.getName().toString());
            if (inferred != null) {
              calleeFqn = inferred;
            } else {
              // Laisser le nom simple pour résolution ultérieure
              calleeFqn = inv.getName().toString();
            }
          }
          callGraph.get(caller).add(calleeFqn);
        }

        // Appels à super
        for (SuperMethodInvocation superInv : invVisitor.getSuperMethods()) {
          String calleeFqn = resolveQualified(superInv);
          if (calleeFqn == null) {
            // Pas de bonne inférence fiable ici; garder un marqueur lisible
            calleeFqn = "super." + superInv.getName();
          }
          callGraph.get(caller).add(calleeFqn);
        }
      }
      classes.add(cm);
    }
    
    // Stocker les appels sur variables locales pour traitement ultérieur
    // (après que toutes les classes soient analysées)
    pendingLocalCalls.addAll(localCallVisitor.getLocalCalls());
  }

  /**
   * Si bindings OK: retourne "pkg.Class.method", sinon null.
   */
  private String resolveQualified(MethodInvocation inv) {
    IMethodBinding binding = inv.resolveMethodBinding();
    if (binding != null && binding.getDeclaringClass() != null) {
      String qClass = binding.getDeclaringClass().getQualifiedName();
      if (qClass != null && !qClass.isBlank()) {
        return qClass + "." + binding.getName();
      }
    }
    return null;
  }

  /**
   * Si bindings OK: retourne "pkg.Class.method", sinon null.
   */
  private String resolveQualified(SuperMethodInvocation inv) {
    IMethodBinding binding = inv.resolveMethodBinding();
    if (binding != null && binding.getDeclaringClass() != null) {
      String qClass = binding.getDeclaringClass().getQualifiedName();
      if (qClass != null && !qClass.isBlank()) {
        return qClass + "." + inv.getName().toString();
      }
    }
    return null;
  }

  /**
   * Infère "pkg.Class.method" à partir de l'index si le nom de méthode
   * est présent et associé à exactement UNE classe du projet.
   */
  private String inferByIndex(String simpleMethodName) {
    Set<String> classesForName = methodIndex.get(simpleMethodName);
    if (classesForName == null || classesForName.isEmpty()) return null;
    if (classesForName.size() == 1) {
      String onlyClass = classesForName.iterator().next(); // "pkg.Class"
      return onlyClass + "." + simpleMethodName;
    }
    // Ambigu (ex: add dans plusieurs classes) -> pas d'inférence
    return null;
  }

  /**
   * Infère la classe à partir de l'expression de l'appel de méthode.
   * Ex: car.getModel() -> utilise le type de la variable car
   */
  private String inferFromExpression(MethodInvocation inv) {
    Expression expression = inv.getExpression();
    if (expression == null) return null;
    
    // Si c'est un SimpleName (ex: car.getModel(), dev.addEmployee())
    if (expression instanceof SimpleName) {
      String variableName = ((SimpleName) expression).getIdentifier();
      
      // D'abord, vérifier si on connaît le type de cette variable locale
      if (localVariableTypes.containsKey(variableName)) {
        String variableType = localVariableTypes.get(variableName);
        // Résoudre le type complet si nécessaire
        String fullTypeName = resolveTypeName(variableType);
        if (fullTypeName != null) {
          String result = fullTypeName + "." + inv.getName().toString();
          return result;
        }
      }
      
      // Fallback : chercher une classe dont le nom court correspond à la variable
      Set<String> allClasses = new HashSet<>(allAnalyzedClasses);
      for (String fullClassName : allClasses) {
        String shortClassName = getShortClassName(fullClassName);
        // Comparaison insensible à la casse : car -> Car
        if (shortClassName.equalsIgnoreCase(variableName)) {
          return fullClassName + "." + inv.getName().toString();
        }
      }
    }
    
    return null;
  }
  
  /**
   * Extrait le nom court d'une classe à partir de son nom complet.
   */
  private String getShortClassName(String fullClassName) {
    int lastDot = fullClassName.lastIndexOf('.');
    return lastDot >= 0 ? fullClassName.substring(lastDot + 1) : fullClassName;
  }
  
  /**
   * Résout un nom de type simple vers son nom complet.
   * Ex: "Department" -> "com.company.model.Department"
   */
  private String resolveTypeName(String typeName) {
    // Si c'est déjà un nom complet, le retourner
    if (typeName.contains(".")) {
      return typeName;
    }
    
    // Chercher dans les classes analysées
    for (String fullClassName : allAnalyzedClasses) {
      String shortClassName = getShortClassName(fullClassName);
      if (shortClassName.equals(typeName)) {
        return fullClassName;
      }
    }
    
    // Fallback : chercher dans l'index des méthodes
    for (String methodName : methodIndex.keySet()) {
      for (String declaringClass : methodIndex.get(methodName)) {
        String shortClassName = getShortClassName(declaringClass);
        if (shortClassName.equals(typeName)) {
          return declaringClass;
        }
      }
    }
    
    // Fallback final : essayer de construire le nom complet
    // En supposant que le type est dans le même package que la classe courante
    return null;
  }
  
  /**
   * Extrait le nom de classe à partir d'un nom de méthode complet.
   * Ex: "automobile.Car.getModel" -> "automobile.Car"
   */
  private String extractClassName(String methodName) {
    if (methodName == null) return null;
    int lastDot = methodName.lastIndexOf('.');
    if (lastDot <= 0) return null;
    return methodName.substring(0, lastDot);
  }
  
  /**
   * Résout les appels non résolus après l'analyse complète.
   * Remplace les noms simples par des noms complets quand possible.
   */
  private void resolveUnresolvedCalls() {
    for (var entry : callGraph.entrySet()) {
      String caller = entry.getKey();
      Set<String> callees = entry.getValue();
      Set<String> resolvedCallees = new HashSet<>();
      
      for (String callee : callees) {
        // Si c'est un nom simple (sans point), essayer de le résoudre
        if (!callee.contains(".")) {
          String resolved = resolveSimpleName(callee);
          if (resolved != null) {
            resolvedCallees.add(resolved);
          } else {
            resolvedCallees.add(callee); // Garder le nom simple si pas de résolution
          }
        } else {
          resolvedCallees.add(callee); // Déjà résolu
        }
      }
      
      // Remplacer l'ensemble des appelés
      entry.setValue(resolvedCallees);
    }
    
    // Phase 2 : Traiter les appels sur variables locales
    processLocalVariableCalls();
  }
  
  /**
   * Traite les appels sur variables locales après que toutes les classes soient analysées.
   */
  private void processLocalVariableCalls() {
    for (LocalVariableCallVisitor.LocalVariableCall call : pendingLocalCalls) {
      // Tous les appels sont maintenant dans Main
      String callerClass = "com.company.Main";
      String caller = callerClass + "." + call.getMethod();
      
      // Résoudre le type de la variable
      String variableType = call.getVariableType();
      String fullTypeName = resolveTypeName(variableType);
        if (fullTypeName != null) {
          String fullMethodName = fullTypeName + "." + call.getMethodName();
          callGraph.computeIfAbsent(caller, k -> new HashSet<>()).add(fullMethodName);
        }
    }
  }
  
  /**
   * Résout un nom simple en cherchant dans toutes les classes analysées.
   * Ex: "getModel" -> "automobile.Car.getModel"
   * Ne résout PAS les méthodes de collections (add, size, etc.)
   * Ne résout PAS les méthodes ambiguës (qui existent dans plusieurs classes)
   */
  private String resolveSimpleName(String simpleName) {
    // Ne pas résoudre les méthodes communes des collections
    if (isCollectionMethod(simpleName)) {
      return null;
    }
    
    // Chercher dans toutes les classes analysées
    Set<String> possibleMatches = new HashSet<>();
    for (String className : allAnalyzedClasses) {
      String fullMethodName = className + "." + simpleName;
      // Vérifier si cette méthode existe dans le graphe d'appel
      if (callGraph.containsKey(fullMethodName)) {
        possibleMatches.add(fullMethodName);
      }
    }
    
    // Si la méthode existe dans plusieurs classes, ne pas la résoudre (ambiguë)
    if (possibleMatches.size() != 1) {
      return null;
    }
    
    return possibleMatches.iterator().next();
  }
  
  /**
   * Vérifie si un nom de méthode est une méthode commune des collections.
   * Ces méthodes ne doivent pas être résolues vers des classes du projet.
   */
  private boolean isCollectionMethod(String methodName) {
    return methodName.equals("add") || 
           methodName.equals("remove") || 
           methodName.equals("size") || 
           methodName.equals("isEmpty") || 
           methodName.equals("contains") ||
           methodName.equals("get") ||
           methodName.equals("set") ||
           methodName.equals("clear");
  }

  /**
   * Crée un AST avec résolution des bindings activée.
   */
  private CompilationUnit createCompilationUnit(char[] content) {
    ASTParser parser = ASTParser.newParser(AST.JLS17);
    parser.setSource(content);
    parser.setKind(ASTParser.K_COMPILATION_UNIT);
    parser.setResolveBindings(true); // Essentiel pour les FQNs (quand possible)
    Map<String, String> options = JavaCore.getOptions();
    JavaCore.setComplianceOptions(JavaCore.VERSION_17, options);
    parser.setCompilerOptions(options);
    return (CompilationUnit) parser.createAST(null);
  }

  private List<File> listJavaFilesForFolder(final File folder) {
    List<File> javaFiles = new ArrayList<>();
    File[] files = folder.listFiles();
    if (files == null) return javaFiles;
    for (File fileEntry : files) {
      if (fileEntry.isDirectory()) {
        javaFiles.addAll(listJavaFilesForFolder(fileEntry));
      } else if (fileEntry.getName().endsWith(".java")) {
        javaFiles.add(fileEntry);
      }
    }
    return javaFiles;
  }

  /**
   * Affiche toutes les statistiques demandées dans l'exercice 1.1.
   * @param x Seuil pour le point 11 (classes avec plus de X méthodes)
   */
  public void printStatistics(int x) {
    int totalClasses = classes.size();
    int totalMethods = classes.stream().mapToInt(ClassMetrics::getMethodCount).sum();
    int totalAttrs = classes.stream().mapToInt(ClassMetrics::getAttributeCount).sum();
    int totalPackages = (int) classes.stream().map(ClassMetrics::getPackageName).distinct().count();
    int totalLoc = classes.stream()
        .flatMap(c -> c.getMethods().stream())
        .mapToInt(MethodMetrics::getLineCount)
        .sum();

    System.out.println("1. Nombre de classes: " + totalClasses);
    System.out.println("2. Nombre de lignes de code: " + totalLoc);
    System.out.println("3. Nombre total de méthodes: " + totalMethods);
    System.out.println("4. Nombre total de packages: " + totalPackages);
    System.out.println("5. Moyenne méthodes par classe: " +
        (totalClasses == 0 ? 0 : (double) totalMethods / totalClasses));
    System.out.println("6. Moyenne LOC par méthode: " +
        (totalMethods == 0 ? 0 : (double) totalLoc / totalMethods));
    System.out.println("7. Moyenne attributs par classe: " +
        (totalClasses == 0 ? 0 : (double) totalAttrs / totalClasses));

    // Top 10% des classes par nombre de méthodes
    List<ClassMetrics> topByMethods =
        topPercent(classes, Comparator.comparingInt(ClassMetrics::getMethodCount).reversed(), 0.1);
    System.out.println("8. Top 10% classes (most methods): " + names(topByMethods));

    // Top 10% des classes par nombre d'attributs
    List<ClassMetrics> topByAttrs =
        topPercent(classes, Comparator.comparingInt(ClassMetrics::getAttributeCount).reversed(), 0.1);
    System.out.println("9. Top 10% classes (most attributes): " + names(topByAttrs));

    // Classes dans les deux catégories (intersection)
    Set<String> both = topByMethods.stream().map(ClassMetrics::getFullName).collect(Collectors.toSet());
    both.retainAll(topByAttrs.stream().map(ClassMetrics::getFullName).collect(Collectors.toSet()));
    System.out.println("10. Classes in both groups: " + both);

    // Classes avec plus de X méthodes
    List<String> overX = classes.stream()
        .filter(c -> c.getMethodCount() > x)
        .map(ClassMetrics::getFullName)
        .toList();
    System.out.println("11. Classes with more than " + x + " methods: " + overX);

    // Top 10% des méthodes par LOC
    List<MethodMetrics> allMethods =
        classes.stream().flatMap(c -> c.getMethods().stream()).toList();
    List<MethodMetrics> topMethodsByLoc =
        topPercent(allMethods, Comparator.comparingInt(MethodMetrics::getLineCount).reversed(), 0.1);
    System.out.println("12. Top 10% methods by LOC: " + topMethodsByLoc);

    // Nombre maximal de paramètres
    int maxParams =
        allMethods.stream().mapToInt(MethodMetrics::getParamCount).max().orElse(0);
    System.out.println("13. Max number of parameters in any method: " + maxParams);
  }

  /**
   * Retourne les statistiques sous forme de String (pour l'interface graphique).
   */
  public String getStatisticsAsString(int x) {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    PrintStream ps = new PrintStream(baos);
    PrintStream oldOut = System.out;
    System.setOut(ps);
    printStatistics(x);
    System.setOut(oldOut);
    return baos.toString();
  }

  /**
   * Retourne les X% premiers éléments selon le comparateur donné.
   * Garantit au moins 1 élément si la liste n'est pas vide.
   */
  private <T> List<T> topPercent(List<T> list, Comparator<T> comparator, double percent) {
    if (list.isEmpty()) return List.of();
    int count = Math.max(1, (int) Math.ceil(list.size() * percent));
    return list.stream().sorted(comparator).limit(count).toList();
  }

  private List<String> names(List<ClassMetrics> list) {
    return list.stream().map(ClassMetrics::getFullName).toList();
  }

  /**
   * Affiche le graphe d'appel (Exercice 2.1).
   */
  public void printCallGraph() {
    System.out.println("==== CALL GRAPH ====");
    for (var entry : callGraph.entrySet()) {
      System.out.println(entry.getKey() + " --> " + entry.getValue());
    }
  }

  /**
   * Retourne le graphe d'appel sous forme de String (pour l'interface graphique).
   */
  public String getCallGraphAsString() {
    StringBuilder sb = new StringBuilder("==== CALL GRAPH ====\n");
    for (var entry : callGraph.entrySet()) {
      sb.append(entry.getKey()).append(" --> ").append(entry.getValue()).append("\n");
    }
    return sb.toString();
  }

  // --- Debug facultatif ---
  @SuppressWarnings("unused")
  private void debugCallGraph(int limit) {
    int edges = callGraph.values().stream().mapToInt(Set::size).sum();
    System.out.println("DBG CallGraph nodes=" + callGraph.size() + " edges=" + edges);
    callGraph.entrySet().stream().limit(limit).forEach(en ->
        System.out.println("DBG " + en.getKey() + " -> " + en.getValue())
    );
  }
}