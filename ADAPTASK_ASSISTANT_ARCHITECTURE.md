# Adaptask Assistant V1.6 — Architecture hybride local-first

## Objectif
Assistant bilingue français/anglais, spécialisé dans les tâches, incidents, échéances, priorités, catégories et rappels d'Adaptask.

## Priorité de traitement
1. **Local** : transcription vocale embarquée FR/EN (moteur et modèles à intégrer), interprétation des commandes et accès aux données SQLite.
2. **Clarification locale** : si l'intention est incertaine, afficher une proposition modifiable et demander une précision. Ne pas enregistrer automatiquement une action incertaine.
3. **Dernier recours en ligne** : uniquement lorsque le traitement local ne suffit pas, que le réseau est disponible, qu'un fournisseur externe a été configuré et que l'utilisateur autorise explicitement l'envoi de données.
4. **Hors ligne** : les fonctions essentielles restent utilisables. Aucune dépendance obligatoire à une API cloud.

## Confidentialité et sécurité
- Ne pas transmettre par défaut les descriptions d'incidents, noms, lieux ou détails des tâches à un service externe.
- Afficher le texte exact qui serait transmis et obtenir le consentement avant l'appel distant.
- Ne jamais enregistrer une tâche, clôturer un incident ou modifier une échéance sans confirmation explicite.
- Les clés API ne doivent pas être intégrées en dur dans l'APK.
- Prévoir un réglage « Assistance en ligne de secours » désactivé par défaut.
- Les services externes gratuits peuvent avoir des quotas et ne garantissent pas la disponibilité.

## Statut
- Réalisé : analyse locale bilingue de commandes textuelles et formulaire de vérification (prototype).
- Prototype intégré : bouton de dictée utilisant SpeechRecognizer.createOnDeviceSpeechRecognizer sur Android 12+ uniquement si un moteur local compatible est présent. Les packs linguistiques FR/EN ne sont pas inclus dans l’APK, et la disponibilité dépend du téléphone. À réaliser : empaquetage éventuel de modèles vocaux, gestion des ambiguïtés, réglages de consentement, adaptateur distant optionnel, tests de compilation et sur appareil.
- Aucun appel cloud n'est actuellement implémenté. Le système ne doit pas prétendre disposer déjà du fallback.
