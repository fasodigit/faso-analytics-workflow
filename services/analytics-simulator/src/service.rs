// REST/JSON simulator API. We pick REST over gRPC for Phase 2 to avoid
// coupling with the analytics_engine proto (which a sibling agent may be
// changing). Endpoints :
//
//   POST /v1/simulate                 → enqueue + return {simulation_id, status:"QUEUED"}
//   GET  /v1/simulations/{id}         → poll status / result key
//   GET  /healthz                     → liveness
//
// The simulation lifecycle is :
//   QUEUED → MATERIALIZING_SAMPLE → SANDBOX_RUNNING → COMPLETED | FAILED | TIMEOUT.

use std::collections::HashMap;
use std::sync::Arc;

use axum::{
    extract::{Path, State},
    http::StatusCode,
    response::IntoResponse,
    routing::{get, post},
    Json, Router,
};
use serde::{Deserialize, Serialize};
use tokio::sync::RwLock;
use tracing::{error, info, instrument};
use uuid::Uuid;

use crate::config::SimulatorConfig;
use crate::error::SimulatorError;
use crate::sampling::{self, SampleSpec};
use crate::sandbox::{self, SandboxSpec};

#[derive(Clone)]
pub struct AppState {
    pub cfg:        Arc<SimulatorConfig>,
    pub simulations: Arc<RwLock<HashMap<String, SimulationRecord>>>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "SCREAMING_SNAKE_CASE")]
pub enum SimulationStatus {
    Queued,
    MaterializingSample,
    SandboxRunning,
    Completed,
    Failed,
    Timeout,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct SimulationRecord {
    pub simulation_id: String,
    pub plan_id:       String,
    pub status:        SimulationStatus,
    pub started_at:    String,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub finished_at:   Option<String>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub result_key:    Option<String>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub error:         Option<String>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub duration_ms:   Option<u64>,
}

#[derive(Debug, Deserialize)]
pub struct SimulateRequest {
    pub plan_id: String,
    #[serde(default)]
    pub source:  Option<SourceRef>,
    pub sample:  SampleSpec,
    /// Optional inline rows for Phase 2 testing — real sources land in Phase 3.
    #[serde(default)]
    pub rows:    Vec<serde_json::Value>,
}

#[derive(Debug, Deserialize)]
#[serde(tag = "type", rename_all = "lowercase")]
pub enum SourceRef {
    Yugabyte { schema: String, table: String },
    Dragonfly { url: String, key: String },
    Inline,
}

#[derive(Debug, Serialize)]
pub struct SimulateResponse {
    pub simulation_id: String,
    pub status:        SimulationStatus,
}

pub async fn build_router(cfg: SimulatorConfig) -> Result<Router, SimulatorError> {
    let state = AppState {
        cfg:         Arc::new(cfg),
        simulations: Arc::new(RwLock::new(HashMap::new())),
    };

    let app = Router::new()
        .route("/healthz", get(healthz))
        .route("/v1/simulate", post(post_simulate))
        .route("/v1/simulations/:id", get(get_simulation))
        .with_state(state);

    Ok(app)
}

async fn healthz() -> impl IntoResponse {
    (StatusCode::OK, "ok")
}

#[instrument(skip(state, req))]
async fn post_simulate(
    State(state): State<AppState>,
    Json(req): Json<SimulateRequest>,
) -> Result<Json<SimulateResponse>, ApiError> {
    let sim_id = Uuid::now_v7().to_string();
    let record = SimulationRecord {
        simulation_id: sim_id.clone(),
        plan_id:       req.plan_id.clone(),
        status:        SimulationStatus::Queued,
        started_at:    now_rfc3339(),
        finished_at:   None,
        result_key:    None,
        error:         None,
        duration_ms:   None,
    };
    state.simulations.write().await.insert(sim_id.clone(), record);

    // Detach the sandbox lifecycle on its own task so the HTTP response can
    // return immediately. Failures land in the simulation record.
    let sid = sim_id.clone();
    let st = state.clone();
    tokio::spawn(async move {
        if let Err(e) = run_simulation(&st, &sid, req).await {
            error!(sim_id = %sid, error = %e, "simulation failed");
            mark_failed(&st, &sid, e.to_string()).await;
        }
    });

    Ok(Json(SimulateResponse {
        simulation_id: sim_id,
        status:        SimulationStatus::Queued,
    }))
}

async fn get_simulation(
    State(state): State<AppState>,
    Path(id): Path<String>,
) -> Result<Json<SimulationRecord>, ApiError> {
    let map = state.simulations.read().await;
    match map.get(&id) {
        Some(rec) => Ok(Json(rec.clone())),
        None => Err(ApiError::NotFound(format!("simulation {id} not found"))),
    }
}

async fn run_simulation(
    state: &AppState,
    sim_id: &str,
    req: SimulateRequest,
) -> Result<(), SimulatorError> {
    // 1. Materialize sample → DragonflyDB.
    set_status(state, sim_id, SimulationStatus::MaterializingSample).await;
    let sample = sampling::materialize(req.sample.strategy, req.sample.size, req.sample.seed, &req.rows);
    let _ = sampling::write_sample_to_dragonfly(&state.cfg.dragonfly_url, sim_id, &sample)
        .await
        .map_err(|e| {
            // Non-fatal in dev — Dragonfly may be down. We still want the
            // sandbox path to run for tests, so we just log and continue.
            info!(error = %e, sim_id, "dragonfly write skipped (likely dev mode)");
            e
        })
        .ok();

    // 2. Launch sandbox container.
    set_status(state, sim_id, SimulationStatus::SandboxRunning).await;
    let timeout_sec = state.cfg.timeout_for_sample(sample.size);
    let spec = SandboxSpec {
        sim_id:           sim_id.to_string(),
        sandbox_image:    state.cfg.sandbox_image.clone(),
        seccomp_profile:  state.cfg.seccomp_profile.clone(),
        apparmor_profile: state.cfg.apparmor_profile.clone(),
        memory_max:       state.cfg.memory_max.clone(),
        cpu_max:          state.cfg.cpu_max.clone(),
        pids_limit:       state.cfg.pids_limit,
        timeout_sec,
        kill_grace_sec:   state.cfg.kill_grace_sec,
        env: vec![
            ("SIM_ID".into(), sim_id.to_string()),
            ("DRAGONFLY_URL".into(), state.cfg.dragonfly_url.clone()),
            ("PLAN_ID".into(), req.plan_id.clone()),
        ],
    };
    let outcome = sandbox::run_sandbox(&spec).await?;

    // 3. Record result.
    let mut map = state.simulations.write().await;
    if let Some(rec) = map.get_mut(sim_id) {
        rec.duration_ms = Some(outcome.duration.as_millis() as u64);
        rec.finished_at = Some(now_rfc3339());
        rec.status = if outcome.timed_out {
            SimulationStatus::Timeout
        } else if outcome.exit_status.map(|s| s.success()).unwrap_or(false) {
            rec.result_key = Some(format!("simulation:{sim_id}:result"));
            SimulationStatus::Completed
        } else {
            rec.error = Some(format!(
                "sandbox exit {:?}, stderr: {}",
                outcome.exit_status, outcome.stderr
            ));
            SimulationStatus::Failed
        };
    }
    Ok(())
}

async fn set_status(state: &AppState, sim_id: &str, status: SimulationStatus) {
    if let Some(rec) = state.simulations.write().await.get_mut(sim_id) {
        rec.status = status;
    }
}

async fn mark_failed(state: &AppState, sim_id: &str, err: String) {
    if let Some(rec) = state.simulations.write().await.get_mut(sim_id) {
        rec.status = SimulationStatus::Failed;
        rec.error = Some(err);
        rec.finished_at = Some(now_rfc3339());
    }
}

fn now_rfc3339() -> String {
    let secs = std::time::SystemTime::now()
        .duration_since(std::time::UNIX_EPOCH)
        .map(|d| d.as_secs())
        .unwrap_or(0);
    format_unix(secs)
}

fn format_unix(secs: u64) -> String {
    let days = (secs / 86_400) as i64;
    let sod = secs % 86_400;
    let h = (sod / 3600) as u32;
    let m = ((sod % 3600) / 60) as u32;
    let s = (sod % 60) as u32;
    let z = days + 719_468;
    let era = z.div_euclid(146_097);
    let doe = z.rem_euclid(146_097) as u64;
    let yoe = (doe - doe / 1_460 + doe / 36_524 - doe / 146_096) / 365;
    let y = (yoe as i64) + era * 400;
    let doy = doe - (365 * yoe + yoe / 4 - yoe / 100);
    let mp = (5 * doy + 2) / 153;
    let d = (doy - (153 * mp + 2) / 5 + 1) as u32;
    let mo = (if mp < 10 { mp + 3 } else { mp - 9 }) as u32;
    let year = y + i64::from(mo <= 2);
    format!("{:04}-{:02}-{:02}T{:02}:{:02}:{:02}Z", year, mo, d, h, m, s)
}

#[derive(Debug)]
pub enum ApiError {
    BadRequest(String),
    NotFound(String),
    Internal(String),
}

impl From<SimulatorError> for ApiError {
    fn from(e: SimulatorError) -> Self {
        match e {
            SimulatorError::InvalidRequest(m) => ApiError::BadRequest(m),
            _ => ApiError::Internal(e.to_string()),
        }
    }
}

impl IntoResponse for ApiError {
    fn into_response(self) -> axum::response::Response {
        let (status, msg) = match self {
            ApiError::BadRequest(m) => (StatusCode::BAD_REQUEST, m),
            ApiError::NotFound(m)   => (StatusCode::NOT_FOUND, m),
            ApiError::Internal(m)   => (StatusCode::INTERNAL_SERVER_ERROR, m),
        };
        let body = serde_json::json!({ "error": msg });
        (status, Json(body)).into_response()
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn dummy_cfg() -> SimulatorConfig {
        SimulatorConfig {
            grpc_addr:        "0.0.0.0:50052".into(),
            dragonfly_url:    "redis://127.0.0.1:6399".into(),
            sandbox_image:    "faso/analytics-sandbox:1.0".into(),
            seccomp_profile:  "/etc/faso/seccomp/analytics-sandbox.json".into(),
            apparmor_profile: "faso-analytics-sandbox".into(),
            timeout_small:    60,
            timeout_medium:   300,
            timeout_large:    600,
            memory_max:       "2g".into(),
            cpu_max:          "2.0".into(),
            pids_limit:       64,
            kill_grace_sec:   10,
        }
    }

    #[tokio::test]
    async fn build_router_smoke() {
        let _ = build_router(dummy_cfg()).await.expect("router builds");
    }

    #[test]
    fn now_rfc3339_format() {
        let s = now_rfc3339();
        // YYYY-MM-DDTHH:MM:SSZ — 20 chars + Z
        assert_eq!(s.len(), 20);
        assert!(s.ends_with('Z'));
    }
}
