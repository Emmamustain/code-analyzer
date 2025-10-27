package com.tp.gui;

import com.tp.analysis.*;
import com.tp.ParserAnalyzer;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;

/**
 * Panel pour l'affichage et la configuration du clustering hiérarchique.
 */
public class ClusteringPanel extends JPanel {
    
    private JTextArea resultArea;
    private JTextField minCouplingField;
    private JButton clusterButton;
    private JButton exportButton;
    private ClusteringService clusteringService;
    private ClusteringService.ClusteringResult currentResult;
    private ParserAnalyzer analyzer;
    
    public ClusteringPanel() {
        initializeComponents();
        layoutComponents();
        setupEventHandlers();
    }
    
    private void initializeComponents() {
        // Zone de résultats
        resultArea = new JTextArea(20, 60);
        resultArea.setEditable(false);
        resultArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        resultArea.setText("Sélectionnez un projet et cliquez sur 'Analyser le Clustering' pour commencer.");
        
        // Champ pour le couplage minimum
        minCouplingField = new JTextField("0.1", 10);
        minCouplingField.setToolTipText("Couplage minimum requis pour qu'un groupe de classes forme un module");
        
        // Boutons
        clusterButton = new JButton("Analyser le Clustering");
        exportButton = new JButton("Exporter CSV");
        exportButton.setEnabled(false);
    }
    
    private void layoutComponents() {
        setLayout(new BorderLayout());
        
        // Panel de configuration
        JPanel configPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        configPanel.setBorder(new TitledBorder("Configuration du Clustering"));
        
        configPanel.add(new JLabel("Couplage minimum (CP):"));
        configPanel.add(minCouplingField);
        configPanel.add(clusterButton);
        configPanel.add(exportButton);
        
        // Panel principal
        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.add(configPanel, BorderLayout.NORTH);
        mainPanel.add(new JScrollPane(resultArea), BorderLayout.CENTER);
        
        add(mainPanel, BorderLayout.CENTER);
    }
    
    private void setupEventHandlers() {
        clusterButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                performClustering();
            }
        });
        
        exportButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                exportToCSV();
            }
        });
    }
    
    /**
     * Effectue le clustering sur le projet analysé.
     */
    public void performClustering(ParserAnalyzer analyzer) {
        try {
            // Enregistrer l'analyzer pour permettre les relances
            this.analyzer = analyzer;
            
            // Obtenir les données de couplage
            Map<String, Set<String>> callGraph = analyzer.getCallGraph();
            Map<String, Map<String, Integer>> couplingMatrix = CouplingService.countInterClassCalls(callGraph);
            int totalCalls = CouplingService.totalInterClassEdges(couplingMatrix);
            Map<String, Map<String, Double>> couplingWeights = CouplingService.normalizeToCouplingWeights(couplingMatrix, totalCalls);
            
            // Créer le service de clustering
            clusteringService = new ClusteringService(couplingMatrix, couplingWeights);
            
            // Obtenir le couplage minimum
            double minCoupling;
            try {
                minCoupling = Double.parseDouble(minCouplingField.getText());
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(this, 
                    "Valeur de couplage minimum invalide. Utilisation de 0.1 par défaut.", 
                    "Erreur", JOptionPane.WARNING_MESSAGE);
                minCoupling = 0.1;
                minCouplingField.setText("0.1");
            }
            
            // Effectuer le clustering
            currentResult = clusteringService.performCompleteClustering(minCoupling);
            
            // Afficher les résultats
            displayResults();
            exportButton.setEnabled(true);
            
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, 
                "Erreur lors du clustering: " + ex.getMessage(), 
                "Erreur", JOptionPane.ERROR_MESSAGE);
            ex.printStackTrace();
        }
    }
    
    /**
     * Effectue le clustering (méthode publique pour compatibilité).
     */
    public void performClustering() {
        if (analyzer == null) {
            JOptionPane.showMessageDialog(this, 
                "Veuillez d'abord analyser un projet.", 
                "Erreur", JOptionPane.WARNING_MESSAGE);
            return;
        }
        performClustering(analyzer);
    }
    
    /**
     * Affiche les résultats du clustering.
     */
    private void displayResults() {
        if (currentResult == null) {
            return;
        }
        
        StringBuilder report = new StringBuilder();
        report.append("=== RÉSULTATS DU CLUSTERING HIÉRARCHIQUE ===\n\n");
        
        // Informations générales
        report.append("INFORMATIONS GÉNÉRALES:\n");
        report.append("- Nombre total de classes: ").append(currentResult.getDendrogram().getClassCount()).append("\n");
        report.append("- Nombre de modules identifiés: ").append(currentResult.getModules().size()).append("\n");
        report.append("- Couplage minimum utilisé: ").append(minCouplingField.getText()).append("\n\n");
        
        // Détails des modules
        report.append("MODULES IDENTIFIÉS:\n");
        List<ModuleIdentifier.Module> modules = currentResult.getModules();
        for (int i = 0; i < modules.size(); i++) {
            ModuleIdentifier.Module module = modules.get(i);
            report.append(String.format("%d. %s\n", i + 1, module));
        }
        
        report.append("\n=== STATISTIQUES DÉTAILLÉES ===\n");
        
        // Statistiques par module
        for (int i = 0; i < modules.size(); i++) {
            ModuleIdentifier.Module module = modules.get(i);
            report.append(String.format("\nModule %d: %s\n", i + 1, module.getId()));
            report.append(String.format("  - Nombre de classes: %d\n", module.getClassCount()));
            report.append(String.format("  - Couplage moyen: %.3f\n", module.getAverageCoupling()));
            report.append(String.format("  - Classes: %s\n", module.getClasses()));
        }
        
        // Contraintes respectées
        report.append("\n=== VÉRIFICATION DES CONTRAINTES ===\n");
        int totalClasses = extractTotalClasses(currentResult);
        int maxModules = totalClasses / 2;
        boolean constraintM2 = modules.size() <= maxModules;
        boolean constraintCP = modules.stream().allMatch(m -> m.getAverageCoupling() >= Double.parseDouble(minCouplingField.getText()));
        
                    report.append(String.format("- Contrainte M/2: %d modules maximum (actuel: %d) %s\n",
                                maxModules, modules.size(),
                                constraintM2 ? "OK" : "ECHEC"));
                    report.append(String.format("- Couplage minimum: Tous les modules respectent CP=%s %s\n",
                                minCouplingField.getText(), constraintCP ? "OK" : "ECHEC"));
        
        resultArea.setText(report.toString());
    }
    
    /**
     * Exporte les résultats en CSV.
     */
    private void exportToCSV() {
        if (currentResult == null) {
            JOptionPane.showMessageDialog(this, 
                "Aucun résultat à exporter. Effectuez d'abord le clustering.", 
                "Erreur", JOptionPane.WARNING_MESSAGE);
            return;
        }
        
        try {
            String csvContent = clusteringService.generateCSVReport();
            
            // Ouvrir une boîte de dialogue pour sauvegarder
            JFileChooser fileChooser = new JFileChooser();
            fileChooser.setSelectedFile(new java.io.File("clustering_modules.csv"));
            fileChooser.setDialogTitle("Exporter les modules en CSV");
            
            if (fileChooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
                java.io.File file = fileChooser.getSelectedFile();
                java.nio.file.Files.write(file.toPath(), csvContent.getBytes());
                JOptionPane.showMessageDialog(this, 
                    "Fichier CSV exporté avec succès: " + file.getName(), 
                    "Succès", JOptionPane.INFORMATION_MESSAGE);
            }
            
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, 
                "Erreur lors de l'export CSV: " + ex.getMessage(), 
                "Erreur", JOptionPane.ERROR_MESSAGE);
            ex.printStackTrace();
        }
    }
    
    /**
     * Extrait le nombre total de classes du résultat.
     */
    private int extractTotalClasses(ClusteringService.ClusteringResult result) {
        // Utiliser le nombre de classes du dendrogramme
        return result.getDendrogram().getClassCount();
    }
    
    /**
     * Réinitialise le panel.
     */
    public void reset() {
        resultArea.setText("Sélectionnez un projet et cliquez sur 'Analyser le Clustering' pour commencer.");
        exportButton.setEnabled(false);
        currentResult = null;
        clusteringService = null;
        analyzer = null;
    }
}
