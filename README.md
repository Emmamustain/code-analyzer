# Analyseur de Code Java Statique avec Visualisation de Graphe d'Appel

Ce projet, réalisé dans le cadre d'un projet universitaire, est un analyseur de code statique pour applications Java. Il parcourt le code source, calcule un ensemble complet de métrriques logicielles et génère un graphe d'appel interactif pour visualiser les relations entre les méthodes.

L'application dispose d'une interface graphique construite avec Java Swing.

## Fonctionnalités

### Métriques Statistiques
L'application calcule et affiche 13 métriques précises sur le code analysé, notamment le nombre de classes, de méthodes, de packages, les moyennes, les top 10 %, etc.

### Graphe d'Appel
- Représentation textuelle des relations d'appel entre méthodes
- Visualisation interactive dans une fenêtre dédiée

### Fonctionnalités Avancées
- Visualisation interactive: molette pour zoom/dézoom, glisser-déposer pour se déplacer
- Coloration par package: chaque package possède une couleur unique
- Légende dynamique: correspondance couleur/package
- Labels simplifiés: format Classe.méthode pour une meilleure lisibilité
- Style graphique soigné: nœuds et arêtes stylisés via le CSS de GraphStream

## Stack Technique

- Langage: Java 17
- Build/gestion des dépendances: Maven
- Parsing et analyse du code (AST): Eclipse JDT Core
- Visualisation du graphe: GraphStream
- Interface graphique: Java Swing

## Installation et Lancement

### Prérequis
- Java Development Kit (JDK) 17 ou supérieur
- Un IDE compatible Maven (IntelliJ IDEA, Eclipse, ou VS Code avec extensions Java/Maven)
- Connexion Internet lors du premier import (pour télécharger les dépendances Maven)

### 1. Récupérer le projet (archive .zip)
- Décompressez l’archive du projet dans un dossier de votre choix.

### 2. Importer le projet Maven dans l’IDE
- Eclipse:
  - File → Import…
  - Maven → Existing Maven Projects → Next
  - Root Directory → parcourir le dossier décompressé
  - Vérifier que pom.xml est détecté → Finish
- IntelliJ IDEA:
  - File → Open…
  - Sélectionner le dossier racine qui contient pom.xml
  - Ouvrir “as Project” et attendre la fin de l’indexation et du téléchargement des dépendances
- VS Code:
  - File → Open Folder…
  - Ouvrir le dossier racine (qui contient pom.xml)
  - Accepter l’import Maven et attendre la résolution des dépendances

Remarque: Maven gère automatiquement les bibliothèques nécessaires (Eclipse JDT, GraphStream, etc.).

### 3. Lancer l’application
Deux options:

- Depuis l’IDE (recommandé)
  - Lancer la classe GUI: `com.tp.gui.AnalyzerGUI` (interface Swing)
  - Optionnel: lancer la version console: `com.tp.Analyzer`

- En ligne de commande (facultatif)
  - Dans le dossier du projet (celui qui contient pom.xml):
    ```bash
    mvn clean compile
    ```
  - Le lancement de la GUI se fait de préférence depuis l’IDE.

## Manuel d’Utilisation

1. Lancement  
   Au démarrage, la fenêtre “Code Analyzer” s’ouvre.

2. Sélection d’un projet  
   - Cliquer sur “Select Project”  
   - Choisir le dossier du code source Java à analyser (ex: `src/main/java` d’un projet)  
   - Valider: le chemin s’affiche dans l’onglet “Statistics”

3. Analyse du code  
   - Cliquer sur “Analyze”  
   - L’application parcourt tous les fichiers `.java`  
   - L’onglet “Statistics” affiche les 13 métriques calculées  
   - L’onglet “Call Graph” affiche la représentation textuelle du graphe d’appel

4. Visualisation du graphe d’appel  
   - Cliquer sur “Visualize Call Graph”  
   - Une fenêtre s’ouvre avec le graphe interactif  
   - Interactions:
     - Zoom/Dézoom: molette de la souris
     - Déplacement: cliquer-glisser dans la zone du graphe  
   - La légende à droite indique la couleur associée à chaque package

## Fonctionnement Interne

1. Découverte des fichiers  
   Parcours récursif du dossier sélectionné pour collecter les fichiers `.java`.

2. Parsing AST  
   Chaque fichier est parsé avec Eclipse JDT pour produire un Abstract Syntax Tree (AST).  
   La résolution des bindings (`setResolveBindings(true)`) est activée pour enrichir l’analyse sémantique.

3. Visiteurs spécialisés  
   Parcours de l’AST pour collecter les déclarations de classes et de méthodes, les variables, et les invocations (y compris `super.method()`).

4. Agrégation des métriques  
   La classe `ParserAnalyzer` agrège les données dans des modèles (`ClassMetrics`, `MethodMetrics`) et construit la structure du graphe d’appel: `Map<String, Set<String>>`.

5. Visualisation  
   - Représentation textuelle des appels dans l’onglet “Call Graph”  
   - Visualisation GraphStream dans une fenêtre dédiée:
     - Coloration par package
     - Labels simplifiés Classe.méthode
     - Légende dynamique
     - Mise en page calculée une fois avant l’affichage