package com.tp;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.tp.visitors.*;

import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.*;

/**
 * Classe utilitaire pour tester l'analyse de base de l'AST.
 * Affiche les informations sur les classes, méthodes, variables et invocations.
 */
public class Parser {

    public static final String projectSourcePath =
            "/Users/clstialdsn/eclipse-workspace/company-app/src/main/java";

    public static void main(String[] args) throws IOException {
        List<File> javaFiles = listJavaFilesForFolder(new File(projectSourcePath));

        for (File f : javaFiles) {
            String content = Files.readString(f.toPath());
            CompilationUnit cu = parse(content.toCharArray());

            System.out.println("==== Analyzing file: " + f.getName() + " ====");

            printClasses(cu);
            printMethods(cu);
            printVariables(cu);
            printMethodInvocations(cu);

            System.out.println();
        }
    }

    /**
     * Collecte récursivement tous les fichiers .java d'un dossier.
     */
    public static List<File> listJavaFilesForFolder(final File folder) {
        List<File> javaFiles = new ArrayList<>();
        for (File fileEntry : folder.listFiles()) {
            if (fileEntry.isDirectory()) {
                javaFiles.addAll(listJavaFilesForFolder(fileEntry));
            } else if (fileEntry.getName().endsWith(".java")) {
                javaFiles.add(fileEntry);
            }
        }
        return javaFiles;
    }

    /**
     * Crée un AST (Abstract Syntax Tree) à partir du code source Java.
     * Utilise Java 17 (JLS17) avec résolution des bindings pour permettre
     * l'analyse des types et des références entre classes.
     */
    private static CompilationUnit parse(char[] classSource) {
        ASTParser parser = ASTParser.newParser(AST.JLS17);
        parser.setKind(ASTParser.K_COMPILATION_UNIT);
        parser.setSource(classSource);
        
        // Crucial pour résoudre les types et construire un graphe d'appel précis
        parser.setResolveBindings(true);

        Map<String, String> options = JavaCore.getOptions();
        JavaCore.setComplianceOptions(JavaCore.VERSION_17, options);
        parser.setCompilerOptions(options);

        return (CompilationUnit) parser.createAST(null);
    }

    /**
     * Affiche les classes et leur hiérarchie d'héritage.
     */
    public static void printClasses(CompilationUnit cu) {
        TypeDeclarationVisitor visitor = new TypeDeclarationVisitor();
        cu.accept(visitor);
        for (TypeDeclaration t : visitor.getTypes()) {
            System.out.println("Class: " + t.getName());
            if (t.getSuperclassType() != null) {
                System.out.println("  Superclass: " + t.getSuperclassType());
            }
        }
    }

    public static void printMethods(CompilationUnit cu) {
        MethodDeclarationVisitor visitor = new MethodDeclarationVisitor();
        cu.accept(visitor);

        for (MethodDeclaration m : visitor.getMethods()) {
            System.out.println("  Method: " + m.getName()
                    + " Return type: " + m.getReturnType2());
        }
    }

    public static void printVariables(CompilationUnit cu) {
        MethodDeclarationVisitor methodVisitor = new MethodDeclarationVisitor();
        cu.accept(methodVisitor);

        for (MethodDeclaration m : methodVisitor.getMethods()) {
            VariableDeclarationFragmentVisitor varVisitor =
                    new VariableDeclarationFragmentVisitor();
            m.accept(varVisitor);

            for (VariableDeclarationFragment v : varVisitor.getVariables()) {
                System.out.println("    Variable: " + v.getName()
                        + " Init: " + v.getInitializer());
            }
        }
    }

    /**
     * Affiche les invocations de méthodes pour chaque méthode analysée.
     */
    public static void printMethodInvocations(CompilationUnit cu) {
        MethodDeclarationVisitor methodVisitor = new MethodDeclarationVisitor();
        cu.accept(methodVisitor);

        for (MethodDeclaration m : methodVisitor.getMethods()) {
            MethodInvocationVisitor invVisitor = new MethodInvocationVisitor();
            m.accept(invVisitor);

            for (MethodInvocation inv : invVisitor.getMethods()) {
                System.out.println("    Method " + m.getName()
                        + " calls method " + inv.getName());
            }
        }
    }
}