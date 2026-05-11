// Module `transforms` — implémentations Phase 2 des transformations
// (join, group_by, pivot, window, outlier, normalize, recode) extraites
// de `compile.rs` pour clarté. Cohérent ADR-001 + schemas/workflow-v1.json.
//
// Convention : chaque builder retourne un `Result<(DataFrame, PlanStepDescription)>`
// pour pouvoir être chaîné dans `compile.rs`.

use std::collections::HashMap;

use datafusion::common::ScalarValue;
use datafusion::functions::expr_fn as core_fn;          // nullif, coalesce
use datafusion::functions::math::expr_fn::{abs, ln};
use datafusion::functions_aggregate::approx_percentile_cont::approx_percentile_cont_udaf;
use datafusion::functions_aggregate::average::avg_udaf;
use datafusion::functions_aggregate::min_max::{max_udaf, min_udaf};
use datafusion::functions_aggregate::stddev::stddev_udaf;
use datafusion::functions_window::expr_fn::{
    cume_dist, dense_rank, lag, lead, ntile, rank as wf_rank, row_number,
};
use datafusion::logical_expr::{
    expr::WindowFunction, BuiltInWindowFunction, ExprFunctionExt, WindowFrame,
    WindowFrameBound, WindowFrameUnits, WindowFunctionDefinition,
};
use datafusion::prelude::*;
use serde::{Deserialize, Serialize};
use tracing::{debug, warn};

use crate::compile::PlanStepDescription;
use crate::error::EngineError;

// ----------------------------------------------------------------------------
// Supporting structs (referenced from TransformDef in compile.rs)
// ----------------------------------------------------------------------------

#[derive(Debug, Clone, Deserialize, Serialize)]
pub struct JoinKey {
    pub left:  String,
    pub right: String,
}

#[derive(Debug, Clone, Deserialize, Serialize)]
pub struct WindowOrder {
    pub field:     String,
    pub direction: String,    // ASC | DESC
}

#[derive(Debug, Clone, Deserialize, Serialize)]
pub struct WindowFrameDef {
    pub kind:  String,        // ROWS | RANGE
    pub start: String,        // ex: "UNBOUNDED PRECEDING", "CURRENT ROW", "1 PRECEDING"
    pub end:   String,
}

// ----------------------------------------------------------------------------
// JOIN
// ----------------------------------------------------------------------------

/// Compile a join transform.
///
/// Phase 2 simplification : `right` doit référencer une table déjà enregistrée
/// dans le `SessionContext`. Référencer un step précédent du même pipeline est
/// supporté via la map `step_outputs` (id -> DataFrame).
#[allow(clippy::too_many_arguments)]
pub async fn compile_join(
    ctx:           &SessionContext,
    left_df:       DataFrame,
    id:            &str,
    right_ref:     &str,
    on:            &[JoinKey],
    join_type_str: &str,
    step_outputs:  &HashMap<String, DataFrame>,
    step_idx:      usize,
) -> Result<(DataFrame, PlanStepDescription), EngineError> {
    use datafusion::common::JoinType;

    let join_type = match join_type_str.to_uppercase().as_str() {
        "INNER"      => JoinType::Inner,
        "LEFT"       => JoinType::Left,
        "RIGHT"      => JoinType::Right,
        "FULL_OUTER" => JoinType::Full,
        "SEMI"       => JoinType::LeftSemi,
        "ANTI"       => JoinType::LeftAnti,
        other        => return Err(EngineError::Compilation(format!(
            "join[{}]: joinType non supporté '{}'", id, other
        ))),
    };

    // Resolve right side : prefer a previously-emitted step output, else fall
    // back to a registered table on `ctx`. This is the Phase 2 simplification
    // documented at module level.
    let right_df = if let Some(prev) = step_outputs.get(right_ref) {
        prev.clone()
    } else {
        ctx.table(right_ref).await.map_err(|e| EngineError::Compilation(format!(
            "join[{}]: right='{}' introuvable (ni step id, ni table SessionContext): {}",
            id, right_ref, e
        )))?
    };

    let left_cols:  Vec<&str> = on.iter().map(|k| k.left.as_str()).collect();
    let right_cols: Vec<&str> = on.iter().map(|k| k.right.as_str()).collect();

    let joined = left_df
        .join(right_df, join_type, &left_cols, &right_cols, None)
        .map_err(|e| EngineError::Compilation(format!("join[{}]: {}", id, e)))?;

    let descr = PlanStepDescription {
        id:             id.to_string(),
        kind:           "join".to_string(),
        description:    format!(
            "{} JOIN {} ON ({})",
            join_type_str.to_uppercase(),
            right_ref,
            on.iter().map(|k| format!("{}={}", k.left, k.right)).collect::<Vec<_>>().join(",")
        ),
        estimated_rows: 500_000,
        estimated_cost: 10.0 + step_idx as f64,
    };
    Ok((joined, descr))
}

// ----------------------------------------------------------------------------
// GROUP_BY (DISTINCT by fields — no aggregations)
// ----------------------------------------------------------------------------

pub fn compile_group_by(
    df:       DataFrame,
    id:       &str,
    fields:   &[String],
    step_idx: usize,
) -> Result<(DataFrame, PlanStepDescription), EngineError> {
    let group_exprs: Vec<Expr> = fields.iter().map(col).collect();
    let out = df
        .aggregate(group_exprs, vec![])
        .map_err(|e| EngineError::Compilation(format!("group_by[{}]: {}", id, e)))?;

    let descr = PlanStepDescription {
        id:             id.to_string(),
        kind:           "group_by".to_string(),
        description:    format!("GROUP BY {} (distinct)", fields.join(",")),
        estimated_rows: 10_000,
        estimated_cost: 3.0 + step_idx as f64,
    };
    Ok((out, descr))
}

// ----------------------------------------------------------------------------
// PIVOT — deferred (DataFusion 43 has no native PIVOT)
// ----------------------------------------------------------------------------

/// Phase 2 : la rotation native (`PIVOT (... FOR ... IN (...))`) n'est arrivée
/// qu'avec DataFusion 44. Un fallback purement plan logique nécessiterait de
/// matérialiser les valeurs distinctes de `column_field` au moment du compile,
/// ce qui couple compile et execute. On préfère renvoyer une erreur claire et
/// trace un warning pour que l'UI affiche un message explicite.
pub fn compile_pivot_deferred(id: &str) -> Result<(DataFrame, PlanStepDescription), EngineError> {
    warn!(target: "analytics_engine::transforms", "pivot[{}]: deferred pending DataFusion 44+", id);
    Err(EngineError::Compilation(format!(
        "pivot[{}]: pivot pending DataFusion 44+ (deferred Phase 2)",
        id
    )))
}

// ----------------------------------------------------------------------------
// WINDOW
// ----------------------------------------------------------------------------

#[allow(clippy::too_many_arguments)]
pub fn compile_window(
    df:           DataFrame,
    id:           &str,
    alias:        &str,
    function:     &str,
    partition_by: &[String],
    order_by:     Option<&[WindowOrder]>,
    frame:        Option<&WindowFrameDef>,
    step_idx:     usize,
) -> Result<(DataFrame, PlanStepDescription), EngineError> {
    // Build the underlying WindowFunction expression based on the function name.
    // ROW_NUMBER / RANK / DENSE_RANK / NTILE / CUME_DIST / LAG / LEAD are
    // exposed via `functions_window::expr_fn::*`. FIRST_VALUE / LAST_VALUE come
    // from `BuiltInWindowFunction`.
    let base_expr: Expr = match function.to_uppercase().as_str() {
        "ROW_NUMBER" => row_number(),
        "RANK"       => wf_rank(),
        "DENSE_RANK" => dense_rank(),
        "CUME_DIST"  => cume_dist(),
        "NTILE"      => ntile(lit(4_i64)),                   // default 4 buckets
        "LAG"        => lag(col(alias), None, None),         // 1 offset
        "LEAD"       => lead(col(alias), None, None),
        "FIRST_VALUE" => Expr::WindowFunction(WindowFunction::new(
            BuiltInWindowFunction::FirstValue,
            vec![col(alias)],
        )),
        "LAST_VALUE"  => Expr::WindowFunction(WindowFunction::new(
            BuiltInWindowFunction::LastValue,
            vec![col(alias)],
        )),
        other => return Err(EngineError::Compilation(format!(
            "window[{}]: function '{}' non supportée", id, other
        ))),
    };

    // Translate JSON Schema partition_by/order_by/frame into ExprFunctionExt
    // builder operations. ExprFunctionExt::build() returns the final Expr.
    let part_exprs: Vec<Expr> = partition_by.iter().map(col).collect();
    let order_exprs: Vec<datafusion::logical_expr::SortExpr> = order_by
        .map(|orders| orders.iter().map(|o| {
            let asc = o.direction.eq_ignore_ascii_case("ASC");
            col(&o.field).sort(asc, false)
        }).collect())
        .unwrap_or_default();

    let mut builder = base_expr.partition_by(part_exprs);
    if !order_exprs.is_empty() {
        builder = builder.order_by(order_exprs);
    }
    if let Some(fr) = frame {
        let wf = build_window_frame(fr)?;
        builder = builder.window_frame(wf);
    }
    let window_expr = builder.build()
        .map_err(|e| EngineError::Compilation(format!("window[{}]: {}", id, e)))?;

    let out = df
        .with_column(alias, window_expr)
        .map_err(|e| EngineError::Compilation(format!("window[{}]: {}", id, e)))?;

    let descr = PlanStepDescription {
        id:             id.to_string(),
        kind:           "window".to_string(),
        description:    format!("{} = {} OVER (PARTITION BY {})", alias, function, partition_by.join(",")),
        estimated_rows: 500_000,
        estimated_cost: 8.0 + step_idx as f64,
    };
    Ok((out, descr))
}

fn build_window_frame(def: &WindowFrameDef) -> Result<WindowFrame, EngineError> {
    let units = match def.kind.to_uppercase().as_str() {
        "ROWS"  => WindowFrameUnits::Rows,
        "RANGE" => WindowFrameUnits::Range,
        other   => return Err(EngineError::Compilation(format!(
            "window frame kind invalide '{}', attendu ROWS|RANGE", other
        ))),
    };
    let start = parse_frame_bound(&def.start)?;
    let end   = parse_frame_bound(&def.end)?;
    Ok(WindowFrame::new_bounds(units, start, end))
}

fn parse_frame_bound(s: &str) -> Result<WindowFrameBound, EngineError> {
    let up = s.trim().to_uppercase();
    if up == "CURRENT ROW" {
        return Ok(WindowFrameBound::CurrentRow);
    }
    if up == "UNBOUNDED PRECEDING" {
        return Ok(WindowFrameBound::Preceding(ScalarValue::UInt64(None)));
    }
    if up == "UNBOUNDED FOLLOWING" {
        return Ok(WindowFrameBound::Following(ScalarValue::UInt64(None)));
    }
    // ex: "1 PRECEDING", "5 FOLLOWING"
    let parts: Vec<&str> = up.split_whitespace().collect();
    if parts.len() == 2 {
        if let Ok(n) = parts[0].parse::<u64>() {
            return match parts[1] {
                "PRECEDING" => Ok(WindowFrameBound::Preceding(ScalarValue::UInt64(Some(n)))),
                "FOLLOWING" => Ok(WindowFrameBound::Following(ScalarValue::UInt64(Some(n)))),
                _ => Err(EngineError::Compilation(format!("frame bound invalide: '{}'", s))),
            };
        }
    }
    Err(EngineError::Compilation(format!("frame bound non parseable: '{}'", s)))
}

// ----------------------------------------------------------------------------
// OUTLIER
// ----------------------------------------------------------------------------

#[allow(clippy::too_many_arguments)]
pub fn compile_outlier(
    df:          DataFrame,
    id:          &str,
    field:       &str,
    strategy:    &str,
    lower_bound: Option<f64>,
    upper_bound: Option<f64>,
    action:      Option<&str>,
    step_idx:    usize,
) -> Result<(DataFrame, PlanStepDescription), EngineError> {
    let action = action.unwrap_or("FLAG").to_uppercase();
    let flag_col = format!("{}_is_outlier", field);

    // 1. Compute is_outlier predicate per strategy.
    //    For THREE_SIGMA and IQR we need stats over the whole dataset → we
    //    construct them via a window function over an empty PARTITION BY
    //    with an UNBOUNDED PRECEDING ... UNBOUNDED FOLLOWING frame (full
    //    dataset). Aggregate UDFs are wrapped in WindowFunctionDefinition.

    let outlier_expr: Expr = match strategy.to_uppercase().as_str() {
        "THREE_SIGMA" => {
            let mean_w = full_dataset_window(
                WindowFunctionDefinition::AggregateUDF(avg_udaf()),
                vec![col(field)],
                id, "mean",
            )?;
            let std_w = full_dataset_window(
                WindowFunctionDefinition::AggregateUDF(stddev_udaf()),
                vec![col(field)],
                id, "stddev",
            )?;
            // |x - mean| > 3 * stddev  ⇒ outlier
            abs(col(field) - mean_w).gt(lit(3.0_f64) * std_w)
        }
        "IQR" => {
            let q1 = full_dataset_window(
                WindowFunctionDefinition::AggregateUDF(approx_percentile_cont_udaf()),
                vec![col(field), lit(0.25_f64)],
                id, "q1",
            )?;
            let q3 = full_dataset_window(
                WindowFunctionDefinition::AggregateUDF(approx_percentile_cont_udaf()),
                vec![col(field), lit(0.75_f64)],
                id, "q3",
            )?;
            let iqr = q3.clone() - q1.clone();
            col(field).lt(q1 - lit(1.5_f64) * iqr.clone())
                .or(col(field).gt(q3 + lit(1.5_f64) * iqr))
        }
        "XLSFORM_CONSTRAINT" | "HARD_BOUNDS" => {
            let lo = lower_bound.unwrap_or(f64::NEG_INFINITY);
            let hi = upper_bound.unwrap_or(f64::INFINITY);
            col(field).lt(lit(lo)).or(col(field).gt(lit(hi)))
        }
        other => return Err(EngineError::Compilation(format!(
            "outlier[{}]: strategy '{}' non supportée", id, other
        ))),
    };

    // 2. Apply action.
    let out = match action.as_str() {
        "FLAG" => df.with_column(&flag_col, outlier_expr)
            .map_err(|e| EngineError::Compilation(format!("outlier[{}]: FLAG: {}", id, e)))?,
        "DROP" => df.filter(outlier_expr.not())
            .map_err(|e| EngineError::Compilation(format!("outlier[{}]: DROP: {}", id, e)))?,
        "CLAMP" => {
            // Pour HARD_BOUNDS/XLSFORM_CONSTRAINT on a des bornes explicites,
            // sinon (THREE_SIGMA/IQR) on retombe sur FLAG (clamp impossible
            // sans matérialisation des stats).
            if matches!(strategy.to_uppercase().as_str(), "XLSFORM_CONSTRAINT" | "HARD_BOUNDS") {
                let lo = lower_bound.unwrap_or(f64::NEG_INFINITY);
                let hi = upper_bound.unwrap_or(f64::INFINITY);
                // clamp = CASE WHEN x<lo THEN lo WHEN x>hi THEN hi ELSE x END
                let clamped = datafusion::logical_expr::when(col(field).lt(lit(lo)), lit(lo))
                    .when(col(field).gt(lit(hi)), lit(hi))
                    .otherwise(col(field))
                    .map_err(|e| EngineError::Compilation(format!("outlier[{}]: clamp case: {}", id, e)))?;
                df.with_column(field, clamped)
                    .map_err(|e| EngineError::Compilation(format!("outlier[{}]: CLAMP: {}", id, e)))?
            } else {
                debug!(target: "analytics_engine::transforms",
                    "outlier[{}]: action=CLAMP non supporté pour stratégie={}, fallback FLAG",
                    id, strategy);
                df.with_column(&flag_col, outlier_expr)
                    .map_err(|e| EngineError::Compilation(format!("outlier[{}]: CLAMP→FLAG fallback: {}", id, e)))?
            }
        }
        other => return Err(EngineError::Compilation(format!(
            "outlier[{}]: action '{}' invalide (attendu FLAG|DROP|CLAMP)", id, other
        ))),
    };

    let descr = PlanStepDescription {
        id:             id.to_string(),
        kind:           "outlier".to_string(),
        description:    format!("OUTLIER {} strategy={} action={}", field, strategy, action),
        estimated_rows: 500_000,
        estimated_cost: 7.0 + step_idx as f64,
    };
    Ok((out, descr))
}

// ----------------------------------------------------------------------------
// NORMALIZE
// ----------------------------------------------------------------------------

pub fn compile_normalize(
    df:       DataFrame,
    id:       &str,
    field:    &str,
    method:   &str,
    step_idx: usize,
) -> Result<(DataFrame, PlanStepDescription), EngineError> {
    let alias = format!("{}_norm", field);
    let new_expr: Expr = match method.to_uppercase().as_str() {
        "ZSCORE" => {
            let mean_w = full_dataset_window(
                WindowFunctionDefinition::AggregateUDF(avg_udaf()),
                vec![col(field)],
                id, "mean",
            )?;
            let std_w = full_dataset_window(
                WindowFunctionDefinition::AggregateUDF(stddev_udaf()),
                vec![col(field)],
                id, "stddev",
            )?;
            // (x - mean) / NULLIF(stddev, 0)  pour éviter division par zéro
            (col(field) - mean_w) / core_fn::nullif(std_w, lit(0.0_f64))
        }
        "MIN_MAX" => {
            let min_w = full_dataset_window(
                WindowFunctionDefinition::AggregateUDF(min_udaf()),
                vec![col(field)],
                id, "min",
            )?;
            let max_w = full_dataset_window(
                WindowFunctionDefinition::AggregateUDF(max_udaf()),
                vec![col(field)],
                id, "max",
            )?;
            (col(field) - min_w.clone()) / core_fn::nullif(max_w - min_w, lit(0.0_f64))
        }
        "RANK" => {
            // rank() OVER (ORDER BY field)
            wf_rank()
                .partition_by(vec![])
                .order_by(vec![col(field).sort(true, false)])
                .build()
                .map_err(|e| EngineError::Compilation(format!("normalize[{}]: rank: {}", id, e)))?
        }
        "LOG" => ln(col(field)),
        other => return Err(EngineError::Compilation(format!(
            "normalize[{}]: method '{}' non supportée", id, other
        ))),
    };

    let out = df.with_column(&alias, new_expr)
        .map_err(|e| EngineError::Compilation(format!("normalize[{}]: {}", id, e)))?;

    let descr = PlanStepDescription {
        id:             id.to_string(),
        kind:           "normalize".to_string(),
        description:    format!("NORMALIZE {} method={} → {}", field, method, alias),
        estimated_rows: 500_000,
        estimated_cost: 5.0 + step_idx as f64,
    };
    Ok((out, descr))
}

// ----------------------------------------------------------------------------
// RECODE
// ----------------------------------------------------------------------------

pub fn compile_recode(
    df:       DataFrame,
    id:       &str,
    field:    &str,
    alias:    &str,
    mapping:  &serde_json::Map<String, serde_json::Value>,
    default:  Option<&serde_json::Value>,
    step_idx: usize,
) -> Result<(DataFrame, PlanStepDescription), EngineError> {
    if mapping.is_empty() {
        return Err(EngineError::Compilation(format!(
            "recode[{}]: mapping vide", id
        )));
    }

    // CASE field WHEN 'k' THEN v ... ELSE default END
    let mut iter = mapping.iter();
    let (first_k, first_v) = iter.next().unwrap();
    let mut builder = datafusion::logical_expr::case(col(field))
        .when(lit(first_k.as_str()), json_to_lit(first_v));
    for (k, v) in iter {
        builder = builder.when(lit(k.as_str()), json_to_lit(v));
    }

    let case_expr = match default {
        Some(d) => builder.otherwise(json_to_lit(d))
            .map_err(|e| EngineError::Compilation(format!("recode[{}]: otherwise: {}", id, e)))?,
        None    => builder.end()
            .map_err(|e| EngineError::Compilation(format!("recode[{}]: end: {}", id, e)))?,
    };

    let out = df.with_column(alias, case_expr)
        .map_err(|e| EngineError::Compilation(format!("recode[{}]: {}", id, e)))?;

    let descr = PlanStepDescription {
        id:             id.to_string(),
        kind:           "recode".to_string(),
        description:    format!("RECODE {} → {} ({} mappings)", field, alias, mapping.len()),
        estimated_rows: 500_000,
        estimated_cost: 2.0 + step_idx as f64,
    };
    Ok((out, descr))
}

/// Construit une window function évaluée sur tout le dataset (PARTITION BY ∅,
/// ROWS BETWEEN UNBOUNDED PRECEDING AND UNBOUNDED FOLLOWING). Utilisé pour
/// matérialiser des stats globales (mean/stddev/min/max/percentile) sans
/// avoir à faire un join/aggregate séparé.
fn full_dataset_window(
    fun:      WindowFunctionDefinition,
    args:     Vec<Expr>,
    id:       &str,
    purpose:  &str,
) -> Result<Expr, EngineError> {
    let frame = WindowFrame::new_bounds(
        WindowFrameUnits::Rows,
        WindowFrameBound::Preceding(ScalarValue::UInt64(None)),
        WindowFrameBound::Following(ScalarValue::UInt64(None)),
    );
    Expr::WindowFunction(WindowFunction::new(fun, args))
        .partition_by(vec![])
        .window_frame(frame)
        .build()
        .map_err(|e| EngineError::Compilation(format!("[{}] {} window: {}", id, purpose, e)))
}

fn json_to_lit(v: &serde_json::Value) -> Expr {
    match v {
        serde_json::Value::Null     => lit(ScalarValue::Null),
        serde_json::Value::Bool(b)  => lit(*b),
        serde_json::Value::Number(n) => {
            if let Some(i) = n.as_i64() {
                lit(i)
            } else if let Some(f) = n.as_f64() {
                lit(f)
            } else {
                lit(n.to_string())
            }
        }
        serde_json::Value::String(s) => lit(s.as_str()),
        // arrays / objects ne sont pas des littéraux supportés → fallback string
        other => lit(other.to_string()),
    }
}
