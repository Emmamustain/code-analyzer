package com.tp.gui;

import com.tp.ParserAnalyzer;
import javax.swing.*;

import org.graphstream.graph.Graph;
import org.graphstream.graph.Node;
import org.graphstream.graph.implementations.SingleGraph;
import org.graphstream.ui.swing_viewer.SwingViewer;
import org.graphstream.ui.swing_viewer.ViewPanel;
import org.graphstream.ui.view.View;
import org.graphstream.ui.view.Viewer;
import org.graphstream.ui.view.camera.Camera;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class AnalyzerGUI extends JFrame {
  private JTextArea statsArea;
  private JTextArea callGraphArea;
  private File selectedDir;

  public AnalyzerGUI() {
    super("Analyseur de Code");
    setSize(800, 600);
    setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

    JButton selectBtn = new JButton("Sélectionner un projet");
    JButton analyzeBtn = new JButton("Analyser");
    JButton visualizeBtn = new JButton("Visualiser le graphe d’appel");

    statsArea = new JTextArea();
    statsArea.setEditable(false);
    callGraphArea = new JTextArea();
    callGraphArea.setEditable(false);

    JScrollPane statsScroll = new JScrollPane(statsArea);
    JScrollPane callScroll = new JScrollPane(callGraphArea);

    JTabbedPane tabs = new JTabbedPane();
    tabs.addTab("Statistiques", statsScroll);
    tabs.addTab("Graphe d’appel (texte)", callScroll);

    JPanel topPanel = new JPanel();
    topPanel.add(selectBtn);
    topPanel.add(analyzeBtn);
    topPanel.add(visualizeBtn);

    add(topPanel, BorderLayout.NORTH);
    add(tabs, BorderLayout.CENTER);

    selectBtn.addActionListener(e -> chooseProjectDir());
    analyzeBtn.addActionListener(e -> analyzeProject());
    visualizeBtn.addActionListener(
        e -> {
          if (selectedDir == null) {
            JOptionPane.showMessageDialog(
                this, "Veuillez d’abord sélectionner un dossier de projet.");
            return;
          }
          try {
            ParserAnalyzer pa = new ParserAnalyzer(selectedDir.getAbsolutePath());
            pa.analyze();
            showGraph(pa.getCallGraph());
          } catch (Exception ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(
                this, "Erreur lors de l’affichage du graphe : " + ex.getMessage());
          }
        });
  }

  private void chooseProjectDir() {
    JFileChooser chooser = new JFileChooser();
    chooser.setDialogTitle("Sélectionner le dossier du projet");
    chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
    if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
      selectedDir = chooser.getSelectedFile();
      statsArea.setText("Sélectionné : " + selectedDir.getAbsolutePath() + "\n");
      callGraphArea.setText("");
    }
  }

  private void analyzeProject() {
    if (selectedDir == null) {
      JOptionPane.showMessageDialog(
          this, "Veuillez d’abord sélectionner un dossier de projet.");
      return;
    }
    try {
      ParserAnalyzer pa = new ParserAnalyzer(selectedDir.getAbsolutePath());
      pa.analyze();

      String stats = pa.getStatisticsAsString(3);
      String graph = pa.getCallGraphAsString();

      statsArea.setText(stats);
      callGraphArea.setText(graph);

    } catch (Exception ex) {
      ex.printStackTrace();
      JOptionPane.showMessageDialog(this, "Erreur : " + ex.getMessage());
    }
  }

  // Affiche le graphe (sans sous-classer ViewPanel)
  public void showGraph(Map<String, Set<String>> callGraph) {
    System.setProperty("org.graphstream.ui", "swing");
    Graph graph = new SingleGraph("Graphe d’appel");
    graph.setAttribute("ui.quality");
    graph.setAttribute("ui.antialias");

    String styleSheet =
        "graph { padding: 50px; }"
            + "node {"
            + "   size: 15px;"
            + "   text-size: 10px;"
            + "   text-alignment: under;"
            + "   text-background-mode: rounded-box;"
            + "   text-background-color: rgba(255, 255, 255, 200);"
            + "   text-padding: 2px;"
            + "   stroke-mode: plain;"
            + "   stroke-color: #333;"
            + "   stroke-width: 1px;"
            + "}"
            + "edge {"
            + "   fill-color: rgba(100, 100, 100, 100);"
            + "   arrow-size: 4px, 5px;"
            + "   text-size: 10px;"
            + "}";
    graph.setAttribute("ui.stylesheet", styleSheet);

    Map<String, String> packageColors = new HashMap<>();
    String[] colors = {
      "#FF6B6B", "#4ECDC4", "#45B7D1", "#FFA07A", "#98D8C8",
      "#F7DC6F", "#BB8FCE", "#85C1E2", "#F8B739", "#52B788"
    };
    int colorIndex = 0;

    Set<String> systemMethods =
        Set.of(
            "println",
            "print",
            "add",
            "remove",
            "get",
            "set",
            "size",
            "isEmpty",
            "contains",
            "equals",
            "toString",
            "hashCode");

    for (var entry : callGraph.entrySet()) {
      String caller = entry.getKey();
      String callerPkg = extractPackage(caller);

      packageColors.putIfAbsent(callerPkg, colors[colorIndex++ % colors.length]);

      if (graph.getNode(caller) == null) {
        Node node = graph.addNode(caller);
        node.setAttribute("ui.label", simplifyName(caller));
        node.setAttribute(
            "ui.style", "fill-color: " + packageColors.get(callerPkg) + ";");
      }

      for (String callee : entry.getValue()) {
        String simpleCallee =
            callee.contains(".")
                ? callee.substring(callee.lastIndexOf('.') + 1)
                : callee;

        if (systemMethods.contains(simpleCallee)) continue;

        String calleePkg = extractPackage(callee);
        packageColors.putIfAbsent(calleePkg, colors[colorIndex++ % colors.length]);

        if (graph.getNode(callee) == null) {
          Node node = graph.addNode(callee);
          node.setAttribute("ui.label", simplifyName(callee));
          node.setAttribute(
              "ui.style", "fill-color: " + packageColors.get(calleePkg) + ";");
        }

        String edgeId = caller + "->" + callee;
        if (graph.getEdge(edgeId) == null) {
          graph.addEdge(edgeId, caller, callee, true);
        }
      }
    }

    Viewer viewer = new SwingViewer(graph, Viewer.ThreadingModel.GRAPH_IN_GUI_THREAD);

    org.graphstream.ui.layout.Layout layout =
        new org.graphstream.ui.layout.springbox.implementations.SpringBox(false);

    ViewPanel viewPanel = (ViewPanel) viewer.addDefaultView(false);

    viewPanel.setMouseManager(
        new org.graphstream.ui.swing_viewer.util.DefaultMouseManager());

    JFrame frame =
        new JFrame(
            "Graphe d’appel — Molette : zoom | Glisser : déplacement | Clic droit : menu");
    frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
    frame.setSize(1400, 900);

    // Réglage du layout (selon votre version SpringBox)
    var sb = (org.graphstream.ui.layout.springbox.implementations.SpringBox) layout;
    sb.setForce(0.2);
    sb.setQuality(1.0);
    sb.setStabilizationLimit(0.2);

    // Conteneur pour basculer entre vue live et image
    JPanel centerContainer = new JPanel(new BorderLayout());
    centerContainer.add(viewPanel, BorderLayout.CENTER);

    JPanel controlPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
    controlPanel.add(new JLabel("📊 Méthodes système filtrées pour plus de clarté"));

    JButton fitBtn = new JButton("Ajuster la vue");
    fitBtn.addActionListener(ev -> fitView(viewer));
    controlPanel.add(fitBtn);

    viewer.enableAutoLayout(layout);

    // Bouton Snapshot : remplacer la vue live par une image zoomable
    JButton snapshotBtn = new JButton("📷 Capturer en image");
    snapshotBtn.addActionListener(
        e -> {
          try {
            BufferedImage image = captureGraphAsImage(viewPanel);
            ImageViewerPanel imageViewer = new ImageViewerPanel(image);

            centerContainer.removeAll();
            centerContainer.add(imageViewer, BorderLayout.CENTER);
            centerContainer.revalidate();
            centerContainer.repaint();

            snapshotBtn.setEnabled(false);
            fitBtn.setEnabled(false);

            JOptionPane.showMessageDialog(
                frame,
                "Graphe converti en image ! Utilisez la molette pour zoomer et le clic-glisser pour vous déplacer.",
                "Capture créée",
                JOptionPane.INFORMATION_MESSAGE);
          } catch (Exception ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(
                frame, "Erreur lors de la capture : " + ex.getMessage());
          }
        });
    controlPanel.add(snapshotBtn);

    frame.add(controlPanel, BorderLayout.NORTH);
    frame.add(centerContainer, BorderLayout.CENTER);

    JPanel legendPanel = createLegendPanel(packageColors);
    frame.add(legendPanel, BorderLayout.EAST);

    frame.setLocationRelativeTo(this);
    frame.setVisible(true);

    SwingUtilities.invokeLater(
        () -> {
          syncInternalViewSize(viewer, viewPanel);
          fitView(viewer);
        });

    viewPanel.addComponentListener(
        new java.awt.event.ComponentAdapter() {
          @Override
          public void componentResized(java.awt.event.ComponentEvent e) {
            syncInternalViewSize(viewer, viewPanel);
            viewPanel.revalidate();
            viewPanel.repaint();
          }
        });

    java.awt.event.MouseMotionAdapter motionSync =
        new java.awt.event.MouseMotionAdapter() {
          private void sync() {
            syncInternalViewSize(viewer, viewPanel);
          }

          @Override
          public void mouseDragged(java.awt.event.MouseEvent e) {
            sync();
          }

          @Override
          public void mouseMoved(java.awt.event.MouseEvent e) {
            sync();
          }
        };
    viewPanel.addMouseMotionListener(motionSync);
    viewPanel.addMouseWheelListener(e -> syncInternalViewSize(viewer, viewPanel));

    new javax.swing.Timer(
            250,
            e -> {
              if (viewPanel.isShowing()) syncInternalViewSize(viewer, viewPanel);
            })
        .start();
  }

  private void fitView(Viewer viewer) {
    View view = viewer.getDefaultView();
    if (view == null) return;
    Camera cam = view.getCamera();
    cam.setViewPercent(1.25);
    cam.setViewCenter(0, 0, 0);
    cam.resetView();
  }

  // Synchronise la taille interne de la vue GraphStream avec le composant Swing
  private void syncInternalViewSize(Viewer viewer, ViewPanel panel) {
    View view = viewer.getDefaultView();
    if (view == null) return;
    int w = Math.max(1, panel.getWidth());
    int h = Math.max(1, panel.getHeight());
    ((ViewPanel) view).resizeFrame(w, h);
  }

  private String extractPackage(String fullMethodName) {
    int lastDot = fullMethodName.lastIndexOf('.');
    if (lastDot == -1) return "default";

    String classPath = fullMethodName.substring(0, lastDot);
    int secondLastDot = classPath.lastIndexOf('.');
    if (secondLastDot == -1) return classPath;

    return classPath.substring(0, secondLastDot);
  }

  private String simplifyName(String fullName) {
    String[] parts = fullName.split("\\.");
    if (parts.length < 2) return fullName;
    return parts[parts.length - 2] + "." + parts[parts.length - 1];
  }

  private JPanel createLegendPanel(Map<String, String> packageColors) {
    JPanel panel = new JPanel();
    panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
    panel.setBorder(BorderFactory.createTitledBorder("Légende des packages"));

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

  // Capture du panel graphique vers une image haute résolution
  private BufferedImage captureGraphAsImage(ViewPanel viewPanel) {
    int baseWidth = Math.max(viewPanel.getWidth(), 1);
    int baseHeight = Math.max(viewPanel.getHeight(), 1);

    int width = baseWidth * 3;
    int height = baseHeight * 3;

    BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
    Graphics2D g2d = image.createGraphics();

    g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
    g2d.setRenderingHint(
        RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

    // Rendu à échelle 2x pour réduire la taille perçue des labels
    g2d.scale(2.0, 2.0);

    viewPanel.paint(g2d);
    g2d.dispose();

    return image;
  }

  public static void main(String[] args) {
    SwingUtilities.invokeLater(() -> new AnalyzerGUI().setVisible(true));
  }

  // Visionneuse d’image avec zoom/déplacement
  private static class ImageViewerPanel extends JPanel {
    private final BufferedImage image;
    private double scale = 1.0;
    private int offsetX = 0;
    private int offsetY = 0;
    private Point dragStart;

    public ImageViewerPanel(BufferedImage image) {
      this.image = image;
      setBackground(Color.WHITE);

      addMouseWheelListener(
          e -> {
            Point mouse = e.getPoint();
            double oldScale = scale;

            if (e.getWheelRotation() < 0) {
              scale *= 1.1;
            } else {
              scale /= 1.1;
            }

            scale = Math.max(0.1, Math.min(scale, 10.0));

            // Zoom vers la position de la souris
            offsetX = (int) (mouse.x - (mouse.x - offsetX) * (scale / oldScale));
            offsetY = (int) (mouse.y - (mouse.y - offsetY) * (scale / oldScale));

            repaint();
          });

      addMouseListener(
          new java.awt.event.MouseAdapter() {
            @Override
            public void mousePressed(java.awt.event.MouseEvent e) {
              dragStart = e.getPoint();
              setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
            }

            @Override
            public void mouseReleased(java.awt.event.MouseEvent e) {
              dragStart = null;
              setCursor(Cursor.getDefaultCursor());
            }
          });

      addMouseMotionListener(
          new java.awt.event.MouseMotionAdapter() {
            @Override
            public void mouseDragged(java.awt.event.MouseEvent e) {
              if (dragStart != null) {
                int dx = e.getX() - dragStart.x;
                int dy = e.getY() - dragStart.y;
                offsetX += dx;
                offsetY += dy;
                dragStart = e.getPoint();
                repaint();
              }
            }
          });
    }

    @Override
    protected void paintComponent(Graphics g) {
      super.paintComponent(g);
      Graphics2D g2d = (Graphics2D) g;

      g2d.setRenderingHint(
          RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
      g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

      AffineTransform transform = new AffineTransform();
      transform.translate(offsetX, offsetY);
      transform.scale(scale, scale);

      g2d.drawImage(image, transform, null);

      // HUD de zoom
      g2d.setColor(new Color(0, 0, 0, 150));
      g2d.fillRoundRect(10, 10, 140, 30, 10, 10);
      g2d.setColor(Color.WHITE);
      g2d.setFont(new Font("Arial", Font.PLAIN, 12));
      g2d.drawString(String.format("Zoom : %.0f%%", scale * 100), 20, 30);
    }
  }
}