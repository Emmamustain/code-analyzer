package com.tp.gui;
import com.tp.ParserAnalyzer;

import javax.swing.*;

import org.graphstream.graph.Graph;
import org.graphstream.graph.Node;
import org.graphstream.graph.implementations.SingleGraph;
import org.graphstream.ui.swing_viewer.SwingViewer;
import org.graphstream.ui.view.View;
import org.graphstream.ui.view.Viewer;

import java.awt.*;
import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class AnalyzerGUI extends JFrame {
    private JTextArea statsArea;
    private JTextArea callGraphArea;
    private File selectedDir;

    public AnalyzerGUI() {
        super("Code Analyzer");
        setSize(800, 600);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        JButton selectBtn = new JButton("Select Project");
        JButton analyzeBtn = new JButton("Analyze");
        JButton visualizeBtn = new JButton("Visualize Call Graph");
        

        // Tabs
        statsArea = new JTextArea();
        statsArea.setEditable(false);
        callGraphArea = new JTextArea();
        callGraphArea.setEditable(false);

        JScrollPane statsScroll = new JScrollPane(statsArea);
        JScrollPane callScroll = new JScrollPane(callGraphArea);

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Statistics", statsScroll);
        tabs.addTab("Call Graph", callScroll);

        // Top panel
        JPanel topPanel = new JPanel();
        topPanel.add(selectBtn);
        topPanel.add(analyzeBtn);
        topPanel.add(visualizeBtn);

        add(topPanel, BorderLayout.NORTH);
        add(tabs, BorderLayout.CENTER);

        // actions
        selectBtn.addActionListener(e -> chooseProjectDir());
        analyzeBtn.addActionListener(e -> analyzeProject());
        visualizeBtn.addActionListener(e -> {
            if (selectedDir == null) {
                JOptionPane.showMessageDialog(this, "Please select a project first.");
                return;
            }
            try {
                ParserAnalyzer pa = new ParserAnalyzer(selectedDir.getAbsolutePath());
                pa.analyze();
                showGraph(pa.getCallGraph()); // 👈 this calls your new method
            } catch (Exception ex) {
                ex.printStackTrace();
                JOptionPane.showMessageDialog(this, "Error showing graph: " + ex.getMessage());
            }
        });
    }

    private void chooseProjectDir() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            selectedDir = chooser.getSelectedFile();
            statsArea.setText("Selected: " + selectedDir.getAbsolutePath() + "\n");
            callGraphArea.setText(""); // clear
        }
    }

    private void analyzeProject() {
        if (selectedDir == null) {
            JOptionPane.showMessageDialog(this, "Please select a project folder first.");
            return;
        }
        try {
            ParserAnalyzer pa = new ParserAnalyzer(selectedDir.getAbsolutePath());
            pa.analyze();
            
            // use the helper methods instead of sysout
            String stats = pa.getStatisticsAsString(3); // Ex. 1.1
            String graph = pa.getCallGraphAsString();   // Ex. 2.1

            statsArea.setText(stats);
            callGraphArea.setText(graph);

        } catch (Exception ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(this, "Error: " + ex.getMessage());
        }
    }

    public void showGraph(Map<String, Set<String>> callGraph) {
    	System.setProperty("org.graphstream.ui", "swing");
        Graph graph = new SingleGraph("Call Graph");
        // Enhanced styling
        graph.setAttribute("ui.quality");
        graph.setAttribute("ui.antialias");
        
        String styleSheet = 
            "node {" +
            "   size: 20px;" +
            "   text-size: 11px;" +
            "   text-alignment: under;" +
            "   text-background-mode: rounded-box;" +
            "   text-background-color: rgba(255, 255, 255, 220);" +
            "   text-padding: 3px;" +
            "   stroke-mode: plain;" +
            "   stroke-color: #333;" +
            "   stroke-width: 1px;" +
            "}" +
            "edge {" +
            "   fill-color: #888;" +
            "   arrow-size: 10px, 6px;" +
            "}";
        
        graph.setAttribute("ui.stylesheet", styleSheet);
        
        // Color palette for packages
        Map<String, String> packageColors = new HashMap<>();
        String[] colors = {
            "#FF6B6B", "#4ECDC4", "#45B7D1", "#FFA07A", "#98D8C8",
            "#F7DC6F", "#BB8FCE", "#85C1E2", "#F8B739", "#52B788"
        };
        int colorIndex = 0;
        
        // Build graph with package coloring
        for (var entry : callGraph.entrySet()) {
            String caller = entry.getKey();
            String callerPkg = extractPackage(caller);
            
            if (!packageColors.containsKey(callerPkg)) {
                packageColors.put(callerPkg, colors[colorIndex % colors.length]);
                colorIndex++;
            }
            
            if (graph.getNode(caller) == null) {
                Node node = graph.addNode(caller);
                node.setAttribute("ui.label", simplifyName(caller));
                node.setAttribute("ui.style", "fill-color: " + packageColors.get(callerPkg) + ";");
            }

            for (String callee : entry.getValue()) {
                String calleePkg = extractPackage(callee);
                
                if (!packageColors.containsKey(calleePkg)) {
                    packageColors.put(calleePkg, colors[colorIndex % colors.length]);
                    colorIndex++;
                }
                
                if (graph.getNode(callee) == null) {
                    Node node = graph.addNode(callee);
                    node.setAttribute("ui.label", simplifyName(callee));
                    node.setAttribute("ui.style", "fill-color: " + packageColors.get(calleePkg) + ";");
                }
                
                String edgeId = caller + "->" + callee;
                if (graph.getEdge(edgeId) == null) {
                    graph.addEdge(edgeId, caller, callee, true);
                }
            }
        }
        
        // Display with enhanced viewer
        Viewer viewer = new SwingViewer(graph, Viewer.ThreadingModel.GRAPH_IN_ANOTHER_THREAD);
        viewer.enableAutoLayout();
        View view = viewer.addDefaultView(false);
        
        // Create frame with controls
        JFrame frame = new JFrame("Call Graph - Use mouse wheel to zoom, drag to pan");
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        frame.setSize(1200, 800);
        
        // Add view to frame
        frame.add((Component) view, BorderLayout.CENTER);
        
        // Add legend panel
        JPanel legendPanel = createLegendPanel(packageColors);
        frame.add(legendPanel, BorderLayout.EAST);
        
        frame.setLocationRelativeTo(this);
        frame.setVisible(true);
    }

    // Extract package from full method name
    private String extractPackage(String fullMethodName) {
        int lastDot = fullMethodName.lastIndexOf('.');
        if (lastDot == -1) return "default";
        
        String classPath = fullMethodName.substring(0, lastDot);
        int secondLastDot = classPath.lastIndexOf('.');
        if (secondLastDot == -1) return classPath;
        
        return classPath.substring(0, secondLastDot);
    }

    // Simplify node labels (ClassName.methodName)
    private String simplifyName(String fullName) {
        String[] parts = fullName.split("\\.");
        if (parts.length < 2) return fullName;
        return parts[parts.length - 2] + "." + parts[parts.length - 1];
    }

    // Create legend panel
    private JPanel createLegendPanel(Map<String, String> packageColors) {
    	  JPanel panel = new JPanel();
          panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
          panel.setBorder(BorderFactory.createTitledBorder("Package Legend"));
          
          for (var entry : packageColors.entrySet()) {
              JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT));
              
              JLabel colorBox = new JLabel("   ");
              colorBox.setOpaque(true);
              colorBox.setBackground(Color.decode(entry.getValue()));
              colorBox.setBorder(BorderFactory.createLineBorder(Color.BLACK));
              
              JLabel pkgLabel = new JLabel(" " + entry.getKey());
              pkgLabel.setFont(new Font("Monospaced", Font.PLAIN, 11));
              
              row.add(colorBox);
              row.add(pkgLabel);
              panel.add(row);
          }
          
          JScrollPane scroll = new JScrollPane(panel);
          scroll.setPreferredSize(new Dimension(250, 0));
          
          JPanel wrapper = new JPanel(new BorderLayout());
          wrapper.add(scroll);
          return wrapper;
      }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new AnalyzerGUI().setVisible(true));
    }
}