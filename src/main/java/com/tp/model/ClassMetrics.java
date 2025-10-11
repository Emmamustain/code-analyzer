package com.tp.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Modèle de données pour stocker les métriques d'une classe.
 * Contient le nom, le package, le nombre d'attributs et la liste des méthodes.
 */
public class ClassMetrics {
    private String packageName;
    private String name;
    private int attributeCount;
    private final List<MethodMetrics> methods = new ArrayList<>();

    public ClassMetrics(String packageName, String name) {
        this.packageName = packageName;
        this.name = name;
    }

    /**
     * Retourne le nom qualifié complet de la classe (package.nom).
     * Utile pour l'affichage et les comparaisons.
     */
    public String getFullName() { return packageName + "." + name; }
    public String getName() { return name; }
    public String getPackageName() { return packageName; }

    public void addMethod(MethodMetrics mm) { methods.add(mm); }
    public List<MethodMetrics> getMethods() { return methods; }

    public void setAttributeCount(int count) { this.attributeCount = count; }
    public int getAttributeCount() { return attributeCount; }

    public int getMethodCount() { return methods.size(); }
}