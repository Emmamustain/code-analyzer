# Analyseur Statique de Code Java avec Couplage et Clustering Hiérarchique

Ce projet est un analyseur statique complet pour applications Java qui calcule des métriques de couplage entre classes et implémente un algorithme de clustering hiérarchique pour identifier des modules cohésifs. L'application propose une interface graphique moderne réalisée avec Java Swing.

## Fonctionnalités

### Métriques de base

- Calcul et affichage de métriques logicielles (nombre de classes, méthodes, packages, lignes de code, moyennes, etc.)
- Résultats disponibles dans l'onglet Statistiques

### Analyse de couplage

- **Calcul du couplage inter-classes** : Métrique normalisée basée sur les appels de méthodes entre classes
- **Détection automatique des packages** : Support universel pour tous types de structures de packages
- **Résolution avancée des types** : Gestion des appels sur paramètres et variables locales
- **Filtrage intelligent** : Exclusion des méthodes de collections et des appels intra-classe

### Visualisation du couplage

- **Graphe de couplage interactif** : Visualisation avec GraphStream, coloration par package
- **Résumé textuel** : Affichage détaillé des métriques de couplage
- **Comparaison JDT vs Spoon** : Analyse comparative des deux approches de parsing

### Clustering hiérarchique

- **Algorithme agglomératif** : Clustering bottom-up avec linkage moyen
- **Identification de modules** : Découpage du dendrogramme selon les contraintes CP et M/2
- **Deux implémentations** : Version JDT et version Spoon pour comparaison
- **Export CSV** : Génération de rapports détaillés des modules identifiés

### Interface utilisateur

- **Onglets spécialisés** : Graphe de couplage, Résumé couplage, Clustering hiérarchique, Clustering Spoon
- **Contrôles interactifs** : Sliders pour seuils, boutons d'export, paramètres configurables
- **Visualisation d'image** : Capture haute résolution avec zoom et navigation fluides

## Pile technique

- **Langage** : Java 17
- **Build** : Maven
- **Parsing AST** : Eclipse JDT Core
- **Analyse Spoon** : Spoon Core 10.4.1
- **Visualisation** : GraphStream
- **Interface** : Java Swing

## Installation et lancement

### Prérequis

- JDK 17 ou supérieur
- IDE compatible Maven (IntelliJ IDEA, Eclipse, VS Code)
- Connexion Internet pour le téléchargement des dépendances

### Installation

1. Cloner ou télécharger le projet
2. Importer dans votre IDE :
   - **Eclipse** : File → Import → Maven → Existing Maven Projects
   - **IntelliJ** : File → Open → Sélectionner le dossier racine
   - **VS Code** : File → Open Folder → Accepter l'import Maven

### Lancement

```bash
mvn exec:java -Dexec.mainClass="com.tp.gui.AnalyzerGUI"
```

Ou depuis l'IDE : exécuter la classe `com.tp.gui.AnalyzerGUI`

## Manuel d'utilisation

### 1. Sélection du projet

- Cliquer sur "Sélectionner un projet"
- Choisir le dossier racine du projet Java à analyser
- Le chemin s'affiche dans l'onglet Statistiques

### 2. Analyse des métriques

- Cliquer sur "Analyser"
- Consulter les métriques dans l'onglet Statistiques

### 3. Analyse du couplage

- **Onglet "Graphe de Couplage"** :
  - Choisir entre analyseur JDT ou Spoon
  - Ajuster les seuils avec les sliders
  - Visualiser le graphe interactif
- **Onglet "Résumé Couplage"** :
  - Consulter les métriques détaillées
  - Comparer les résultats JDT vs Spoon

### 4. Clustering hiérarchique

- **Onglet "Clustering Hiérarchique"** :
  - Configurer le seuil de couplage minimum (CP)
  - Cliquer sur "Analyser le Clustering"
  - Consulter les modules identifiés
  - Exporter en CSV si nécessaire
- **Onglet "Clustering Spoon"** :
  - Même processus avec l'analyseur Spoon
  - Comparaison des résultats

## Architecture technique

### Parsing et analyse

- **Eclipse JDT** : Parsing AST avec résolution des bindings
- **Visiteurs spécialisés** : Collecte des déclarations, invocations et types
- **Post-traitement** : Résolution des appels sur variables locales et paramètres

### Calcul du couplage

- **Matrice de couplage** : Comptage des appels inter-classes uniques
- **Normalisation** : Poids de couplage basés sur le nombre total d'arêtes
- **Filtrage** : Exclusion des méthodes de collections et appels intra-classe

### Clustering hiérarchique

- **Algorithme agglomératif** : Fusion itérative des clusters les plus couplés
- **Linkage moyen** : Calcul de distance entre clusters
- **Découpage top-down** : Identification des modules selon les contraintes

### Services Spoon

- **Modèle Spoon** : Analyse alternative avec résolution de types avancée
- **Comparaison** : Évaluation des différences entre JDT et Spoon
- **Validation** : Vérification de la cohérence des résultats

## Contraintes et limitations

### Contraintes du clustering

- **M/2 modules maximum** : M = nombre total de classes
- **Seuil CP** : Couplage minimum requis par module
- **Branches du dendrogramme** : Chaque module correspond à une branche unique

### Limitations techniques

- Résolution des bindings JDT dépendante de la structure du projet
- Performance dégradée sur très gros projets (>1000 classes)
- Visualisation GraphStream limitée sur certains environnements

## Structure du projet

```
src/main/java/com/tp/
├── analysis/           # Services d'analyse et clustering
├── gui/               # Interface utilisateur
├── model/             # Modèles de données
├── spoon/             # Services Spoon
├── visitors/          # Visiteurs AST
├── Analyzer.java      # Point d'entrée principal
└── ParserAnalyzer.java # Analyseur principal
```

## Exemples d'utilisation

### Analyse d'un projet simple

1. Sélectionner un projet Java
2. Analyser les métriques de base
3. Examiner le couplage entre classes
4. Identifier les modules cohésifs avec le clustering

### Comparaison JDT vs Spoon

1. Analyser le même projet avec les deux approches
2. Comparer les résultats dans l'onglet "Résumé Couplage"
3. Évaluer les différences de résolution des types

### Export et reporting

1. Générer les rapports de clustering
2. Exporter les modules en CSV
3. Utiliser les données pour l'analyse d'architecture

## Contribution

Ce projet a été développé dans un cadre académique pour l'analyse statique de code Java et l'identification de modules cohésifs par clustering hiérarchique.
