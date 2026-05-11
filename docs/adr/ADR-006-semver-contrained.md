# ADR-006 — Versioning contraint : SemVer avec règles métier strictes

| Field | Value |
|---|---|
| **Status** | Proposed — default per ultraplan v0.1-DRAFT |
| **Decision date** | 2026-05-11 |

## Contexte

Un workflow analytique évolue continuellement : nouveau KPI, nouvelle visualisation, ajustement de seuil, correctif. Le **versioning** doit :

1. Permettre à un utilisateur de savoir si une mise à jour est sûre (PATCH) ou breaking (MAJOR).
2. Forcer une re-simulation et une approbation selon la nature du changement.
3. Tracer la lignée généalogique (parent/child versions).
4. Empêcher les sauts arbitraires (`1.2.3 → 4.0.0` sans justification).

SemVer libre laisse trop de marge ; un schéma contraint impose une **grammaire métier**.

## Décision

**SemVer contraint** avec règles métier ci-dessous, vérifiées au commit côté `analytics-api`.

### Sémantique des composants

| Composant | Quand l'incrémenter |
|---|---|
| **MAJOR** | Breaking change sur le **schéma de sortie** : suppression de KPI, suppression de visualisation, renommage de field exposé, changement de type sur un KPI clé (entier → décimal arrondi), changement de polarité (more=better → less=better). |
| **MINOR** | Ajout non-breaking : nouveau KPI, nouvelle visualisation, nouvelle source, nouvelle transformation interne dont le résultat exposé ne casse rien. |
| **PATCH** | Ajustement de paramètres internes : modification d'un seuil, correction de format (`#,##0` → `#,##0.0`), refactor interne sans impact sur les consommateurs. |

### Pré-release (`-draft.N`, `-rc.N`)

Toute version en cours d'édition est `-draft.<N>`. Une version validée par un approbateur passe en `-rc.<N>`. Après déploiement réussi, la version perd son suffixe (`1.3.0-rc.1` → `1.3.0`).

### Règles strictes

1. **Pas de saut arbitraire** : seul `+1` accepté sur le composant qui s'incrémente. Les autres composants sont remis à zéro :
   - `1.2.7 → 2.0.0` (MAJOR + reset MINOR/PATCH) ✓
   - `1.2.7 → 1.3.0` (MINOR + reset PATCH) ✓
   - `1.2.7 → 1.2.8` (PATCH) ✓
   - `1.2.7 → 3.0.0` ✗ (refusé par validation)
   - `1.2.7 → 1.5.0` ✗ (refusé)
2. **Détection automatique de breaking** : si le diff entre version N et N+1 supprime un KPI ou une visualisation, ou si un changement détecté correspond à une règle MAJOR, et que la nouvelle version est `MINOR` ou `PATCH`, **le serveur refuse le commit** avec un message expliquant le composant attendu.
3. **Re-simulation obligatoire** selon le composant :
   - MAJOR → re-simulation **obligatoire** (sample ≥ 1 000 lignes), comparaison vs version DEPLOYED précédente, **4-eyes approval obligatoire**.
   - MINOR → re-simulation obligatoire, comparaison vs version précédente, approbation single sauf si workflow `is_critical = true`.
   - PATCH → re-simulation **fortement recommandée** mais peut être skippée pour les hotfix avec approbation explicite par tag `--skip-simulation`.
4. **Délai max simulation → déploiement (Q9)** : 7 jours. Au-delà, re-simulation forcée car le schéma source peut avoir bougé.

### Parent / child

Chaque version pointe vers son `parent_workflow_id` (table `workflows`) ET stocke `blake3_parent` (hash de la version précédente du même workflow). Une duplication d'un autre workflow utilise `parent_workflow_id = source_id` mais commence à `1.0.0-draft.1` (lignée différente, pas héritage de version).

## Justification

1. **Lisibilité opérationnelle** : un opérateur peut décider d'auto-mettre à jour les workflows à toutes les PATCH publiées, mais demande validation pour MINOR, et bloque les MAJOR pour revue.
2. **Sécurité analytique** : impossible de "casser silencieusement" un dashboard en glissant une suppression de KPI sous une MINOR. La détection automatique force la classification.
3. **Auditabilité** : `audit_log` montre la chronologie des versions avec les composants incrémentés, ce qui éclaire les revues post-incident.
4. **Cohérence plateforme** : les autres modules FASO DIGITALISATION (ARMAGEDDON, KAYA, faso-mem) adoptent SemVer contraint. Décision homogène.

## Conséquences

### Positives

- Pas d'ambiguïté sur la sévérité d'un upgrade.
- Détection précoce de breaking change (avant déploiement).
- Audit narratif structuré (un `git log` du workflow est lisible).

### Négatives

- Surcouche de validation côté API (~150 lignes Java) : test unitaire critique.
- Frustration possible d'un développeur qui voudrait sauter `1.2.7 → 1.4.0` pour aligner avec une version externe. Mitigation : message d'erreur explicite + procédure d'override approuvée par un super-admin.
- Risque qu'un breaking change soit mal détecté par l'auto-checker → faux négatif. Mitigation : revue manuelle de la classification proposée dans l'UI avant submit.

## Conditions de réexamen

- Si > 5 % des commits sont rejetés pour cause de classification erronée → revoir l'auto-checker.
- Si un cas d'usage légitime nécessite un saut de version (alignement marketing avec un sous-projet) → introduire un override `--bump-to <version> --reason <text>` audité.

## Référence

- SemVer 2.0.0 : <https://semver.org/spec/v2.0.0.html>
- Étude de variantes : <https://en.wikipedia.org/wiki/Software_versioning>
