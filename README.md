# Adaptask

Application Android native de gestion des tâches, incidents et rappels (Java, API 26+).

## Budget & Dépenses (1.7.0-beta1)

Un module **intégré à Adaptask**, accessible depuis l'accueil ou l'onglet **Budget** :
- définir/modifier un **capital initial** en FCFA ;
- ajouter, modifier ou supprimer une dépense (description, catégorie, montant entier et date) ;
- consulter **total dépensé** et **reste = capital − dépenses** mis à jour immédiatement ;
- afficher une alerte si le solde est négatif ;
- exporter l'historique en CSV compatible Excel (séparateur point-virgule, UTF-8 BOM, solde progressif) ;
- utiliser le module **100 % hors connexion**, en français ou anglais selon la langue de l'appareil.

Les dépenses sont stockées localement dans `adaptask.db` (migration de schéma 7 → 8), sans serveur ni connexion bancaire, sans dépendance externe et sans synchronisation cloud.

### Sauvegardes

Le JSON exporté depuis les paramètres d'Adaptask inclut désormais le budget et ses dépenses (format v2). La restauration v2 restaure toutes ces données dans **une transaction SQLite**. Les anciens fichiers de sauvegarde v1 restent acceptés et ne modifient pas les données du budget existant. Avant une restauration v2, exporter une sauvegarde récente est recommandé : les données courantes sont remplacées.

### Limites de cette version

Un seul capital global dans Adaptask ; monnaie FCFA en montants entiers ; pas de rapprochement bancaire, budget périodique, revenu ou synchronisation entre appareils. Le capital peut être modifié ; le solde est recalculé sur **toutes les dépenses enregistrées**, sans remise à zéro automatique.

## Compilation

Le workflow GitHub Actions `Build Android APK` compile `assembleDebug` à chaque push sur `main` et sur les pull requests vers `main`, puis met l'APK dans les artefacts de la compilation.

## Confidentialité

Tâches et dépenses restent dans la base locale de l'app. L'export CSV/JSON ne partage les données que lorsque l'utilisateur choisit de créer lui-même un fichier.
