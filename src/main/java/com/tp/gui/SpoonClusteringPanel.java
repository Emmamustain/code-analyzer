package com.tp.gui;

import com.tp.spoon.SpoonCouplingService;
import com.tp.spoon.SpoonClusteringService;
import com.tp.analysis.ClusteringService;
import com.tp.analysis.ModuleIdentifier;
import com.tp.ParserAnalyzer;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;

/**
 * Panel pour l'affichage et la configuration du clustering hiérarchique avec Spoon.
 */
public class SpoonClusteringPanel extends JPanel {
    
    private JTextArea resultArea;
    private JTextField minCouplingField;
    private JButton clusterButton;
    private JButton exportButton;
    private SpoonClusteringService clusteringService;
    private ClusteringService.ClusteringResult currentResult;
    private ParserAnalyzer analyzer;

    public SpoonClusteringPanel() {
        initializeComponents();
        layoutComponents();
        setupEventHandlers();
    }
    
    private void initializeComponents() {
        // Zone de résultats
        resultArea = new JTextArea(20, 60);
        resultArea.setEditable(false);
        resultArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        resultArea.setText("Sélectionnez un projet et cliquez sur 'Analyser le Clustering (Spoon)' pour commencer.");
        
        // Champ pour le couplage minimum
        minCouplingField = new JTextField("0.1", 10);
        minCouplingField.setToolTipText("Couplage minimum requis pour qu'un groupe de classes forme un module");
        
        // Boutons
        clusterButton = new JButton("Analyser le Clustering (Spoon)");
        exportButton = new JButton("Exporter les Modules (CSV)");
        exportButton.setEnabled(false);
    }
    
    private void layoutComponents() {
        setLayout(new BorderLayout());
        
        // Panel de contrôle en haut
        JPanel controlPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        controlPanel.setBorder(new TitledBorder("Paramètres de Clustering Spoon"));
        controlPanel.add(new JLabel("Couplage Minimum (CP):"));
        controlPanel.add(minCouplingField);
        controlPanel.add(clusterButton);
        controlPanel.add(exportButton);
        
        add(controlPanel, BorderLayout.NORTH);
        add(new JScrollPane(resultArea), BorderLayout.CENTER);
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
     * Effectue le clustering sur le projet analysé avec Spoon.
     */
    public void performClustering(ParserAnalyzer analyzer) {
        try {
            // Enregistrer l'analyzer pour permettre les relances
            this.analyzer = analyzer;
            
            // Créer le service de couplage Spoon
            SpoonCouplingService couplingService = new SpoonCouplingService(analyzer);
            couplingService.calculateCoupling();
            
            // Créer le service de clustering Spoon
            clusteringService = new SpoonClusteringService(couplingService);
            
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
                "Erreur lors du clustering Spoon: " + ex.getMessage(), 
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
     * Affiche les résultats du clustering Spoon.
     */
    private void displayResults() {
        if (currentResult == null) {
            resultArea.setText("Aucun résultat de clustering Spoon à afficher.");
            return;
        }
        
        StringBuilder report = new StringBuilder();
        report.append(clusteringService.generateTextReport());
        
        // Ajouter la vérification des contraintes
        report.append("\n=== VÉRIFICATION DES CONTRAINTES ===\n");
        int totalClasses = extractTotalClasses(currentResult);
        int maxModules = totalClasses / 2;
        List<ModuleIdentifier.Module> modules = currentResult.getModules();
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
     * Exporte les modules identifiés vers un fichier CSV.
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
            fileChooser.setSelectedFile(new java.io.File("clustering_modules_spoon.csv"));
            fileChooser.setDialogTitle("Exporter les modules Spoon en CSV");
            
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
        return result.getDendrogram().getClassCount();
    }
    
    /**
     * Réinitialise le panel.
     */
    public void reset() {
        resultArea.setText("Sélectionnez un projet et cliquez sur 'Analyser le Clustering (Spoon)' pour commencer.");
        exportButton.setEnabled(false);
        currentResult = null;
        clusteringService = null;
        analyzer = null;
    }
}
