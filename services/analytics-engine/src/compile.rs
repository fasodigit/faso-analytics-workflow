// Module `compile` — parsing d'une définition workflow (JSON v1) vers un plan
// DataFusion. Cohérent ADR-001 + schemas/workflow-v1.json.
//
// Convention : un plan compilé est un `LogicalPlan` DataFusion stocké en cache
// LRU dans `PlanRegistry` (clé = `plan_id` UUID). L'`Execute` RPC l'invoque
// avec un `SourceHandle` qui matérialise le plan en `RecordBatch`s.

use std::collections::HashMap;
use std::sync::Arc;

use datafusion::common::{DFSchema, DataFusionError};
use datafusion::logical_expr::{
    col, lit, Aggregate, AggregateUDF, Expr, LogicalPlan, LogicalPlanBuilder,
};
use datafusion::prelude::*;
use serde::{Deserialize, Serialize};
use tracing::{debug, instrument};
use uuid::Uuid;

use crate::error::EngineError;

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
    // Other transforms deferred to Phase 2 (join, pivot, window, outlier, normalize, recode, group_by).
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

#[derive(Clone)]
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
            }
            TransformDef::Aggregate { id, group_by, aggregations } => {
                let group_exprs: Vec<Expr> = group_by.iter().map(|f| col(f)).collect();
                let agg_exprs: Vec<Expr> = aggregations.iter()
                    .map(|a| build_aggregate_expr(a))
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
            }
            TransformDef::Other => {
                // Phase 2 : implémenter join / pivot / window / outlier / normalize / recode
                return Err(EngineError::Compilation(
                    "transform kind not implemented in Phase 1".to_string(),
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
