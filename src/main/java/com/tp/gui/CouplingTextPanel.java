package com.tp.gui;

import com.tp.analysis.CouplingService;
import com.tp.spoon.SpoonCouplingService;
import com.tp.ParserAnalyzer;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Composant pour afficher le résumé textuel du couplage entre classes.
 * Affiche les métriques de couplage sous forme de texte formaté.
 */
public class CouplingTextPanel extends JPanel {
    private JTextArea textArea;
    private JScrollPane scrollPane;
    private ParserAnalyzer analyzer;
    private JLabel statusLabel;
    
    public CouplingTextPanel() {
        setLayout(new BorderLayout());
        initializeComponents();
    }
    
    private void initializeComponents() {
        // Zone de texte pour afficher le résumé
        textArea = new JTextArea();
        textArea.setEditable(false);
        textArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        textArea.setBackground(Color.WHITE);
        textArea.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        
        // Scroll pane pour la zone de texte
        scrollPane = new JScrollPane(textArea);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setBorder(BorderFactory.createTitledBorder("Résumé du Couplage entre Classes"));
        
        // Panel de contrôle
        JPanel controlPanel = createControlPanel();
        
        // Panel de statut
        JPanel statusPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        statusLabel = new JLabel("Sélectionnez un projet et cliquez sur 'Analyser le Couplage' pour voir le résumé");
        statusPanel.add(statusLabel);
        
        // Assemblage des composants
        add(controlPanel, BorderLayout.NORTH);
        add(scrollPane, BorderLayout.CENTER);
        add(statusPanel, BorderLayout.SOUTH);
    }
    
    private JPanel createControlPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        
        // Bouton d'analyse
        JButton analyzeBtn = new JButton("Analyser le Couplage");
        analyzeBtn.addActionListener(e -> analyzeCoupling());
        panel.add(analyzeBtn);
        
        // Bouton de copie
        JButton copyBtn = new JButton("Copier le Résumé");
        copyBtn.addActionListener(e -> copyToClipboard());
        panel.add(copyBtn);
        
        // Bouton de sauvegarde
        JButton saveBtn = new JButton("Sauvegarder");
        saveBtn.addActionListener(e -> saveToFile());
        panel.add(saveBtn);
        
        return panel;
    }
    
    public void setAnalyzer(ParserAnalyzer analyzer) {
        this.analyzer = analyzer;
        if (analyzer != null) {
            statusLabel.setText("Analyseur prêt - Cliquez sur 'Analyser le Couplage'");
        }
    }
    
    private void analyzeCoupling() {
        if (analyzer == null) {
            JOptionPane.showMessageDialog(this, "Aucun analyseur disponible");
            return;
        }
        
        try {
            statusLabel.setText("Analyse du couplage en cours...");
            
            StringBuilder combinedSummary = new StringBuilder();
            
            // === ANALYSE JDT ===
            statusLabel.setText("Analyse JDT en cours...");
            CouplingService.resetPackageDetection();
            Map<String, Set<String>> callGraph = analyzer.getCallGraph();
            Map<String, Map<String, Integer>> jdtCounts = CouplingService.countInterClassCalls(callGraph);
            int jdtTotal = CouplingService.totalInterClassEdges(jdtCounts);
            Map<String, Map<String, Double>> jdtWeights = CouplingService.normalizeToCouplingWeights(jdtCounts, jdtTotal);
            
            String jdtSummary = generateCouplingSummary(jdtWeights, jdtCounts, jdtTotal, "JDT");
            combinedSummary.append(jdtSummary);
            
            // === ANALYSE SPOON ===
            statusLabel.setText("Analyse Spoon en cours...");
            SpoonCouplingService spoonService = new SpoonCouplingService(analyzer);
            spoonService.calculateCouplingMatrix();
            Map<String, Map<String, Integer>> spoonCounts = spoonService.getCouplingMatrix();
            Map<String, Map<String, Double>> spoonWeights = spoonService.getCouplingWeights();
            int spoonTotal = spoonService.getTotalCalls();
            
            String spoonSummary = generateCouplingSummary(spoonWeights, spoonCounts, spoonTotal, "Spoon");
            combinedSummary.append("\n\n").append(spoonSummary);
            
            // === COMPARAISON ===
            combinedSummary.append("\n\n").append(generateComparisonSummary(jdtWeights, jdtCounts, jdtTotal, 
                                                                           spoonWeights, spoonCounts, spoonTotal));
            
            // Afficher le résumé combiné
            textArea.setText(combinedSummary.toString());
            textArea.setCaretPosition(0);
            
            statusLabel.setText(String.format("Résumé généré - JDT: %d connexions, Spoon: %d connexions", 
                jdtTotal, spoonTotal));
            
        } catch (Exception e) {
            e.printStackTrace();
            statusLabel.setText("Erreur lors de l'analyse: " + e.getMessage());
            JOptionPane.showMessageDialog(this, "Erreur lors de l'analyse: " + e.getMessage());
        }
    }
    
    private String generateCouplingSummary(Map<String, Map<String, Double>> weights,
                                         Map<String, Map<String, Integer>> counts,
                                         int totalEdges, String analyzerType) {
        StringBuilder sb = new StringBuilder();
        
        // En-tête
        sb.append("=== RÉSUMÉ COUPLAGE ENTRE CLASSES (").append(analyzerType).append(") ===\n\n");
        
        // Packages détectés
        Set<String> detectedPackages = CouplingService.getDetectedPackages();
        if (!detectedPackages.isEmpty()) {
            sb.append("Packages détectés : ");
            sb.append(String.join(", ", detectedPackages));
            sb.append("\n\n");
        }
        
        // Statistiques générales
        sb.append("Total inter-class calls = ").append(totalEdges).append("\n\n");
        
        // Liste des couplages triés par poids décroissant
        record CouplingPair(String source, String target, double weight, int count) {}
        List<CouplingPair> pairs = new ArrayList<>();
        
        for (var entry : weights.entrySet()) {
            String source = entry.getKey();
            for (var targetEntry : entry.getValue().entrySet()) {
                String target = targetEntry.getKey();
                double weight = targetEntry.getValue();
                int count = counts.get(source).get(target);
                pairs.add(new CouplingPair(source, target, weight, count));
            }
        }
        
        // Trier par poids décroissant
        pairs.sort((p1, p2) -> Double.compare(p2.weight, p1.weight));
        
        // Afficher les couplages
        sb.append("Couplages par ordre d'importance :\n");
        for (int i = 0; i < pairs.size(); i++) {
            CouplingPair pair = pairs.get(i);
            sb.append(String.format("%2d) %s -- %s : %.4f (%d appels)\n",
                i + 1, 
                getShortClassName(pair.source), 
                getShortClassName(pair.target),
                pair.weight,
                pair.count));
        }
        
        // Statistiques supplémentaires
        sb.append("\n=== STATISTIQUES DÉTAILLÉES ===\n");
        
        // Couplage moyen
        double avgCoupling = pairs.isEmpty() ? 0.0 : 
            pairs.stream().mapToDouble(p -> p.weight).average().orElse(0.0);
        sb.append(String.format("Couplage moyen : %.4f\n", avgCoupling));
        
        // Couplage maximum
        double maxCoupling = pairs.isEmpty() ? 0.0 : 
            pairs.stream().mapToDouble(p -> p.weight).max().orElse(0.0);
        sb.append(String.format("Couplage maximum : %.4f\n", maxCoupling));
        
        // Nombre de classes impliquées
        Set<String> allClasses = new java.util.HashSet<>();
        for (CouplingPair pair : pairs) {
            allClasses.add(pair.source);
            allClasses.add(pair.target);
        }
        sb.append(String.format("Classes impliquées : %d\n", allClasses.size()));
        
        // Top 3 des couplages les plus forts
        sb.append("\n=== TOP 3 COUPLAGES LES PLUS FORTS ===\n");
        for (int i = 0; i < Math.min(3, pairs.size()); i++) {
            CouplingPair pair = pairs.get(i);
            double percentage = pair.weight * 100;
            sb.append(String.format("%d. %s → %s : %.2f%% (%d appels)\n",
                i + 1,
                getShortClassName(pair.source),
                getShortClassName(pair.target),
                percentage,
                pair.count));
        }
        
        // Analyse des packages
        sb.append("\n=== ANALYSE PAR PACKAGE ===\n");
        Map<String, Integer> packageCoupling = new java.util.HashMap<>();
        Map<String, Integer> packageCount = new java.util.HashMap<>();
        
        for (CouplingPair pair : pairs) {
            String sourcePkg = getPackageName(pair.source);
            String targetPkg = getPackageName(pair.target);
            
            packageCoupling.merge(sourcePkg, pair.count, Integer::sum);
            packageCount.merge(sourcePkg, 1, Integer::sum);
            
            if (!sourcePkg.equals(targetPkg)) {
                packageCoupling.merge(targetPkg, pair.count, Integer::sum);
                packageCount.merge(targetPkg, 1, Integer::sum);
            }
        }
        
        for (var entry : packageCoupling.entrySet()) {
            String pkg = entry.getKey();
            int coupling = entry.getValue();
            int count = packageCount.getOrDefault(pkg, 0);
            sb.append(String.format("Package %s : %d appels (%d connexions)\n", 
                pkg, coupling, count));
        }
        
        return sb.toString();
    }
    
    private String getShortClassName(String fullClassName) {
        int lastDot = fullClassName.lastIndexOf('.');
        return lastDot == -1 ? fullClassName : fullClassName.substring(lastDot + 1);
    }
    
    private String getPackageName(String fullClassName) {
        int lastDot = fullClassName.lastIndexOf('.');
        if (lastDot == -1) return "default";
        
        String classPath = fullClassName.substring(0, lastDot);
        int secondLastDot = classPath.lastIndexOf('.');
        if (secondLastDot == -1) return classPath;
        
        return classPath.substring(0, secondLastDot);
    }
    
    private String generateComparisonSummary(Map<String, Map<String, Double>> jdtWeights,
                                           Map<String, Map<String, Integer>> jdtCounts,
                                           int jdtTotal,
                                           Map<String, Map<String, Double>> spoonWeights,
                                           Map<String, Map<String, Integer>> spoonCounts,
                                           int spoonTotal) {
        StringBuilder sb = new StringBuilder();
        
        sb.append("=== COMPARAISON JDT vs SPOON ===\n\n");
        
        // Statistiques générales
        sb.append("STATISTIQUES GÉNÉRALES:\n");
        sb.append(String.format("- JDT: %d connexions inter-classes\n", jdtTotal));
        sb.append(String.format("- Spoon: %d connexions inter-classes\n", spoonTotal));
        sb.append(String.format("- Différence: %+d connexions\n\n", spoonTotal - jdtTotal));
        
        // Analyse des différences
        int jdtClasses = jdtWeights.size();
        int spoonClasses = spoonWeights.size();
        sb.append("CLASSES DÉTECTÉES:\n");
        sb.append(String.format("- JDT: %d classes\n", jdtClasses));
        sb.append(String.format("- Spoon: %d classes\n", spoonClasses));
        sb.append(String.format("- Différence: %+d classes\n\n", spoonClasses - jdtClasses));
        
        // Couplages communs vs uniques
        Set<String> jdtPairs = new java.util.HashSet<>();
        Set<String> spoonPairs = new java.util.HashSet<>();
        
        for (var entry : jdtWeights.entrySet()) {
            for (String target : entry.getValue().keySet()) {
                jdtPairs.add(entry.getKey() + " -> " + target);
            }
        }
        
        for (var entry : spoonWeights.entrySet()) {
            for (String target : entry.getValue().keySet()) {
                spoonPairs.add(entry.getKey() + " -> " + target);
            }
        }
        
        Set<String> commonPairs = new java.util.HashSet<>(jdtPairs);
        commonPairs.retainAll(spoonPairs);
        
        Set<String> jdtOnly = new java.util.HashSet<>(jdtPairs);
        jdtOnly.removeAll(spoonPairs);
        
        Set<String> spoonOnly = new java.util.HashSet<>(spoonPairs);
        spoonOnly.removeAll(jdtPairs);
        
        sb.append("ANALYSE DES COUPLAGES:\n");
        sb.append(String.format("- Couplages communs: %d\n", commonPairs.size()));
        sb.append(String.format("- Uniques à JDT: %d\n", jdtOnly.size()));
        sb.append(String.format("- Uniques à Spoon: %d\n", spoonOnly.size()));
        
        if (!jdtOnly.isEmpty()) {
            sb.append("\nCOUPLAGES UNIQUES À JDT:\n");
            for (String pair : jdtOnly) {
                sb.append("  - ").append(pair).append("\n");
            }
        }
        
        if (!spoonOnly.isEmpty()) {
            sb.append("\nCOUPLAGES UNIQUES À SPOON:\n");
            for (String pair : spoonOnly) {
                sb.append("  - ").append(pair).append("\n");
            }
        }
        
        return sb.toString();
    }
    
    private void copyToClipboard() {
        String text = textArea.getText();
        if (text.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Aucun contenu à copier");
            return;
        }
        
        try {
            java.awt.datatransfer.StringSelection selection = 
                new java.awt.datatransfer.StringSelection(text);
            java.awt.datatransfer.Clipboard clipboard = 
                Toolkit.getDefaultToolkit().getSystemClipboard();
            clipboard.setContents(selection, null);
            
            JOptionPane.showMessageDialog(this, "Résumé copié dans le presse-papiers");
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Erreur lors de la copie: " + e.getMessage());
        }
    }
    
    private void saveToFile() {
        String text = textArea.getText();
        if (text.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Aucun contenu à sauvegarder");
            return;
        }
        
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Sauvegarder le résumé de couplage");
        chooser.setSelectedFile(new java.io.File("coupling_summary.txt"));
        
        if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            try {
                java.io.File file = chooser.getSelectedFile();
                java.nio.file.Files.write(file.toPath(), text.getBytes());
                JOptionPane.showMessageDialog(this, "Résumé sauvegardé: " + file.getName());
            } catch (Exception e) {
                JOptionPane.showMessageDialog(this, "Erreur lors de la sauvegarde: " + e.getMessage());
            }
        }
    }
}
