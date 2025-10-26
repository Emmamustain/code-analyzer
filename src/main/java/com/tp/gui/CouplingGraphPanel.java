package com.tp.gui;

import com.tp.analysis.CouplingService;
import com.tp.spoon.SpoonCouplingService;
import com.tp.ParserAnalyzer;
import org.graphstream.graph.Graph;
import org.graphstream.graph.Node;
import org.graphstream.graph.Edge;
import org.graphstream.graph.implementations.SingleGraph;
import org.graphstream.ui.swing_viewer.SwingViewer;
import org.graphstream.ui.swing_viewer.ViewPanel;
import org.graphstream.ui.view.Viewer;
import org.graphstream.ui.layout.Layout;
import org.graphstream.ui.layout.springbox.implementations.SpringBox;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Composant pour afficher le graphe de couplage pondéré dans l'interface graphique.
 * Utilise GraphStream pour la visualisation interactive.
 */
public class CouplingGraphPanel extends JPanel {
    private ViewPanel viewPanel;
    private Graph graph;
    private Viewer viewer;
    private ParserAnalyzer analyzer;
    private JLabel statusLabel;
    private JSlider weightSlider;
    private JSlider nodeSlider;
    private JLabel weightLabel;
    private JLabel nodeLabel;
    private JRadioButton jdtRadioButton;
    private JRadioButton spoonRadioButton;
    private ButtonGroup analyzerGroup;
    private boolean useSpoon = false;
    
    public CouplingGraphPanel() {
        setLayout(new BorderLayout());
        initializeComponents();
    }
    
    private void initializeComponents() {
        // Panel de contrôle
        JPanel controlPanel = createControlPanel();
        add(controlPanel, BorderLayout.NORTH);
        
        // Panel de statut
        JPanel statusPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        statusLabel = new JLabel("Sélectionnez un projet pour analyser le couplage");
        statusPanel.add(statusLabel);
        add(statusPanel, BorderLayout.SOUTH);
        
        // Panel central pour le graphe
        JPanel graphPanel = new JPanel(new BorderLayout());
        graphPanel.setBorder(BorderFactory.createTitledBorder("Graphe de Couplage Pondéré"));
        add(graphPanel, BorderLayout.CENTER);
    }
    
    private JPanel createControlPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        
        // Sélection de l'analyseur
        JPanel analyzerPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        analyzerPanel.setBorder(BorderFactory.createTitledBorder("Analyseur"));
        
        jdtRadioButton = new JRadioButton("JDT", true);
        spoonRadioButton = new JRadioButton("Spoon", false);
        analyzerGroup = new ButtonGroup();
        analyzerGroup.add(jdtRadioButton);
        analyzerGroup.add(spoonRadioButton);
        
        jdtRadioButton.addActionListener(e -> {
            useSpoon = false;
            updateGraph();
        });
        
        spoonRadioButton.addActionListener(e -> {
            useSpoon = true;
            updateGraph();
        });
        
        analyzerPanel.add(jdtRadioButton);
        analyzerPanel.add(spoonRadioButton);
        panel.add(analyzerPanel);
        
        // Séparateur
        panel.add(new JSeparator(SwingConstants.VERTICAL));
        
        // Bouton d'analyse
        JButton analyzeBtn = new JButton("Analyser le Couplage");
        analyzeBtn.addActionListener(e -> analyzeCoupling());
        panel.add(analyzeBtn);
        
        // Séparateur
        panel.add(new JSeparator(SwingConstants.VERTICAL));
        
        // Contrôle du seuil de poids
        panel.add(new JLabel("Seuil de poids:"));
        weightSlider = new JSlider(0, 100, 10); // 0% à 1% par pas de 0.1%
        weightSlider.setPreferredSize(new Dimension(150, 20));
        weightSlider.addChangeListener(e -> updateGraph());
        panel.add(weightSlider);
        
        weightLabel = new JLabel("0.1%");
        panel.add(weightLabel);
        
        // Contrôle du nombre de nœuds
        panel.add(new JLabel("Max nœuds:"));
        nodeSlider = new JSlider(10, 100, 50);
        nodeSlider.setPreferredSize(new Dimension(150, 20));
        nodeSlider.addChangeListener(e -> updateGraph());
        panel.add(nodeSlider);
        
        nodeLabel = new JLabel("50");
        panel.add(nodeLabel);
        
        // Bouton de réinitialisation
        JButton resetBtn = new JButton("Réinitialiser Vue");
        resetBtn.addActionListener(e -> resetView());
        panel.add(resetBtn);
        
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
            
            Map<String, Map<String, Integer>> counts;
            Map<String, Map<String, Double>> weights;
            int total;
            
            if (useSpoon) {
                // Utiliser Spoon pour le couplage
                statusLabel.setText("Analyse du couplage avec Spoon...");
                SpoonCouplingService spoonService = new SpoonCouplingService(analyzer);
                spoonService.calculateCoupling();
                counts = spoonService.getCouplingMatrix();
                weights = spoonService.getCouplingWeights();
                total = spoonService.getTotalCalls();
            } else {
                // Utiliser JDT pour le couplage
                statusLabel.setText("Analyse du couplage avec JDT...");
                CouplingService.resetPackageDetection();
                Map<String, Set<String>> callGraph = analyzer.getCallGraph();
                counts = CouplingService.countInterClassCalls(callGraph);
                total = CouplingService.totalInterClassEdges(counts);
                weights = CouplingService.normalizeToCouplingWeights(counts, total);
            }
            
            // Créer le graphe
            createCouplingGraph(weights, counts, total);
            
            String analyzerType = useSpoon ? "Spoon" : "JDT";
            statusLabel.setText(String.format("Graphe créé (%s) - %d arêtes inter-classes, %d nœuds affichés", 
                analyzerType, total, graph.getNodeCount()));
                
        } catch (Exception e) {
            e.printStackTrace();
            statusLabel.setText("Erreur lors de l'analyse: " + e.getMessage());
            JOptionPane.showMessageDialog(this, "Erreur lors de l'analyse: " + e.getMessage());
        }
    }
    
    private void createCouplingGraph(Map<String, Map<String, Double>> weights, 
                                   Map<String, Map<String, Integer>> counts, 
                                   int totalEdges) {
        // Créer le graphe seulement s'il n'existe pas encore
        if (graph == null) {
            System.setProperty("org.graphstream.ui", "swing");
            graph = new SingleGraph("Graphe de Couplage");
            graph.setAttribute("ui.quality");
            graph.setAttribute("ui.antialias");
        
            // Style du graphe
            String styleSheet = 
                "graph { padding: 50px; }" +
                "node {" +
                "   size: 20px;" +
                "   text-size: 12px;" +
                "   text-alignment: under;" +
                "   text-background-mode: rounded-box;" +
                "   text-background-color: rgba(255, 255, 255, 200);" +
                "   text-padding: 3px;" +
                "   stroke-mode: plain;" +
                "   stroke-color: #333;" +
                "   stroke-width: 2px;" +
                "   fill-color: #4ECDC4;" +
                "}" +
                "edge {" +
                "   fill-color: rgba(100, 100, 100, 150);" +
                "   arrow-size: 6px, 8px;" +
                "   text-size: 10px;" +
                "   text-alignment: along;" +
                "   text-background-mode: rounded-box;" +
                "   text-background-color: rgba(255, 255, 255, 200);" +
                "}";
            graph.setAttribute("ui.stylesheet", styleSheet);
            
            // Créer le viewer seulement une fois
            viewer = new SwingViewer(graph, Viewer.ThreadingModel.GRAPH_IN_GUI_THREAD);
            viewPanel = (ViewPanel) viewer.addDefaultView(false);
            
            // Layout
            Layout layout = new SpringBox(false);
            ((SpringBox) layout).setForce(0.3);
            ((SpringBox) layout).setQuality(1.0);
            ((SpringBox) layout).setStabilizationLimit(0.1);
            viewer.enableAutoLayout(layout);
            
            // Ajouter le viewPanel au layout
            removeAll();
            add(createControlPanel(), BorderLayout.NORTH);
            add(viewPanel, BorderLayout.CENTER);
            
            // Créer la légende
            JPanel legendPanel = createLegendPanel(new HashMap<>());
            add(legendPanel, BorderLayout.EAST);
            
            // Panel de statut
            JPanel statusPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
            statusPanel.add(statusLabel);
            add(statusPanel, BorderLayout.SOUTH);
            
            revalidate();
            repaint();
        }
        
        // Nettoyer le graphe existant au lieu de le recréer
        graph.clear();
        
        // Couleurs par package
        Map<String, String> packageColors = new HashMap<>();
        String[] colors = {
            "#FF6B6B", "#4ECDC4", "#45B7D1", "#FFA07A", "#98D8C8",
            "#F7DC6F", "#BB8FCE", "#85C1E2", "#F8B739", "#52B788",
            "#E74C3C", "#3498DB", "#2ECC71", "#F39C12", "#9B59B6"
        };
        int colorIndex = 0;
        
        // Collecter toutes les classes
        Set<String> allClasses = new HashSet<>();
        for (var entry : weights.entrySet()) {
            allClasses.add(entry.getKey());
            allClasses.addAll(entry.getValue().keySet());
        }
        
        // Limiter le nombre de nœuds
        double minWeight = weightSlider.getValue() / 10000.0; // Convertir en décimal
        int maxNodes = nodeSlider.getValue();
        
        // Trier les classes par nombre de connexions (pour garder les plus importantes)
        Map<String, Integer> classConnections = new HashMap<>();
        for (String className : allClasses) {
            int connections = 0;
            for (var entry : weights.entrySet()) {
                if (entry.getKey().equals(className)) {
                    connections += entry.getValue().size();
                }
                if (entry.getValue().containsKey(className)) {
                    connections++;
                }
            }
            classConnections.put(className, connections);
        }
        
        // Sélectionner les classes les plus connectées
        Set<String> selectedClasses = classConnections.entrySet().stream()
            .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
            .limit(maxNodes)
            .map(Map.Entry::getKey)
            .collect(java.util.stream.Collectors.toSet());
        
        // Ajouter les nœuds
        for (String className : selectedClasses) {
            String packageName = extractPackage(className);
            packageColors.putIfAbsent(packageName, colors[colorIndex++ % colors.length]);
            
            Node node = graph.addNode(className);
            node.setAttribute("ui.label", getShortClassName(className));
            node.setAttribute("ui.style", "fill-color: " + packageColors.get(packageName) + ";");
            
            // Ajouter des informations sur le nœud
            node.setAttribute("ui.tooltip", className);
        }
        
        // Ajouter les arêtes
        int edgeCount = 0;
        for (var entry : weights.entrySet()) {
            String source = entry.getKey();
            if (!selectedClasses.contains(source)) continue;
            
            for (var targetEntry : entry.getValue().entrySet()) {
                String target = targetEntry.getKey();
                double weight = targetEntry.getValue();
                
                if (!selectedClasses.contains(target) || weight < minWeight) continue;
                
                int count = counts.get(source).get(target);
                String edgeId = source + "->" + target;
                
                if (graph.getEdge(edgeId) == null) {
                    Edge edge = graph.addEdge(edgeId, source, target, true);
                    String label = String.format("%.3f (%d)", weight, count);
                    edge.setAttribute("ui.label", label);
                    
                    // Couleur de l'arête basée sur le poids
                    double intensity = Math.min(weight * 100, 1.0); // Normaliser
                    String color = String.format("rgba(255, 0, 0, %.2f)", intensity);
                    edge.setAttribute("ui.style", "fill-color: " + color + ";");
                    
                    edgeCount++;
                }
            }
        }
        
        // Mettre à jour la légende avec les nouvelles couleurs
        JPanel legendPanel = createLegendPanel(packageColors);
        remove(legendPanel); // Supprimer l'ancienne légende si elle existe
        add(legendPanel, BorderLayout.EAST);
        
        // Mettre à jour le statut
        statusLabel.setText(String.format("Graphe mis à jour - %d nœuds, %d arêtes (seuil: %.3f)", 
            graph.getNodeCount(), edgeCount, minWeight));
        
        revalidate();
        repaint();
        
        // Ajuster la vue après un délai
        SwingUtilities.invokeLater(() -> {
            if (viewer != null) {
                viewer.getDefaultView().getCamera().setViewPercent(1.2);
                viewer.getDefaultView().getCamera().resetView();
            }
        });
    }
    
    private void updateGraph() {
        if (analyzer == null) return;
        
        // Mettre à jour les labels
        double weightValue = weightSlider.getValue() / 10000.0;
        weightLabel.setText(String.format("%.3f%%", weightValue * 100));
        nodeLabel.setText(String.valueOf(nodeSlider.getValue()));
        
        // Recréer le graphe avec les nouveaux paramètres seulement si le graphe existe déjà
        if (graph != null) {
            try {
                Map<String, Set<String>> callGraph = analyzer.getCallGraph();
                Map<String, Map<String, Integer>> counts = CouplingService.countInterClassCalls(callGraph);
                int total = CouplingService.totalInterClassEdges(counts);
                Map<String, Map<String, Double>> weights = CouplingService.normalizeToCouplingWeights(counts, total);
                
                createCouplingGraph(weights, counts, total);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
    
    private void resetView() {
        if (viewer != null && viewer.getDefaultView() != null) {
            viewer.getDefaultView().getCamera().setViewPercent(1.2);
            viewer.getDefaultView().getCamera().resetView();
        }
    }
    
    private String extractPackage(String fullClassName) {
        int lastDot = fullClassName.lastIndexOf('.');
        if (lastDot == -1) return "default";
        
        String classPath = fullClassName.substring(0, lastDot);
        int secondLastDot = classPath.lastIndexOf('.');
        if (secondLastDot == -1) return classPath;
        
        return classPath.substring(0, secondLastDot);
    }
    
    private String getShortClassName(String fullClassName) {
        int lastDot = fullClassName.lastIndexOf('.');
        if (lastDot == -1) return fullClassName;
        return fullClassName.substring(lastDot + 1);
    }
    
    private JPanel createLegendPanel(Map<String, String> packageColors) {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(BorderFactory.createTitledBorder("Packages"));
        panel.setPreferredSize(new Dimension(200, 0));
        
        for (var entry : packageColors.entrySet()) {
            JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT));
            
            JLabel colorBox = new JLabel("   ");
            colorBox.setOpaque(true);
            colorBox.setBackground(Color.decode(entry.getValue()));
            colorBox.setBorder(BorderFactory.createLineBorder(Color.BLACK));
            
            JLabel pkgLabel = new JLabel(" " + entry.getKey());
            pkgLabel.setFont(new Font("Monospaced", Font.PLAIN, 10));
            
            row.add(colorBox);
            row.add(pkgLabel);
            panel.add(row);
        }
        
        JScrollPane scroll = new JScrollPane(panel);
        scroll.setPreferredSize(new Dimension(200, 0));
        
        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.add(scroll);
        return wrapper;
    }
    
    @Override
    public void removeNotify() {
        super.removeNotify();
        if (viewer != null) {
            viewer.close();
        }
    }
}
