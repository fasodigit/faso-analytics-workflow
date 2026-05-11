// Module `compile` — parsing d'une définition workflow (JSON v1) vers un plan
// DataFusion. Cohérent ADR-001 + schemas/workflow-v1.json.
//
// Convention : un plan compilé est un `LogicalPlan` DataFusion stocké en cache
// LRU dans `PlanRegistry` (clé = `plan_id` UUID). L'`Execute` RPC l'invoque
// avec un `SourceHandle` qui matérialise le plan en `RecordBatch`s.

use std::collections::HashMap;
use std::sync::Arc;

use datafusion::common::DFSchema;
use datafusion::logical_expr::{col, lit, Expr, LogicalPlan};
use datafusion::prelude::*;
use serde::{Deserialize, Serialize};
use tracing::{debug, instrument};
use uuid::Uuid;

use crate::error::EngineError;
use crate::transforms::{
    self, JoinKey, WindowFrameDef, WindowOrder,
};

// ----------------------------------------------------------------------------
// Représentation typée de la définition workflow (subset minimal v1.0).
// Le subset couvre les types nécessaires pour les 8 exemples livrés en Phase 0 :
//   source: yugabyte | dragonfly | upload
//   pipeline: filter | aggregate | computed
//   kpis, visualizations, outputs : ignorés ici (rendus côté API/frontend).
// ----------------------------------------------------------------------------

#[derive(Debug, Clone, Deserialize, Serialize)]
pub struct WorkflowDefinition {
    #[serde(rename = "apiVersion")]
    pub api_version: String,
    pub kind:        String,
    pub metadata:    WorkflowMetadata,
    pub spec:        WorkflowSpec,
}

#[derive(Debug, Clone, Deserialize, Serialize)]
pub struct WorkflowMetadata {
    pub name:        String,
    #[serde(rename = "subProject")]
    pub sub_project: String,
    pub semver:      String,
    #[serde(rename = "isCritical", default)]
    pub is_critical: bool,
}

#[derive(Debug, Clone, Deserialize, Serialize)]
pub struct WorkflowSpec {
    pub source:   SourceDef,
    #[serde(default)]
    pub pipeline: Vec<TransformDef>,
}

#[derive(Debug, Clone, Deserialize, Serialize)]
#[serde(tag = "type", rename_all = "lowercase")]
pub enum SourceDef {
    Yugabyte    { schema: String, table: String, #[serde(default)] sql_predicate: Option<String> },
    Dragonfly   { #[serde(rename = "keyPattern")] key_pattern: String },
    Upload      { format: String, #[serde(rename = "objectKey")] object_key: Option<String> },
    // Other source kinds (Kobo, SurveyMonkey, Metabase, Redpanda) deferred to Phase 2.
    #[serde(other)]
    Other,
}

#[derive(Debug, Clone, Deserialize, Serialize)]
#[serde(tag = "kind", rename_all = "snake_case")]
pub enum TransformDef {
    Filter    { id: String, expression: String },
    Aggregate { id: String, #[serde(default)] group_by: Vec<String>, aggregations: Vec<AggDef> },
    Computed  { id: String, alias: String, expression: String, #[serde(default)] r#type: Option<String> },

    // Phase 2 — transforms ajoutés (cf. schemas/workflow-v1.json definitions).
    Join {
        id:        String,
        right:     String,
        on:        Vec<JoinKey>,
        #[serde(rename = "joinType")]
        join_type: String,    // INNER/LEFT/RIGHT/FULL_OUTER/SEMI/ANTI
    },
    GroupBy {
        id:     String,
        fields: Vec<String>,
    },
    Pivot {
        id:           String,
        #[serde(rename = "rowFields")]
        row_fields:   Vec<String>,
        #[serde(rename = "columnField")]
        column_field: String,
        #[serde(rename = "valueField")]
        value_field:  String,
        aggregation:  String,
    },
    Window {
        id:           String,
        alias:        String,
        function:     String,
        #[serde(rename = "partitionBy", default)]
        partition_by: Vec<String>,
        #[serde(rename = "orderBy", default)]
        order_by:     Option<Vec<WindowOrder>>,
        #[serde(default)]
        frame:        Option<WindowFrameDef>,
    },
    Outlier {
        id:          String,
        field:       String,
        strategy:    String,
        #[serde(rename = "lowerBound", default)]
        lower_bound: Option<f64>,
        #[serde(rename = "upperBound", default)]
        upper_bound: Option<f64>,
        #[serde(default)]
        action:      Option<String>,
    },
    Normalize {
        id:     String,
        field:  String,
        method: String,    // ZSCORE/MIN_MAX/RANK/LOG
    },
    Recode {
        id:      String,
        field:   String,
        alias:   String,
        mapping: serde_json::Map<String, serde_json::Value>,
        #[serde(default)]
        default: Option<serde_json::Value>,
    },

    // Variantes inconnues → catch-all (préserve la compatibilité forward).
    #[serde(other)]
    Other,
}

#[derive(Debug, Clone, Deserialize, Serialize)]
pub struct AggDef {
    pub alias:    String,
    pub function: String,  // SUM, AVG, COUNT, COUNT_DISTINCT, MIN, MAX, MEDIAN
    pub field:    String,
}

// ----------------------------------------------------------------------------
// Compilation : WorkflowDefinition -> LogicalPlan
// ----------------------------------------------------------------------------

/// Cache LRU des plans compilés (capacité 256).
/// Phase 2 : remplacer par une `lru::LruCache` derrière `Arc<RwLock<>>`.
#[derive(Default)]
pub struct PlanRegistry {
    plans: HashMap<Uuid, CompiledPlan>,
}

#[derive(Clone, Debug)]
pub struct CompiledPlan {
    pub plan_id:       Uuid,
    pub logical_plan:  LogicalPlan,
    pub output_schema: Arc<DFSchema>,
    pub source_def:    SourceDef,
    pub steps_descr:   Vec<PlanStepDescription>,
}

#[derive(Clone, Debug)]
pub struct PlanStepDescription {
    pub id:              String,
    pub kind:            String,
    pub description:     String,
    pub estimated_rows:  u64,
    pub estimated_cost:  f64,
}

impl PlanRegistry {
    pub fn new() -> Self { Self::default() }

    pub fn get(&self, plan_id: &Uuid) -> Option<CompiledPlan> {
        self.plans.get(plan_id).cloned()
    }

    pub fn insert(&mut self, plan: CompiledPlan) {
        self.plans.insert(plan.plan_id, plan);
    }
}

#[instrument(skip(ctx, definition_jcs))]
pub async fn compile(
    ctx: &SessionContext,
    definition_jcs: &str,
) -> Result<CompiledPlan, EngineError> {
    let def: WorkflowDefinition = serde_json::from_str(definition_jcs)
        .map_err(|e| EngineError::InvalidDefinition(e.to_string()))?;

    if def.api_version != "analytics.faso/v1" {
        return Err(EngineError::InvalidDefinition(format!(
            "apiVersion attendu 'analytics.faso/v1', reçu '{}'",
            def.api_version
        )));
    }
    if def.kind != "AnalyticsWorkflow" {
        return Err(EngineError::InvalidDefinition(format!(
            "kind attendu 'AnalyticsWorkflow', reçu '{}'",
            def.kind
        )));
    }

    let plan_id = Uuid::now_v7();
    let mut steps_descr = Vec::new();

    // 1. Source -> DataFrame avec schéma déclaré (Phase 1 : schéma sera fourni
    //    par Execute via SourceHandle ; pour Compile on travaille sur une
    //    table "source" placeholder enregistrée côté engine.)
    debug!(?def.spec.source, "compiling source step");
    steps_descr.push(PlanStepDescription {
        id:             "source".to_string(),
        kind:           "source".to_string(),
        description:    describe_source(&def.spec.source),
        estimated_rows: 1_000_000,    // Phase 2 : récupérer via stats YB
        estimated_cost: 1.0,
    });

    let mut df = ctx.table("source").await
        .map_err(|e| EngineError::Compilation(format!("source table not registered: {}", e)))?;

    // Map step_id -> DataFrame pour permettre aux joins de référencer une
    // étape précédente (cf. transforms::compile_join).
    let mut step_outputs: HashMap<String, DataFrame> = HashMap::new();

    // 2. Pipeline transformations
    for (idx, transform) in def.spec.pipeline.iter().enumerate() {
        match transform {
            TransformDef::Filter { id, expression } => {
                let expr = parse_filter_expr(expression, df.schema())?;
                df = df.filter(expr)
                    .map_err(|e| EngineError::Compilation(format!("filter[{}]: {}", id, e)))?;
                steps_descr.push(PlanStepDescription {
                    id:             id.clone(),
                    kind:           "filter".to_string(),
                    description:    format!("WHERE {}", expression),
                    estimated_rows: 500_000,
                    estimated_cost: 1.0 + idx as f64,
                });
                step_outputs.insert(id.clone(), df.clone());
            }
            TransformDef::Aggregate { id, group_by, aggregations } => {
                let group_exprs: Vec<Expr> = group_by.iter().map(col).collect();
                let agg_exprs: Vec<Expr> = aggregations.iter()
                    .map(build_aggregate_expr)
                    .collect::<Result<_, _>>()?;
                df = df.aggregate(group_exprs.clone(), agg_exprs)
                    .map_err(|e| EngineError::Compilation(format!("aggregate[{}]: {}", id, e)))?;
                steps_descr.push(PlanStepDescription {
                    id:             id.clone(),
                    kind:           "aggregate".to_string(),
                    description:    format!("GROUP BY {} aggs={}", group_by.join(","), aggregations.len()),
                    estimated_rows: 1_000,
                    estimated_cost: 5.0 + idx as f64,
                });
                step_outputs.insert(id.clone(), df.clone());
            }
            TransformDef::Computed { id, alias, expression, .. } => {
                let expr = parse_filter_expr(expression, df.schema())?;
                df = df.with_column(alias, expr)
                    .map_err(|e| EngineError::Compilation(format!("computed[{}]: {}", id, e)))?;
                steps_descr.push(PlanStepDescription {
                    id:             id.clone(),
                    kind:           "computed".to_string(),
                    description:    format!("{} = {}", alias, expression),
                    estimated_rows: 500_000,
                    estimated_cost: 1.0,
                });
                step_outputs.insert(id.clone(), df.clone());
            }

            // -----------------------------------------------------------------
            // Phase 2 — transforms ajoutés.
            // Délégation à `crate::transforms::*` pour clarté.
            // -----------------------------------------------------------------
            TransformDef::Join { id, right, on, join_type } => {
                let (new_df, descr) = transforms::compile_join(
                    ctx, df, id, right, on, join_type, &step_outputs, idx,
                ).await?;
                df = new_df;
                steps_descr.push(descr);
                step_outputs.insert(id.clone(), df.clone());
            }
            TransformDef::GroupBy { id, fields } => {
                let (new_df, descr) = transforms::compile_group_by(df, id, fields, idx)?;
                df = new_df;
                steps_descr.push(descr);
                step_outputs.insert(id.clone(), df.clone());
            }
            TransformDef::Pivot { id, .. } => {
                // Phase 2 simplification : pivot natif DataFusion seulement en v44+.
                let _ = transforms::compile_pivot_deferred(id)?;
                unreachable!("compile_pivot_deferred always returns Err");
            }
            TransformDef::Window { id, alias, function, partition_by, order_by, frame } => {
                let (new_df, descr) = transforms::compile_window(
                    df,
                    id,
                    alias,
                    function,
                    partition_by,
                    order_by.as_deref(),
                    frame.as_ref(),
                    idx,
                )?;
                df = new_df;
                steps_descr.push(descr);
                step_outputs.insert(id.clone(), df.clone());
            }
            TransformDef::Outlier { id, field, strategy, lower_bound, upper_bound, action } => {
                let (new_df, descr) = transforms::compile_outlier(
                    df,
                    id,
                    field,
                    strategy,
                    *lower_bound,
                    *upper_bound,
                    action.as_deref(),
                    idx,
                )?;
                df = new_df;
                steps_descr.push(descr);
                step_outputs.insert(id.clone(), df.clone());
            }
            TransformDef::Normalize { id, field, method } => {
                let (new_df, descr) = transforms::compile_normalize(df, id, field, method, idx)?;
                df = new_df;
                steps_descr.push(descr);
                step_outputs.insert(id.clone(), df.clone());
            }
            TransformDef::Recode { id, field, alias, mapping, default } => {
                let (new_df, descr) = transforms::compile_recode(
                    df,
                    id,
                    field,
                    alias,
                    mapping,
                    default.as_ref(),
                    idx,
                )?;
                df = new_df;
                steps_descr.push(descr);
                step_outputs.insert(id.clone(), df.clone());
            }

            TransformDef::Other => {
                return Err(EngineError::Compilation(
                    "transform kind inconnu (verify schemas/workflow-v1.json)".to_string(),
                ));
            }
        }
    }

    let logical_plan = df.logical_plan().clone();
    let output_schema = Arc::new(logical_plan.schema().as_ref().clone());

    Ok(CompiledPlan {
        plan_id,
        logical_plan,
        output_schema,
        source_def: def.spec.source,
        steps_descr,
    })
}

fn describe_source(src: &SourceDef) -> String {
    match src {
        SourceDef::Yugabyte { schema, table, .. } => format!("yugabyte://{}.{}", schema, table),
        SourceDef::Dragonfly { key_pattern }     => format!("dragonfly://{}", key_pattern),
        SourceDef::Upload { format, object_key } => format!("upload://{}/{}", format, object_key.as_deref().unwrap_or("")),
        SourceDef::Other => "unknown_source".to_string(),
    }
}

/// Parse une expression SQL WHERE simple ou une expression de calcul.
/// Phase 1 : on délègue à DataFusion via SessionContext::create_logical_plan
/// sur une requête `SELECT ($expr) FROM source` puis on récupère l'expression
/// de la projection.
fn parse_filter_expr(expression: &str, _schema: &DFSchema) -> Result<Expr, EngineError> {
    // Phase 1 simplifié : on parse une comparaison binaire basique `col op literal`
    // pour pouvoir démontrer la chaîne bout en bout. Phase 2 utilisera
    // DataFusion `parse_expr` complet avec contexte de schéma.
    let parts: Vec<&str> = expression.split_whitespace().collect();
    if parts.len() == 3 {
        let lhs = col(parts[0]);
        let rhs = match parts[2].parse::<f64>() {
            Ok(n)  => lit(n),
            Err(_) => lit(parts[2].trim_matches('\'')),
        };
        let expr = match parts[1] {
            "=" | "==" => lhs.eq(rhs),
            "!=" | "<>" => lhs.not_eq(rhs),
            "<"  => lhs.lt(rhs),
            "<=" => lhs.lt_eq(rhs),
            ">"  => lhs.gt(rhs),
            ">=" => lhs.gt_eq(rhs),
            op   => return Err(EngineError::Compilation(format!("opérateur non supporté: {}", op))),
        };
        return Ok(expr);
    }
    Err(EngineError::Compilation(format!(
        "expression Phase 1 simplifiée : format attendu 'col op value', reçu '{}'",
        expression
    )))
}

fn build_aggregate_expr(agg: &AggDef) -> Result<Expr, EngineError> {
    use datafusion::functions_aggregate::expr_fn::*;
    let field = col(&agg.field);
    let e = match agg.function.as_str() {
        "SUM"           => sum(field).alias(&agg.alias),
        "AVG"           => avg(field).alias(&agg.alias),
        "COUNT"         => count(field).alias(&agg.alias),
        "COUNT_DISTINCT"=> count_distinct(field).alias(&agg.alias),
        "MIN"           => min(field).alias(&agg.alias),
        "MAX"           => max(field).alias(&agg.alias),
        f => return Err(EngineError::Compilation(format!("aggregation non supportée Phase 1: {}", f))),
    };
    Ok(e)
}

// ============================================================================
// Tests Phase 2 — couverture des 7 nouveaux transforms.
// Pattern : register_batch fake source(s), build definition JSON, call
// compile() et assert sur steps_descr / output_schema.
// ============================================================================

#[cfg(test)]
mod phase2_transform_tests {
    use super::*;
    use datafusion::arrow::datatypes::{DataType, Field, Schema};
    use datafusion::arrow::record_batch::RecordBatch;

    fn fresh_ctx_with_source() -> SessionContext {
        let ctx = SessionContext::new();
        // Source minimaliste : region (Utf8), year (Int32), value (Float64).
        let schema = Arc::new(Schema::new(vec![
            Field::new("region", DataType::Utf8,    false),
            Field::new("year",   DataType::Int32,   false),
            Field::new("value",  DataType::Float64, false),
            Field::new("category", DataType::Utf8, true),
        ]));
        let batch = RecordBatch::new_empty(schema);
        ctx.register_batch("source", batch).unwrap();
        ctx
    }

    fn fresh_ctx_with_right_table() -> SessionContext {
        let ctx = fresh_ctx_with_source();
        // Right side d'un join : mêmes clés (region, year).
        let schema = Arc::new(Schema::new(vec![
            Field::new("region",     DataType::Utf8,    false),
            Field::new("year",       DataType::Int32,   false),
            Field::new("population", DataType::Float64, false),
        ]));
        let batch = RecordBatch::new_empty(schema);
        ctx.register_batch("demographics", batch).unwrap();
        ctx
    }

    fn compile_def(ctx: &SessionContext, def_json: &str) -> Result<CompiledPlan, EngineError> {
        let rt = tokio::runtime::Builder::new_current_thread().enable_all().build().unwrap();
        rt.block_on(compile(ctx, def_json))
    }

    #[test]
    fn compile_join_inner_succeeds() {
        let ctx = fresh_ctx_with_right_table();
        let def = r#"{
            "apiVersion": "analytics.faso/v1",
            "kind": "AnalyticsWorkflow",
            "metadata": { "name": "j1", "subProject": "VOUCHERS", "semver": "1.0.0" },
            "spec": {
                "source": { "type": "yugabyte", "schema": "s", "table": "perimetre_amenage" },
                "pipeline": [
                    {
                        "kind": "join",
                        "id":   "j_demo",
                        "right":"demographics",
                        "on":   [{"left": "region", "right": "region"}, {"left": "year", "right": "year"}],
                        "joinType": "INNER"
                    }
                ]
            }
        }"#;
        let plan = compile_def(&ctx, def).expect("join compile must succeed");
        assert_eq!(plan.steps_descr.len(), 2, "source + join");
        assert_eq!(plan.steps_descr[1].kind, "join");
        assert!(plan.steps_descr[1].description.contains("INNER"));
    }

    #[test]
    fn compile_window_row_number_succeeds() {
        let ctx = fresh_ctx_with_source();
        let def = r#"{
            "apiVersion": "analytics.faso/v1",
            "kind": "AnalyticsWorkflow",
            "metadata": { "name": "w1", "subProject": "VOUCHERS", "semver": "1.0.0" },
            "spec": {
                "source": { "type": "yugabyte", "schema": "s", "table": "t" },
                "pipeline": [
                    {
                        "kind": "window",
                        "id":   "w_rn",
                        "alias":"rn",
                        "function": "ROW_NUMBER",
                        "partitionBy": ["region"],
                        "orderBy": [{"field": "year", "direction": "ASC"}]
                    }
                ]
            }
        }"#;
        let plan = compile_def(&ctx, def).expect("window compile must succeed");
        assert_eq!(plan.steps_descr.len(), 2);
        assert_eq!(plan.steps_descr[1].kind, "window");
        // L'alias "rn" doit apparaître dans le schéma de sortie.
        let fields: Vec<String> = plan.output_schema
            .iter()
            .map(|(_, f)| f.name().to_string())
            .collect();
        assert!(fields.contains(&"rn".to_string()), "fields = {:?}", fields);
    }

    #[test]
    fn compile_outlier_three_sigma_flags() {
        let ctx = fresh_ctx_with_source();
        let def = r#"{
            "apiVersion": "analytics.faso/v1",
            "kind": "AnalyticsWorkflow",
            "metadata": { "name": "o1", "subProject": "VOUCHERS", "semver": "1.0.0" },
            "spec": {
                "source": { "type": "yugabyte", "schema": "s", "table": "t" },
                "pipeline": [
                    {
                        "kind": "outlier",
                        "id":   "o_3s",
                        "field":"value",
                        "strategy": "THREE_SIGMA",
                        "action": "FLAG"
                    }
                ]
            }
        }"#;
        let plan = compile_def(&ctx, def).expect("outlier compile must succeed");
        assert_eq!(plan.steps_descr[1].kind, "outlier");
        let fields: Vec<String> = plan.output_schema
            .iter()
            .map(|(_, f)| f.name().to_string())
            .collect();
        assert!(fields.contains(&"value_is_outlier".to_string()), "fields = {:?}", fields);
    }

    #[test]
    fn compile_outlier_hard_bounds_drops() {
        let ctx = fresh_ctx_with_source();
        let def = r#"{
            "apiVersion": "analytics.faso/v1",
            "kind": "AnalyticsWorkflow",
            "metadata": { "name": "o2", "subProject": "VOUCHERS", "semver": "1.0.0" },
            "spec": {
                "source": { "type": "yugabyte", "schema": "s", "table": "t" },
                "pipeline": [
                    {
                        "kind": "outlier",
                        "id":   "o_hb",
                        "field":"value",
                        "strategy": "HARD_BOUNDS",
                        "lowerBound": 0.0,
                        "upperBound": 1000.0,
                        "action": "DROP"
                    }
                ]
            }
        }"#;
        let plan = compile_def(&ctx, def).expect("outlier hard bounds compile must succeed");
        assert_eq!(plan.steps_descr[1].kind, "outlier");
        // DROP : pas de colonne ajoutée
        let fields: Vec<String> = plan.output_schema
            .iter()
            .map(|(_, f)| f.name().to_string())
            .collect();
        assert!(!fields.contains(&"value_is_outlier".to_string()), "DROP ne doit pas créer flag");
    }

    #[test]
    fn compile_normalize_zscore_succeeds() {
        let ctx = fresh_ctx_with_source();
        let def = r#"{
            "apiVersion": "analytics.faso/v1",
            "kind": "AnalyticsWorkflow",
            "metadata": { "name": "n1", "subProject": "VOUCHERS", "semver": "1.0.0" },
            "spec": {
                "source": { "type": "yugabyte", "schema": "s", "table": "t" },
                "pipeline": [
                    { "kind": "normalize", "id": "n_zs", "field": "value", "method": "ZSCORE" }
                ]
            }
        }"#;
        let plan = compile_def(&ctx, def).expect("normalize zscore must succeed");
        assert_eq!(plan.steps_descr[1].kind, "normalize");
        let fields: Vec<String> = plan.output_schema
            .iter()
            .map(|(_, f)| f.name().to_string())
            .collect();
        assert!(fields.contains(&"value_norm".to_string()), "fields={:?}", fields);
    }

    #[test]
    fn compile_recode_case_when_succeeds() {
        let ctx = fresh_ctx_with_source();
        let def = r#"{
            "apiVersion": "analytics.faso/v1",
            "kind": "AnalyticsWorkflow",
            "metadata": { "name": "r1", "subProject": "VOUCHERS", "semver": "1.0.0" },
            "spec": {
                "source": { "type": "yugabyte", "schema": "s", "table": "t" },
                "pipeline": [
                    {
                        "kind": "recode",
                        "id":   "r_cat",
                        "field":"category",
                        "alias":"category_fr",
                        "mapping": { "A": "Agriculture", "B": "Bâtiment", "S": "Services" },
                        "default": "Inconnu"
                    }
                ]
            }
        }"#;
        let plan = compile_def(&ctx, def).expect("recode must succeed");
        assert_eq!(plan.steps_descr[1].kind, "recode");
        let fields: Vec<String> = plan.output_schema
            .iter()
            .map(|(_, f)| f.name().to_string())
            .collect();
        assert!(fields.contains(&"category_fr".to_string()), "fields={:?}", fields);
    }

    #[test]
    fn compile_group_by_distinct_succeeds() {
        let ctx = fresh_ctx_with_source();
        let def = r#"{
            "apiVersion": "analytics.faso/v1",
            "kind": "AnalyticsWorkflow",
            "metadata": { "name": "g1", "subProject": "VOUCHERS", "semver": "1.0.0" },
            "spec": {
                "source": { "type": "yugabyte", "schema": "s", "table": "t" },
                "pipeline": [
                    { "kind": "group_by", "id": "g_reg", "fields": ["region", "year"] }
                ]
            }
        }"#;
        let plan = compile_def(&ctx, def).expect("group_by must succeed");
        assert_eq!(plan.steps_descr[1].kind, "group_by");
        // Le schéma de sortie ne doit contenir que (region, year)
        let fields: Vec<String> = plan.output_schema
            .iter()
            .map(|(_, f)| f.name().to_string())
            .collect();
        assert_eq!(fields.len(), 2, "fields={:?}", fields);
        assert!(fields.contains(&"region".to_string()));
        assert!(fields.contains(&"year".to_string()));
    }

    #[test]
    fn compile_pivot_returns_clear_error() {
        let ctx = fresh_ctx_with_source();
        let def = r#"{
            "apiVersion": "analytics.faso/v1",
            "kind": "AnalyticsWorkflow",
            "metadata": { "name": "p1", "subProject": "VOUCHERS", "semver": "1.0.0" },
            "spec": {
                "source": { "type": "yugabyte", "schema": "s", "table": "t" },
                "pipeline": [
                    {
                        "kind": "pivot",
                        "id":   "p1",
                        "rowFields":   ["region"],
                        "columnField": "year",
                        "valueField":  "value",
                        "aggregation": "SUM"
                    }
                ]
            }
        }"#;
        let err = compile_def(&ctx, def).expect_err("pivot must be deferred");
        let msg = err.to_string();
        assert!(msg.contains("pivot pending DataFusion 44+"),
                "expected deferred message, got: {}", msg);
    }

    #[test]
    fn compile_join_unknown_right_fails_clearly() {
        let ctx = fresh_ctx_with_source();
        let def = r#"{
            "apiVersion": "analytics.faso/v1",
            "kind": "AnalyticsWorkflow",
            "metadata": { "name": "j-fail", "subProject": "VOUCHERS", "semver": "1.0.0" },
            "spec": {
                "source": { "type": "yugabyte", "schema": "s", "table": "t" },
                "pipeline": [
                    {
                        "kind": "join",
                        "id":   "j_nope",
                        "right":"nonexistent_table",
                        "on":   [{"left": "region", "right": "region"}],
                        "joinType": "INNER"
                    }
                ]
            }
        }"#;
        let err = compile_def(&ctx, def).expect_err("join on unknown table must fail");
        assert!(err.to_string().contains("nonexistent_table"));
    }
}
