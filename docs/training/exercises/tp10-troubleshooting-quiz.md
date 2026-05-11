# TP10 — Troubleshooting (sans réponse — évaluation finale)

> **Durée** : 60 minutes.
> **Niveau** : évaluation.
> **Module rattaché** : évaluation post-formation (semaine 4).
> **Important** : pas de correction fournie. La validation est faite par l'instructeur principal lors du checkpoint J+30.

---

## Contexte métier

À la fin du plan d'accompagnement 30 jours, chaque référent reçoit un workflow **délibérément cassé** par l'instructeur. Le référent doit le diagnostiquer, le réparer, et expliquer ce qu'il a corrigé. Ce TP est l'épreuve finale qui conditionne la **certification stable** du référent.

## Données fournies

- Un fichier JSON `tp10-broken-workflow.json` envoyé par email à J+25.
- 1 workflow source de référence à partir duquel l'instructeur a fabriqué le cassé.
- Accès au cluster pré-prod.

## Objectif

1. Identifier les **5 problèmes** insérés dans le fichier.
2. Les classifier par catégorie (Schema, Logique métier, KPI, Visualisation, Output).
3. Proposer une correction concrète pour chacun.
4. Soumettre la version corrigée + le journal de raisonnement (5-10 lignes par problème).

## Pas-à-pas suggéré

1. **Charger** le fichier dans la console.
2. **Lancer** la validation JSON Schema → noter les erreurs (probablement 2-3 indices visibles).
3. **Lire ligne à ligne** le fichier en se concentrant sur :
   - `metadata` : semver, isCritical, owner valides ?
   - `source` : type cohérent avec le sub-projet, `connectionRef` valide ?
   - `pipeline` : ordre des transformations cohérent ? expressions SQL parseables ?
   - `kpis` : polarité cohérente avec le sens métier ? thresholds cohérents avec polarité ?
   - `visualizations` : type cohérent avec le message métier ? encoding complet ?
   - `outputs` : refreshSec ≥ 60 ? cron valide ?
4. **Lancer** une simulation `STRATIFIED` pour repérer les erreurs runtime.
5. **Lire** le rapport d'erreurs.
6. **Corriger** chaque problème un par un, re-simuler après chaque correction.
7. **Rédiger** un journal de raisonnement (par problème, 5-10 lignes).
8. **Envoyer** la version corrigée + le journal à l'instructeur principal avant J+30 23:59.

## Critères de validation

- [ ] Les 5 problèmes ont été identifiés (≥ 4/5 pour réussite).
- [ ] Chaque correction est sémantiquement correcte (validée à la simulation).
- [ ] Le journal de raisonnement est cohérent (l'instructeur juge la qualité du diagnostic, pas seulement la correction).
- [ ] La version finale passe la validation Schema.
- [ ] La version finale passe la simulation `STRATIFIED` sans erreur.

## Indications (non exhaustives)

Sans donner les réponses, voici quelques **catégories possibles** de problèmes que l'instructeur peut insérer :

- Une polarité KPI inversée par rapport au sens métier.
- Un `groupBy` qui inclut un champ inexistant après une transformation `computed`.
- Un `refreshSec` à 30 (en dessous du minimum 60).
- Un `format.unit` incohérent avec l'expression (kg au lieu de tonnes après une conversion).
- Un type de viz incompatible avec les champs disponibles (CHOROPLETH_BF sans champ géographique).
- Une stratégie de déploiement DIRECT alors que `isCritical = true`.
- Un cron en timezone UTC alors qu'il devrait être Africa/Ouagadougou.
- Une expression SQL avec un opérateur invalide (`=` au lieu de `==`, ou inversement).

## Évaluation

- **Note ≥ 4/5 problèmes trouvés et corrigés correctement** → certification validée.
- **Note ≥ 3/5** → session de rattrapage 2h planifiée.
- **Note < 3/5** → refaire la formation complète (cas exceptionnel).

## Pas de solution dans ce fichier

Ce TP10 est **délibérément sans corrigé**. La solution est révélée individuellement par l'instructeur lors du checkpoint J+30.

> **Note pour l'instructeur** : le fichier cassé doit être généré manuellement à partir d'un workflow réel du sub-projet du référent (ex. pour le référent VOUCHERS, partir de `pagsi-volumes-region` ; pour HOSPITAL, partir de `resurep-26-cas-especes`). Au minimum 5 problèmes distincts répartis sur les 5 catégories listées plus haut.
