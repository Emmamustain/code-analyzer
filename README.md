# Analyseur Statique de Code Java avec Visualisation du Graphe d’Appel

Ce projet, réalisé dans un cadre universitaire, est un analyseur statique pour applications Java. Il parcourt le code source, calcule un ensemble de métriques logicielles et génère un graphe d’appel pour visualiser les relations entre méthodes. L’application propose une interface graphique réalisée avec Java Swing.

## Fonctionnalités

### Métriques
- Calcul et affichage de 13 métriques (nombre de classes, de méthodes, de packages, lignes de code, moyennes, top 10 %, etc.).
- Résultats disponibles dans l’onglet Statistiques et en sortie console si nécessaire.

### Graphe d’appel
- Représentation textuelle des relations d’appel (onglet Graphe d’appel).
- Visualisation interactive dans une fenêtre dédiée (GraphStream).

### Améliorations de lisibilité et d’usage
- Coloration par package avec légende associée.
- Labels simplifiés au format Classe.méthode.
- Styles soignés (nœuds, arêtes, arrière-plan du texte).
- Bouton “Capturer en image” pour convertir le graphe en une image haute résolution, puis exploration fluide de l’image (zoom au curseur, déplacement par glisser). Cette approche contourne les limites de zoom/pan du composant natif GraphStream.

## Pile technique

- Langage : Java 17
- Build et dépendances : Maven
- Parsing/analyse (AST) : Eclipse JDT Core
- Visualisation : GraphStream
- Interface : Java Swing

## Installation et lancement

### Prérequis
- JDK 17 ou supérieur
- IDE compatible Maven (IntelliJ IDEA, Eclipse, VS Code avec extensions Java/Maven)
- Connexion Internet au premier import pour la résolution des dépendances

### Récupération du projet
- Clone ou archive ZIP, puis extraction dans le dossier de votre choix.

### Import Maven dans l’IDE
- Eclipse
  - File → Import…
  - Maven → Existing Maven Projects
  - Sélectionner le dossier racine (contenant pom.xml) → Finish
- IntelliJ IDEA
  - File → Open…
  - Ouvrir le dossier racine (pom.xml détecté) et attendre l’indexation
- VS Code
  - File → Open Folder…
  - Ouvrir le dossier racine, accepter l’import Maven

Maven télécharge automatiquement Eclipse JDT, GraphStream et les autres dépendances.

### Lancer l’application
- Depuis l’IDE (recommandé)
  - Lancer la classe GUI: com.tp.gui.AnalyzerGUI
- En ligne de commande (optionnel)
  - À la racine du projet (contenant pom.xml):
    ```bash
    mvn clean compile
    ```
  - Le lancement de la GUI se fait plus simplement depuis l’IDE.

## Manuel d’utilisation

1) Démarrage
- La fenêtre “Analyseur de Code” s’ouvre avec trois boutons et deux onglets.

2) Sélection du projet
- Cliquer sur “Sélectionner un projet”
- Choisir le dossier du projet Java à analyser (le dossier racine du projet suffit)
- Le chemin sélectionné s’affiche dans l’onglet Statistiques

3) Analyse
- Cliquer sur “Analyser”
- Les fichiers .java sont parcourus, et les métriques sont affichées dans l’onglet Statistiques
- L’onglet “Graphe d’appel (texte)” montre la structure d’appel sous forme lisible

4) Visualisation du graphe
- Cliquer sur “Visualiser le graphe d’appel”
- Une fenêtre affiche le graphe coloré par package, avec une légende à droite
- Boutons disponibles:
  - “Ajuster la vue”: recadre la caméra
  - “Capturer en image”: convertit la vue en image haute résolution, puis ouvre un visualiseur d’image avec:
    - Zoom/dézoom à la molette (centré sur le curseur)
    - Déplacement par clic-glisser
    - Indication du niveau de zoom

Remarque: le mode image est particulièrement utile pour un zoom fluide et précis sur de grands graphes.

## Fonctionnement interne

1) Découverte des sources
- Parcours récursif du dossier sélectionné pour collecter les fichiers .java.

2) Parsing AST (Eclipse JDT)
- Création d’un AST par fichier.
- Activation de la résolution des bindings (setResolveBindings(true)) pour obtenir des informations sémantiques fiables (noms qualifiés, types, méthodes).

3) Visiteurs spécialisés
- Collecte des déclarations (classes, méthodes), variables, et invocations (y compris super.method()).

4) Agrégation des métriques
- La classe ParserAnalyzer agrège les données dans des modèles (ClassMetrics, MethodMetrics).
- Construction du graphe d’appel: Map<String, Set<String>> (clé: méthode appelante, valeur: méthodes appelées).

5) Visualisation
- Représentation textuelle du graphe dans l’onglet dédié.
- Visualisation graphique via GraphStream (couleurs par package, labels simplifiés, légende).
- Option de capture en image pour un zoom/pan fluides via un visualiseur personnalisé (Java2D).

## Limitations connues

- Le composant de visualisation natif de GraphStream présente des limites de zoom/pan dans certains environnements Swing. Le mode “Capturer en image” est proposé comme solution pour une exploration confortable du graphe.
- La résolution des bindings JDT peut dépendre de la structure du projet et de la présence de toutes les dépendances sources sur le classpath si vous élargissez l’analyse.
