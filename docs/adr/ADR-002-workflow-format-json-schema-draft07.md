# ADR-002 — Format de workflow : JSON Schema Draft-07

| Field | Value |
|---|---|
| **Status** | Proposed — default per ultraplan v0.1-DRAFT |
| **Decision date** | 2026-05-11 |

## Contexte

Un workflow analytique doit être :

- **sérialisable** (persistance YugabyteDB en JSONB, export YAML pour Git review),
- **validable** (refus immédiat des définitions malformées),
- **versionnable** (SemVer contraint — cf. ADR-006),
- **interopérable** (parsing depuis Java, Rust, TypeScript),
- **outillable** (génération de types, génération d'UI Formly, diff structurel).

## Options évaluées

| Option | Pour | Contre |
|---|---|---|
| **A. JSON Schema Draft-07** | Standard mature, écosystème complet (Ajv, jsonschema-rs, python-jsonschema), génération de TS types (json-schema-to-typescript), pilotage de Formly natif, support Open API Spec, écosystème plateforme déjà familier (6 schémas v3 existants côté plateforme) | Verbosité; certains patterns avancés nécessitent Draft 2019-09+ |
| **B. JSON Schema Draft 2020-12** | Plus expressif (`unevaluatedProperties`, `$dynamicAnchor`) | Support outils Java/Rust encore inégal en 2026, surcharge sans bénéfice immédiat |
| **C. Protobuf** | Compact, bin format, evolutions champ-par-champ | Pas de validation structurelle riche (oneof limité), pas idéal pour validation côté front, force la copie côté JSONB |
| **D. Avro** | Validation schéma + évolution arrière/avant | Écosystème principalement Kafka/Hadoop, moins outillé front-end |
| **E. CUE** | Validation forte, typage gradué, génération de doc | Adoption faible côté équipe, écosystème immature |

## Décision

**Option A — JSON Schema Draft-07**, cohérent avec les 6 schémas v3 déjà produits sur la plateforme FASO DIGITALISATION.

Le schéma maître vit dans `schemas/workflow-v1.json` et est servi sous `https://schemas.faso-digitalisation.bf/analytics/workflow/v1.0.json`.

## Justification

1. **Cohérence plateforme** : 6 schémas v3 déjà déployés en Draft-07 ; pas de raison de bifurquer.
2. **Outillage complet** : `json-schema-to-typescript` produit les types TS consommés par Angular + Formly. `jsonschema-rs` (Rust) valide côté `analytics-engine`. `everit-json-schema` (Java) valide côté `analytics-api`.
3. **Capacités suffisantes** : Draft-07 supporte `oneOf` / `anyOf` / `allOf` / `if-then-else` (utilisé pour `COMBO_DUAL_AXIS` qui impose `leftAxis` + `rightAxis`).
4. **Audit & diff** : la canonicalisation JCS RFC 8785 (avant hash BLAKE3) est triviale sur JSON ; bien plus complexe pour Avro/Protobuf.
5. **Migration future** : si Draft 2020-12 devient indispensable, la migration est syntactique et outillée (`ajv-cli convert`).

## Conséquences

### Positives

- 1 source de vérité pour la définition workflow : `schemas/workflow-v1.json`.
- Génération automatique :
  - TS types via CI : `npx json-schema-to-typescript schemas/workflow-v1.json > services/analytics-frontend/src/app/shared/workflow.types.ts`.
  - Java POJOs via `jsonschema2pojo` : `mvn jsonschema2pojo:generate`.
  - Rust types via `typify` (crate Oxide).
- UI Formly pilotée directement par le schéma : si on ajoute un champ optionnel `metadata.cost_center`, le formulaire de création gagne ce champ sans coder.

### Négatives

- Verbosité : un schéma complet de visualisation `COMBO_DUAL_AXIS` requiert ~80 lignes JSON Schema pour exprimer la contrainte des 2 axes.
- Tests outillés requis pour vérifier que les validators Java / Rust / TS produisent les mêmes verdicts (Pact contract testing sur l'endpoint `/validate`).

## Conventions imposées

1. Tous les nouveaux schémas analytiques **doivent** :
   - Inclure `$schema`, `$id`, `title`.
   - Suivre kebab-case URL (`https://schemas.faso-digitalisation.bf/analytics/<name>/v<semver>.json`).
   - Documenter chaque propriété via `description`.
   - Définir `additionalProperties: false` au niveau objet racine.
2. La canonicalisation pour hashing utilise **JCS RFC 8785** (JSON Canonicalization Scheme) ; implémentation via `jsoncons` (C++/Java bindings) ou `serde_jcs` (Rust).
3. Pas de `$ref` distant (tous les `$ref` sont internes au document `#/definitions/...`).

## Conditions de réexamen

- Si un cas d'usage analytique nécessite `$dynamicAnchor` (héritage récursif), migrer vers Draft 2020-12.
- Si la taille du schéma dépasse 10 000 lignes, considérer un découpage modulaire (`workflow-source.json`, `workflow-transform.json`, etc.).

## Référence

- JSON Schema Draft-07 : <https://json-schema.org/draft-07/schema>
- JSON Canonicalization Scheme (JCS) RFC 8785 : <https://www.rfc-editor.org/rfc/rfc8785.html>
- Outils :
  - `ajv` (Node) : <https://ajv.js.org>
  - `jsonschema-rs` (Rust) : <https://github.com/Stranger6667/jsonschema-rs>
  - `everit-json-schema` (Java) : <https://github.com/everit-org/json-schema>
