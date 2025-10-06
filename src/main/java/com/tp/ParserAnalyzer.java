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

public class ParserAnalyzer {
    private final String sourcePath;
    private final List<ClassMetrics> classes = new ArrayList<>();
    private final Map<String, Set<String>> callGraph = new HashMap<>();
   
    public ParserAnalyzer(String sourcePath) {
        this.sourcePath = sourcePath;
    }
    public Map<String, Set<String>> getCallGraph() {
        return callGraph;
    }

    // Entry point
    public void analyze() throws Exception {
        List<File> files = listJavaFilesForFolder(new File(sourcePath));
        for (File f : files) {
            String content = Files.readString(f.toPath());
            CompilationUnit cu = createCompilationUnit(content.toCharArray());
            collectMetrics(cu);
        }
    }

    // ==================================================
    // Collect metrics (attributes, methods, LOC, params)
    // and build the call graph
    // ==================================================
    private void collectMetrics(CompilationUnit cu) {
        PackageDeclaration pkg = cu.getPackage();
        String packageName = (pkg != null) ? pkg.getName().getFullyQualifiedName() : "";

        TypeDeclarationVisitor typeVisitor = new TypeDeclarationVisitor();
        cu.accept(typeVisitor);

        for (TypeDeclaration type : typeVisitor.getTypes()) {
            ClassMetrics cm = new ClassMetrics(packageName, type.getName().toString());

            // Fields (attributes)
            int attrCount = type.getFields().length;
            cm.setAttributeCount(attrCount);

            // Methods
            for (MethodDeclaration method : type.getMethods()) {
                int start = cu.getLineNumber(method.getStartPosition());
                int end = cu.getLineNumber(method.getStartPosition() + method.getLength());
                int loc = (method.getBody() == null) ? 0 : (end - start + 1);
                int params = method.parameters().size();

                MethodMetrics mm = new MethodMetrics(method.getName().toString(), loc, params);
                cm.addMethod(mm);

                // ================================
                // 🆕 Build Call Graph (Exercice 2.1)
                // ================================
                MethodInvocationVisitor invVisitor = new MethodInvocationVisitor();
                method.accept(invVisitor);

                String caller = cm.getFullName() + "." + method.getName();
                callGraph.putIfAbsent(caller, new HashSet<>());

                // Find all normal method invocations
                for (MethodInvocation inv : invVisitor.getMethods()) {
                    String callee = inv.getName().toString();
                    // Try to resolve class if possible
                    IMethodBinding binding = inv.resolveMethodBinding();
                    if (binding != null && binding.getDeclaringClass() != null) {
                        callee = binding.getDeclaringClass().getQualifiedName() + "." + binding.getName();
                    }
                    callGraph.get(caller).add(callee);
                }

                // Handle super method invocations
                for (SuperMethodInvocation superInv : invVisitor.getSuperMethods()) {
                    String callee = "super." + superInv.getName();
                    callGraph.get(caller).add(callee);
                }
            }
            classes.add(cm);
        }
    }

    // ==================================================
    // Create AST
    // ==================================================
    private CompilationUnit createCompilationUnit(char[] content) {
        ASTParser parser = ASTParser.newParser(AST.JLS17);
        parser.setSource(content);
        parser.setKind(ASTParser.K_COMPILATION_UNIT);
        parser.setResolveBindings(true);
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

    // ==================================================
    // STATISTICS (Ex. 1.1)
    // ==================================================
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

        // 8. Top 10% by method count
        List<ClassMetrics> topByMethods = topPercent(classes, Comparator.comparingInt(ClassMetrics::getMethodCount).reversed(), 0.1);
        System.out.println("8. Top 10% classes (most methods): " + names(topByMethods));

        // 9. Top 10% by attribute count
        List<ClassMetrics> topByAttrs = topPercent(classes, Comparator.comparingInt(ClassMetrics::getAttributeCount).reversed(), 0.1);
        System.out.println("9. Top 10% classes (most attributes): " + names(topByAttrs));

        // 10. Intersection
        Set<String> both = topByMethods.stream().map(ClassMetrics::getFullName).collect(Collectors.toSet());
        both.retainAll(topByAttrs.stream().map(ClassMetrics::getFullName).collect(Collectors.toSet()));
        System.out.println("10. Classes in both groups: " + both);

        // 11. Classes with > X methods
        List<String> overX = classes.stream()
                .filter(c -> c.getMethodCount() > x)
                .map(ClassMetrics::getFullName)
                .toList();
        System.out.println("11. Classes with more than " + x + " methods: " + overX);

        // 12. Top 10% methods by LOC
        List<MethodMetrics> allMethods = classes.stream().flatMap(c -> c.getMethods().stream()).toList();
        List<MethodMetrics> topMethodsByLoc = topPercent(allMethods, Comparator.comparingInt(MethodMetrics::getLineCount).reversed(), 0.1);
        System.out.println("12. Top 10% methods by LOC: " + topMethodsByLoc);

        // 13. Max parameter count
        int maxParams = allMethods.stream().mapToInt(MethodMetrics::getParamCount).max().orElse(0);
        System.out.println("13. Max number of parameters in any method: " + maxParams);
    }

    // For GUI
    public String getStatisticsAsString(int x) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintStream ps = new PrintStream(baos);
        PrintStream oldOut = System.out;
        System.setOut(ps);
        printStatistics(x);
        System.setOut(oldOut);
        return baos.toString();
    }

    private <T> List<T> topPercent(List<T> list, Comparator<T> comparator, double percent) {
        if (list.isEmpty()) return List.of();
        int count = Math.max(1, (int) Math.ceil(list.size()*percent));
        return list.stream().sorted(comparator).limit(count).toList();
    }

    private List<String> names(List<ClassMetrics> list) {
        return list.stream().map(ClassMetrics::getFullName).toList();
    }

    // ==================================================
    // CALL GRAPH (Ex. 2.1)
    // ==================================================
    public void printCallGraph() {
        System.out.println("==== CALL GRAPH ====");
        for (var entry : callGraph.entrySet()) {
            System.out.println(entry.getKey() + " --> " + entry.getValue());
        }
    }

    // For GUI later, return as String instead of printing
    public String getCallGraphAsString() {
        StringBuilder sb = new StringBuilder("==== CALL GRAPH ====\n");
        for (var entry : callGraph.entrySet()) {
            sb.append(entry.getKey()).append(" --> ").append(entry.getValue()).append("\n");
        }
        return sb.toString();
    }
}

