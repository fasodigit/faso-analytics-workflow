# Formation FASO-ANALYTICS-WORKFLOW — 2 jours × 8 analystes métier référents

> **Version** : 1.0.0 — 2026-05-11
> **Audience** : analystes métier sans bagage développement, avec exposition préalable à un outil BI (Metabase, Tableau, Power BI) ou tableaux croisés dynamiques Excel.
> **Référence ultraplan** : §10 Phase 4 — Livrable T.

---

## 1. Objectifs pédagogiques

À l'issue de la formation, chaque participant doit être capable de :

1. **Créer un workflow analytique complet** depuis l'interface visuelle (palette + canvas DAG + paramètres), sans assistance d'un développeur.
2. **Lire, comprendre et faire évoluer un workflow existant** au format JSON conforme au schéma `workflow-v1.json` (cf. ADR-002).
3. **Définir un KPI métier** : choisir l'expression d'agrégation, le format d'affichage, la polarité (`more_better` / `less_better`), les seuils warning/critical.
4. **Sélectionner la visualisation adaptée** parmi les 19 types disponibles (BAR, DONUT, LINE, CHOROPLETH_BF, COMBO_DUAL_AXIS, KPI_TILE, etc.) selon le message métier.
5. **Lancer une simulation** sur un échantillon (sample strategies : `RANDOM`, `STRATIFIED`, `BOUNDARY_VALUES`, `FULL`) et **lire le diff** vs la version `DEPLOYED`.
6. **Détecter et résoudre un schema drift** (champ ajouté, type modifié, champ renommé) en mode interactif.
7. **Déployer un workflow** selon les 3 stratégies (`DIRECT`, `SHADOW`, `BLUE_GREEN`), comprendre la règle 4-eyes et savoir effectuer un **rollback en moins de 60 secondes**.
8. **Lire les logs et la chaîne d'audit BLAKE3** (cf. ADR-005) pour diagnostiquer un workflow défaillant ou justifier une décision lors d'un audit Cour des Comptes.

---

## 2. Profil des 8 participants prévus

Un référent par sous-projet — chacun reste le **point de contact privilégié** de son équipe métier après la formation.

| # | Sous-projet | Domaine métier | Profil cible | Référent (placeholder) |
|---|---|---|---|---|
| 1 | **VOUCHERS** | Distribution intrants agricoles (PAGSI, SONAGESS) | Statisticien-économiste DGEAP | `TBD-vouchers@gov.bf` |
| 2 | **E_TICKET** | Billetterie événementielle | Gestionnaire de salle | `TBD-eticket@gov.bf` |
| 3 | **ETAT_CIVIL** | Actes naissance / mariage / décès | Cadre A registre national | `TBD-etat-civil@gov.bf` |
| 4 | **SOGESY** | Gestion stocks pharmaceutiques | Pharmacien CAMEG | `TBD-sogesy@gov.bf` |
| 5 | **HOSPITAL** | Surveillance épidémiologique (RESUREP-26) | Médecin DLM | `TBD-hospital@gov.bf` |
| 6 | **FASO_KALAN** | Plateforme apprentissage scolaire | Enseignant inspecteur MENA | `TBD-faso-kalan@gov.bf` |
| 7 | **ALT_MISSION** | Missions terrain / déplacement | Logisticien DRH | `TBD-alt-mission@gov.bf` |
| 8 | **E_SCHOOL** | Gestion scolarité | Cadre A statistique MENA | `TBD-e-school@gov.bf` |

> **Pré-requis attendus** : chaque participant doit déjà avoir un compte fonctionnel sur l'environnement de **pré-production** (login Kratos OIDC) et avoir validé le binding RBAC à son sub-projet via la console Keto.

---

## 3. Logistique

### 3.1. Calendrier

- **Format** : 2 journées consécutives, 9h00 – 17h30 (locale Africa/Ouagadougou).
- **Lieu** : salle de formation Ministère Économie Numérique, immeuble FASO DIGITALISATION, Ouagadougou.
- **Capacité** : 8 participants + 2 instructeurs + 1 observateur.

### 3.2. Matériel requis

| Élément | Quantité | Remarque |
|---|---|---|
| Poste de travail (PC portable) | 8 (1 / participant) | Linux ou Windows ; Chrome / Firefox à jour ; accès SSH au bastion pré-prod. |
| Vidéoprojecteur HDMI + écran | 1 | Résolution 1920×1080 minimum pour lecture confortable du canvas DAG. |
| Paperboard + marqueurs | 1 | Schématiser le DAG manuellement en cas de blocage. |
| Connexion internet stable | obligatoire | Bande passante symétrique ≥ 20 Mbps. |
| Accès au sub-projet pré-prod | 8 | Vérifié J-7 par l'équipe SecOps. |
| Café / eau / pause déjeuner | 2 jours | Logistique RH. |

### 3.3. Pré-requis individuels (à valider J-3)

- [ ] Avoir lu intégralement `docs/user-guide/01-prise-en-main.typ` (compilation PDF disponible auprès du référent formation).
- [ ] Disposer d'un compte Kratos actif (test de login obligatoire avant J1).
- [ ] Avoir reçu l'email d'ouverture des droits sur le sub-projet associé.
- [ ] Apporter un casque ou écouteurs (sessions de TP individuelles).
- [ ] Avoir installé un client de messagerie de secours pour le suivi post-formation.

---

## 4. Calendrier détaillé

### Jour 1 — Fondamentaux

| Module | Heure | Titre | Format | Durée |
|---|---|---|---|---|
| M1 | 09:00 – 09:45 | Bienvenue + tour de table + vocabulaire métier | Présentation + interaction | 45 min |
| M2 | 09:45 – 10:30 | Architecture C4 simplifiée — pourquoi un module transversal | Slides + Q/R | 45 min |
| — | 10:30 – 10:45 | Pause café | — | 15 min |
| M3 | 10:45 – 11:30 | Tour guidé de l'interface : palette, canvas DAG, paramètres | Démo live | 45 min |
| M4 | 11:30 – 12:30 | TP1 : Reproduire un workflow existant (PAGSI sur sample) | Hands-on | 1 h |
| — | 12:30 – 14:00 | Pause déjeuner | — | 1 h 30 |
| M5 | 14:00 – 15:00 | Sources et transformations — déclarer, paramétrer | Démo + TP2 | 1 h |
| M6 | 15:00 – 16:00 | KPI : définition, format, polarité, target | Démo + TP3 | 1 h |
| — | 16:00 – 16:15 | Pause | — | 15 min |
| M7 | 16:15 – 17:00 | Visualisations — choisir le bon graphique | Démo des 19 types | 45 min |
| M8 | 17:00 – 17:30 | Débriefing J1 + devoir maison | Rétrospective | 30 min |

Voir le détail dans [`jour-1-fondamentaux.md`](./jour-1-fondamentaux.md).

### Jour 2 — Cas pratiques

| Module | Heure | Titre | Format | Durée |
|---|---|---|---|---|
| M9 | 09:00 – 10:00 | Simulation : sample strategies + diff vs DEPLOYED | Démo + TP4 | 1 h |
| — | 10:00 – 10:15 | Pause café | — | 15 min |
| M10 | 10:15 – 11:30 | Cas pratique 1 : PAGSI volumes Région × Céréale | Hands-on intégral | 1 h 15 |
| — | 11:30 – 12:30 | Cas pratique 2 : RESUREP-26 (santé animale, SurveyMonkey source) | Hands-on | 1 h |
| — | 12:30 – 14:00 | Pause déjeuner | — | 1 h 30 |
| M11 | 14:00 – 15:00 | Schema drift : détection, résolution interactive | TP5 | 1 h |
| M12 | 15:00 – 16:00 | Déploiement : DIRECT / SHADOW / BLUE_GREEN + rollback | Démo + TP6 | 1 h |
| — | 16:00 – 16:15 | Pause | — | 15 min |
| M13 | 16:15 – 17:00 | Audit BLAKE3 : vérification offline + lecture des logs | Démo CLI | 45 min |
| M14 | 17:00 – 17:30 | QCM + remise de certificats + plan d'accompagnement 30 jours | Clôture | 30 min |

Voir le détail dans [`jour-2-cas-pratiques.md`](./jour-2-cas-pratiques.md).

---

## 5. Référents instructeurs (placeholder noms)

| Rôle | Nom (placeholder) | Domaine |
|---|---|---|
| Instructeur principal | `TBD-instructeur-1@anthropic.com` | Architecture + déploiement |
| Instructeur secondaire | `TBD-instructeur-2@anthropic.com` | Visualisations + UX |
| Observateur métier | `TBD-observateur@gov.bf` | Validation pédagogique |
| Support technique sur place | `TBD-support@gov.bf` | Tickets bloquants + réseau |

> Les noms définitifs seront confirmés à J-15 par mémo interne.

---

## 6. Évaluation

L'obtention du **certificat de référent FASO-ANALYTICS-WORKFLOW** repose sur trois critères cumulatifs :

1. **Présence assidue** aux 14 modules (les absences ≥ 1 module entraînent une session de rattrapage individuelle).
2. **Validation des 6 TPs structurants** (TP1 à TP6) — chaque TP est annoté par un instructeur (note binaire : validé / à reprendre).
3. **Note ≥ 70 %** au QCM final (18/25 — cf. [`qcm-final.md`](./qcm-final.md)).

En cas d'échec sur l'un des trois critères, une session de rattrapage de 4 heures est planifiée dans le mois suivant.

---

## 7. Suivi post-formation

Un **plan d'accompagnement 30 jours** est mis en place pour chaque référent dès J3 :

- **Semaine 1** : checkpoints quotidiens de 30 minutes.
- **Semaines 2-3** : 2 checkpoints par semaine.
- **Semaine 4** : 1 checkpoint hebdo + rétrospective.

Critère final : **1 workflow réel déployé en SHADOW** par le référent, sans aide d'un développeur. Détail dans [`plan-accompagnement-30j.md`](./plan-accompagnement-30j.md).

---

## 8. Liens utiles

- Plan ultraplan §10 : `docs/PLAN-ULTRAPLAN.md`
- Schémas JSON : `schemas/workflow-v1.json` + `schemas/examples/`
- ADR-001 à ADR-006 : `docs/adr/`
- Guide utilisateur Typst : `docs/user-guide/01-prise-en-main.typ`
- Runbook ops : `docs/runbook/runbook-ops.md`
- Migrations production (workflows réels) : `migrations/production/` *(créé par l'agent M ; à ce jour : PAGSI, RESUREP, AgriVoucher — d'autres viendront en Phase 5)*

> **Risque de désync** : si l'agent M renomme un fichier de workflow, les références ci-dessous dans `exercises/` doivent être ajustées. Le nommage actuel cible : `migrations/production/<workflow-slug>-v<semver>.json`.

---

## 9. Glossaire express

| Terme | Définition courte |
|---|---|
| Workflow | Recette analytique versionnée (source → pipeline → KPIs → visualisations → outputs). |
| DAG | Directed Acyclic Graph — représentation visuelle non cyclique des étapes du pipeline. |
| Sub-projet | Cloisonnement métier (VOUCHERS, HOSPITAL, etc.). Un workflow appartient à un et un seul sub-projet. |
| KPI | Indicateur clé de performance, exprimé par une fonction d'agrégation (SUM, AVG, COUNT) et un format. |
| Polarité | Sens du KPI : `more_better` (vente, couverture) ou `less_better` (incidents, latence). |
| Drift de schéma | Évolution du schéma source qui pourrait casser le workflow (nouveau champ, type changé, suppression). |
| Sample strategy | Méthode d'échantillonnage en simulation : RANDOM, STRATIFIED, BOUNDARY_VALUES, FULL. |
| Stratégie de déploiement | DIRECT (remplacement), SHADOW (en parallèle silencieux), BLUE_GREEN (bascule progressive). |
| 4-eyes | Règle exigeant la validation d'un second utilisateur pour les workflows `isCritical = true`. |
| BLAKE3 | Fonction de hash utilisée dans la chaîne d'audit infalsifiable (cf. ADR-005). |

---

## 10. Sommaire des fichiers de cette formation

```
docs/training/
├── README.md                       <-- ce fichier
├── jour-1-fondamentaux.md          <-- déroulé pédagogique J1
├── jour-2-cas-pratiques.md         <-- déroulé pédagogique J2
├── qcm-final.md                    <-- 25 questions + corrigé
├── plan-accompagnement-30j.md      <-- suivi post-formation
└── exercises/
    ├── tp1-reproduire-pagsi.md
    ├── tp2-source-transformations.md
    ├── tp3-kpi-polarite.md
    ├── tp4-simulation-diff.md
    ├── tp5-schema-drift-resolution.md
    ├── tp6-deploy-shadow-rollback.md
    ├── tp7-cas-pratique-resurep.md
    ├── tp8-combo-dual-axis.md
    ├── tp9-export-pdf-excel.md
    └── tp10-troubleshooting-quiz.md
```

---

*Fin du document — README master formation.*
