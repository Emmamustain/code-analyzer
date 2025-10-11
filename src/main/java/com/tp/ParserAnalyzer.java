package com.tp;

import com.tp.model.ClassMetrics;
import com.tp.model.MethodMetrics;
import com.tp.visitors.*;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.nio.file.Files;
import java.util.*;
import java.util.stream.Collectors;

import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.*;

/**
 * Analyseur principal pour le calcul des métriques et la construction du graphe d'appel.
 * Implémente les exercices 1.1 et 2.1 du TP.
 */
public class ParserAnalyzer {
    private final String sourcePath;
    
    // Stocke les métriques de chaque classe analysée
    private final List<ClassMetrics> classes = new ArrayList<>();
    
    // Graphe d'appel : méthode appelante -> ensemble des méthodes appelées
    private final Map<String, Set<String>> callGraph = new HashMap<>();
   
    public ParserAnalyzer(String sourcePath) {
        this.sourcePath = sourcePath;
    }
    
    public Map<String, Set<String>> getCallGraph() {
        return callGraph;
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
    }

    /**
     * Collecte les métriques (attributs, méthodes, LOC, paramètres)
     * et construit le graphe d'appel pour chaque classe.
     */
    private void collectMetrics(CompilationUnit cu) {
        PackageDeclaration pkg = cu.getPackage();
        String packageName = (pkg != null) ? pkg.getName().getFullyQualifiedName() : "";

        TypeDeclarationVisitor typeVisitor = new TypeDeclarationVisitor();
        cu.accept(typeVisitor);

        for (TypeDeclaration type : typeVisitor.getTypes()) {
            ClassMetrics cm = new ClassMetrics(packageName, type.getName().toString());

            // Compte les attributs déclarés dans la classe
            int attrCount = type.getFields().length;
            cm.setAttributeCount(attrCount);

            // Analyse chaque méthode
            for (MethodDeclaration method : type.getMethods()) {
                // Calcul des lignes de code (LOC) de la méthode
                int start = cu.getLineNumber(method.getStartPosition());
                int end = cu.getLineNumber(method.getStartPosition() + method.getLength());
                int loc = (method.getBody() == null) ? 0 : (end - start + 1);
                int params = method.parameters().size();

                MethodMetrics mm = new MethodMetrics(method.getName().toString(), loc, params);
                cm.addMethod(mm);

                // ================================
                // Construction du graphe d'appel (Exercice 2.1)
                // ================================
                MethodInvocationVisitor invVisitor = new MethodInvocationVisitor();
                method.accept(invVisitor);

                String caller = cm.getFullName() + "." + method.getName();
                callGraph.putIfAbsent(caller, new HashSet<>());

                // Traite les invocations normales de méthodes
                for (MethodInvocation inv : invVisitor.getMethods()) {
                    String callee = inv.getName().toString();
                    
                    // Résolution du binding pour obtenir le nom qualifié complet
                    // (crucial pour un graphe d'appel précis)
                    IMethodBinding binding = inv.resolveMethodBinding();
                    if (binding != null && binding.getDeclaringClass() != null) {
                        callee = binding.getDeclaringClass().getQualifiedName() + "." + binding.getName();
                    }
                    callGraph.get(caller).add(callee);
                }

                // Traite les appels à super (méthodes de la classe parente)
                for (SuperMethodInvocation superInv : invVisitor.getSuperMethods()) {
                    String callee = "super." + superInv.getName();
                    callGraph.get(caller).add(callee);
                }
            }
            classes.add(cm);
        }
    }

    /**
     * Crée un AST avec résolution des bindings activée.
     */
    private CompilationUnit createCompilationUnit(char[] content) {
        ASTParser parser = ASTParser.newParser(AST.JLS17);
        parser.setSource(content);
        parser.setKind(ASTParser.K_COMPILATION_UNIT);
        parser.setResolveBindings(true); // Essentiel pour le graphe d'appel
        Map<String, String> options = JavaCore.getOptions();
        JavaCore.setComplianceOptions(JavaCore.VERSION_17, options);
        parser.setCompilerOptions(options);
        return (CompilationUnit) parser.createAST(null);
    }

    private List<File> listJavaFilesForFolder(final File folder) {
        List<File> javaFiles = new ArrayList<>();
        for (File fileEntry : Objects.requireNonNull(folder.listFiles())) {
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
                .mapToInt(MethodMetrics::getLineCount).sum();

        System.out.println("1. Nombre de classes: " + totalClasses);
        System.out.println("2. Nombre de lignes de code: " + totalLoc);
        System.out.println("3. Nombre total de méthodes: " + totalMethods);
        System.out.println("4. Nombre total de packages: " + totalPackages);
        System.out.println("5. Moyenne méthodes par classe: " + (totalClasses==0?0:(double)totalMethods/totalClasses));
        System.out.println("6. Moyenne LOC par méthode: " + (totalMethods==0?0:(double)totalLoc/totalMethods));
        System.out.println("7. Moyenne attributs par classe: " + (totalClasses==0?0:(double)totalAttrs/totalClasses));

        // Top 10% des classes par nombre de méthodes
        List<ClassMetrics> topByMethods = topPercent(classes, Comparator.comparingInt(ClassMetrics::getMethodCount).reversed(), 0.1);
        System.out.println("8. Top 10% classes (most methods): " + names(topByMethods));

        // Top 10% des classes par nombre d'attributs
        List<ClassMetrics> topByAttrs = topPercent(classes, Comparator.comparingInt(ClassMetrics::getAttributeCount).reversed(), 0.1);
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
        List<MethodMetrics> allMethods = classes.stream().flatMap(c -> c.getMethods().stream()).toList();
        List<MethodMetrics> topMethodsByLoc = topPercent(allMethods, Comparator.comparingInt(MethodMetrics::getLineCount).reversed(), 0.1);
        System.out.println("12. Top 10% methods by LOC: " + topMethodsByLoc);

        // Nombre maximal de paramètres
        int maxParams = allMethods.stream().mapToInt(MethodMetrics::getParamCount).max().orElse(0);
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
        int count = Math.max(1, (int) Math.ceil(list.size()*percent));
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
}