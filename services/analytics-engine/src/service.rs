// Tonic gRPC service binding compile.rs to the proto-generated server trait.
// Référence ADR-001.

use std::sync::Arc;
use std::pin::Pin;

use datafusion::prelude::SessionContext;
use tokio::sync::RwLock;
use tokio_stream::Stream;
use tonic::{Request, Response, Status};
use tracing::{info, instrument};
use uuid::Uuid;

use crate::compile::{compile, PlanRegistry};

// Les types proto sont générés au build dans src/_gen/. Phase 1 stub :
pub mod proto {
    // Phase 2 : décommenter quand src/_gen/faso.analytics.engine.v1.rs existera
    // (tonic-build produit le fichier depuis proto/v1/analytics_engine.proto).
    // tonic::include_proto!("faso.analytics.engine.v1");
}

/// AnalyticsEngineService — implémentation des 3 RPC.
pub struct AnalyticsEngineService {
    ctx:      Arc<SessionContext>,
    registry: Arc<RwLock<PlanRegistry>>,
}

impl AnalyticsEngineService {
    pub fn new() -> Self {
        Self {
            ctx:      Arc::new(SessionContext::new()),
            registry: Arc::new(RwLock::new(PlanRegistry::new())),
        }
    }

    /// Compile (Phase 1) — exposé en méthode publique testable sans la couche
    /// gRPC. Le binding Tonic du trait `AnalyticsEngine` se câblera quand
    /// `tonic-build` aura produit les stubs.
    #[instrument(skip(self, definition_jcs))]
    pub async fn compile_definition(
        &self,
        definition_jcs: &str,
    ) -> Result<Uuid, crate::error::EngineError> {
        let plan = compile(&self.ctx, definition_jcs).await?;
        let plan_id = plan.plan_id;
        info!(?plan_id, steps = plan.steps_descr.len(), "plan compiled and cached");
        self.registry.write().await.insert(plan);
        Ok(plan_id)
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use datafusion::arrow::datatypes::{DataType, Field, Schema};
    use datafusion::arrow::record_batch::RecordBatch;
    use std::sync::Arc;

    /// Test compile() bout en bout avec une définition workflow réelle issue
    /// des exemples livrés en Phase 0 (variante simplifiée pour Phase 1).
    #[tokio::test]
    async fn compile_bar_vertical_pagsi_succeeds() {
        let service = AnalyticsEngineService::new();

        // Enregistrer une table "source" avec un schéma minimal compatible
        // avec l'exemple 01-bar-vertical-pagsi-region.json.
        let schema = Arc::new(Schema::new(vec![
            Field::new("region",        DataType::Utf8,    false),
            Field::new("statut",        DataType::Utf8,    false),
            Field::new("superficie_ha", DataType::Float64, false),
        ]));
        let batch = RecordBatch::new_empty(schema.clone());
        service.ctx.register_batch("source", batch).unwrap();

        // Définition workflow simplifiée Phase 1 (l'exemple 01 utilise une
        // expression `IN ('termine','en_cours')` non encore supportée par
        // parse_filter_expr Phase 1 — on remplace par une comparaison simple).
        let definition = r#"{
            "apiVersion": "analytics.faso/v1",
            "kind": "AnalyticsWorkflow",
            "metadata": {
                "name": "test-bar-vertical",
                "subProject": "VOUCHERS",
                "semver": "1.0.0-draft.1"
            },
            "spec": {
                "source": {
                    "type": "yugabyte",
                    "schema": "voucher_schema",
                    "table": "perimetre_amenage"
                },
                "pipeline": [
                    {
                        "kind": "filter",
                        "id": "filter_positifs",
                        "expression": "superficie_ha > 0"
                    },
                    {
                        "kind": "aggregate",
                        "id": "agg_par_region",
                        "groupBy": ["region"],
                        "aggregations": [
                            { "alias": "superficie_totale_ha", "function": "SUM", "field": "superficie_ha" }
                        ]
                    }
                ]
            }
        }"#;

        let plan_id = service.compile_definition(definition).await.expect("compile must succeed");
        // Plan UUID v7 ⇒ première portion d'horodatage > 0.
        assert!(!plan_id.is_nil(), "plan_id ne doit pas être nil");

        // Le plan doit être retrouvable dans le registry.
        let plan = service.registry.read().await.get(&plan_id).expect("plan in registry");
        assert_eq!(plan.steps_descr.len(), 3, "source + filter + aggregate = 3 steps");
        assert_eq!(plan.steps_descr[0].kind, "source");
        assert_eq!(plan.steps_descr[1].kind, "filter");
        assert_eq!(plan.steps_descr[2].kind, "aggregate");
    }

    #[tokio::test]
    async fn compile_rejects_wrong_api_version() {
        let service = AnalyticsEngineService::new();
        let definition = r#"{
            "apiVersion": "analytics.faso/v999",
            "kind": "AnalyticsWorkflow",
            "metadata": { "name": "x", "subProject": "VOUCHERS", "semver": "1.0.0" },
            "spec": { "source": { "type": "yugabyte", "schema": "s", "table": "t" }, "pipeline": [] }
        }"#;
        let err = service.compile_definition(definition).await.expect_err("must reject");
        let msg = err.to_string();
        assert!(msg.contains("apiVersion"), "got: {}", msg);
    }
}
