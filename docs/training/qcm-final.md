# QCM Final — Formation FASO-ANALYTICS-WORKFLOW

> **Durée** : 20 minutes.
> **Format** : 25 questions à choix multiples.
> **Nombre de bonnes réponses** : 1 par question (sauf indication contraire explicite).
> **Seuil de réussite** : **70 % (18 / 25)**.
> **Distribution** : J2 — Module M14.

---

## Instructions

- Réponds en cochant **une seule** réponse par question (sauf mention « 2 réponses » dans l'énoncé).
- Les questions abordent les 2 jours de formation : vocabulaire, constructeur visuel, simulation, déploiement, observabilité.
- Le corrigé est en annexe à la fin du document — **ne pas le consulter avant d'avoir terminé**.

---

## Partie A — Vocabulaire (Q1 à Q5)

### Q1. Qu'est-ce qu'un workflow dans FASO-ANALYTICS-WORKFLOW ?

- A. Une simple requête SQL exécutée chaque nuit.
- B. Une recette analytique déclarative versionnée (source → pipeline → KPIs → viz → outputs).
- C. Un dashboard Metabase exporté.
- D. Un fichier Excel partagé sur un répertoire commun.

### Q2. Combien y a-t-il de sub-projets référents formés dans le cadre de cette première promotion ?

- A. 6
- B. 7
- C. 8
- D. 10

### Q3. Que signifie la polarité `less_better` pour un KPI ?

- A. La valeur doit être inférieure à un seuil pour être affichée.
- B. La valeur est meilleure lorsqu'elle est faible (ex. délai, incidents).
- C. La valeur est cachée si elle est trop petite.
- D. Le KPI est désactivé.

### Q4. Parmi les éléments suivants, lequel **n'est pas** une source supportée nativement ?

- A. YugabyteDB
- B. SurveyMonkey webhook
- C. CSV sur S3 sovereign
- D. Microsoft Excel local par drag-and-drop

### Q5. Un « output » dans un workflow désigne :

- A. Une variable de sortie d'une transformation `computed`.
- B. La destination finale du résultat (dashboard, email, export, Metabase).
- C. Un alias de KPI.
- D. Un type de visualisation.

---

## Partie B — Constructeur visuel (Q6 à Q10)

### Q6. La palette du constructeur visuel comporte combien de catégories ?

- A. 3 (Source, Transformation, Output).
- B. 4 (Source, Transformation, KPI, Visualisation).
- C. 5 (Source, Transformation, KPI, Visualisation, Output).
- D. 7.

### Q7. Quel raccourci clavier permet de lancer une simulation depuis l'interface ?

- A. F5
- B. Ctrl+Enter
- C. Ctrl+Shift+S
- D. Alt+R

### Q8. Que se passe-t-il si tu cliques sur un bloc d'un workflow en statut `DEPLOYED` ?

- A. Le bloc est immédiatement supprimé.
- B. Le panneau de paramètres affiche les valeurs en lecture seule.
- C. Le workflow est dépublié.
- D. Un dialogue de confirmation s'affiche pour reprendre l'édition.

### Q9. Quelle est la durée de vie d'un brouillon local non sauvegardé ?

- A. 30 minutes.
- B. 1 heure.
- C. 4 heures.
- D. 24 heures.

### Q10. Le DAG d'un workflow doit être :

- A. Cyclique pour autoriser les boucles d'itération.
- B. Acyclique (sans boucle), orienté.
- C. Bidirectionnel.
- D. Toujours linéaire (1 source → 1 viz).

---

## Partie C — Simulation (Q11 à Q15)

### Q11. Quelle sample strategy garantit la préservation des proportions par catégorie ?

- A. RANDOM
- B. STRATIFIED
- C. BOUNDARY_VALUES
- D. FULL

### Q12. Quelle sample strategy est utilisée pour stress-tester les valeurs extrêmes (min, max, nulls) ?

- A. RANDOM
- B. STRATIFIED
- C. BOUNDARY_VALUES
- D. FULL

### Q13. Que signifie un cadre vert dans le panneau de diff ?

- A. Une nouvelle valeur introduite par la version simulée.
- B. Une valeur identique entre DEPLOYED et SIMULATED (modulo `toleranceMargin`).
- C. Une valeur disparue.
- D. Une erreur de runtime.

### Q14. Quel est l'usage recommandé de la stratégie `FULL` ?

- A. À chaque modification mineure.
- B. Seulement pour le bench final avant déploiement.
- C. Pour les démos en formation.
- D. Jamais.

### Q15. Combien de temps maximum un échantillon de simulation reste disponible en sandbox avant expiration ?

- A. 5 minutes
- B. 30 minutes
- C. 2 heures
- D. 24 heures

---

## Partie D — Déploiement (Q16 à Q20)

### Q16. Pour un workflow `isCritical = true`, quelle stratégie de déploiement est **obligatoirement** assortie de 4-eyes ?

- A. DIRECT
- B. SHADOW
- C. BLUE_GREEN
- D. Aucune

### Q17. Que signifie la règle 4-eyes ?

- A. Un même utilisateur saisit son login 2 fois pour confirmer.
- B. Deux utilisateurs distincts doivent valider le déploiement (auteur + approbateur).
- C. Quatre utilisateurs doivent approuver.
- D. Une vérification automatisée par 4 outils différents.

### Q18. Un rollback en stratégie DIRECT doit s'exécuter en moins de :

- A. 5 secondes
- B. 30 secondes
- C. 60 secondes
- D. 5 minutes

### Q19. En stratégie SHADOW, que fait la nouvelle version du workflow ?

- A. Sert directement le dashboard en remplaçant l'ancienne.
- B. Tourne en parallèle de l'ancienne sans servir le trafic (mesure silencieuse).
- C. Est mise en pause jusqu'à validation.
- D. N'est jamais exécutée.

### Q20. Peut-on s'auto-approuver dans une procédure 4-eyes ?

- A. Oui, si on a le rôle `analytics:admin`.
- B. Oui, pour les workflows non critiques.
- C. Non, le système refuse systématiquement.
- D. Oui, après un délai de 24 h.

---

## Partie E — Observabilité et troubleshooting (Q21 à Q25)

### Q21. Quel algorithme de hash est utilisé pour la chaîne d'audit (cf. ADR-005) ?

- A. SHA-256
- B. MD5
- C. BLAKE3
- D. CRC32

### Q22. Une `driftPolicy` indiquant `onRemovedField: BLOCK` signifie :

- A. Que le moteur ignore silencieusement les suppressions.
- B. Que tout retrait de champ source bloque la simulation et le déploiement jusqu'à résolution.
- C. Que les champs sont automatiquement re-créés vides.
- D. Que la suppression est traduite en `IGNORE` au déploiement.

### Q23. Quelle commande CLI permet de vérifier l'intégrité de la chaîne d'audit ?

- A. `analytics-cli audit verify --since <date>`
- B. `analytics-cli check-hash`
- C. `analytics-cli blake3 --validate`
- D. `analytics-cli audit-integrity`

### Q24. Un événement d'audit contient quels champs minimum (2 réponses) ?

- A. `actor` et `hash`
- B. `password` et `secret`
- C. `previousHash` (chaînage)
- D. `costEstimate` en FCFA

### Q25. Quel rôle Keto est nécessaire pour lancer un audit BLAKE3 ?

- A. `analytics:viewer`
- B. `analytics:editor`
- C. `analytics:auditor`
- D. `analytics:admin`

---

## Annexe — Corrigé

> **À ne consulter qu'après avoir terminé.**

| Question | Bonne réponse | Justification courte |
|---|---|---|
| Q1 | **B** | Un workflow est déclaratif, versionné, et compose source/pipeline/KPI/viz/output. |
| Q2 | **C** | 8 sub-projets : VOUCHERS, E_TICKET, ETAT_CIVIL, SOGESY, HOSPITAL, FASO_KALAN, ALT_MISSION, E_SCHOOL. |
| Q3 | **B** | `less_better` : valeur meilleure quand faible (délais, incidents, erreurs). |
| Q4 | **D** | Excel local par drag-and-drop **n'est pas** une source — il faut passer par S3 sovereign. |
| Q5 | **B** | Un output est une destination (dashboard, email, export, Metabase). |
| Q6 | **C** | 5 catégories : Source, Transformation, KPI, Visualisation, Output. |
| Q7 | **B** | Ctrl+Enter lance la simulation. |
| Q8 | **B** | Lecture seule par défaut sur les workflows déployés. |
| Q9 | **C** | 4 heures d'inactivité avant expiration locale. |
| Q10 | **B** | Le DAG est acyclique orienté (Directed Acyclic Graph). |
| Q11 | **B** | STRATIFIED préserve les proportions par catégorie. |
| Q12 | **C** | BOUNDARY_VALUES = min, max, nulls, doublons. |
| Q13 | **B** | Vert = valeur identique (modulo tolérance). |
| Q14 | **B** | FULL = bench final, lent, à n'utiliser qu'une fois. |
| Q15 | **B** | 30 minutes (warning « simulation expirée »). |
| Q16 | **C** | BLUE_GREEN + `isCritical = true` → 4-eyes obligatoire. |
| Q17 | **B** | 2 utilisateurs distincts (auteur + approbateur). |
| Q18 | **C** | SLO rollback DIRECT < 60 s. |
| Q19 | **B** | SHADOW = mesure silencieuse, ne sert pas le trafic. |
| Q20 | **C** | Auto-approbation systématiquement refusée. |
| Q21 | **C** | BLAKE3 (ADR-005). |
| Q22 | **B** | BLOCK = bloque simulation et déploiement jusqu'à résolution. |
| Q23 | **A** | `analytics-cli audit verify --since <date>`. |
| Q24 | **A et C** | `actor`, `hash`, et `previousHash` (chaînage). |
| Q25 | **C** | `analytics:auditor`. |

---

## Barème

| Bonnes réponses | Décision |
|---|---|
| 23 / 25 ou + | **Mention « Avec félicitations »** + certification immédiate. |
| 18 / 25 à 22 / 25 | Certification stable. |
| 14 / 25 à 17 / 25 | Certification conditionnelle + rattrapage 2h. |
| < 14 / 25 | Refaire la formation complète (cas exceptionnel). |

> **Note** : la Q24 est une question à 2 bonnes réponses ; elle compte pour 1 point uniquement si **les 2** sont cochées (A et C). Sinon, 0 point pour cette question.

---

*Fin du QCM final.*
