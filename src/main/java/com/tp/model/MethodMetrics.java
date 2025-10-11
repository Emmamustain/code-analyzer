package com.tp.model;

/**
 * Modèle pour stocker les métriques d'une méthode.
 * Contient le nom, le nombre de lignes de code (LOC) et le nombre de paramètres.
 */
public class MethodMetrics {
    private String name;
    private int lineCount;  // Nombre de lignes de code (LOC)
    private int paramCount; // Nombre de paramètres

    public MethodMetrics(String name, int lineCount, int paramCount) {
        this.name = name;
        this.lineCount = lineCount;
        this.paramCount = paramCount;
    }

    public String getName() { return name; }
    public int getLineCount() { return lineCount; }
    public int getParamCount() { return paramCount; }

    /**
     * Format d'affichage lisible pour les statistiques.
     * Ex: "printReport [LOC=11, params=0]"
     */
    @Override
    public String toString() {
        return name + " [LOC=" + lineCount + ", params=" + paramCount + "]";
    }
}