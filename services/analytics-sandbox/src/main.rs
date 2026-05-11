// FASO-ANALYTICS-WORKFLOW — analytics-sandbox binary.
//
// Runs INSIDE the ephemeral Podman-rootless container described by ADR-004.
// Reads `simulation:{SIM_ID}:sample` (and optionally `simulation:{SIM_ID}:plan`)
// from DragonflyDB, executes a DataFusion query locally on the in-memory
// sample, then writes the result back to `simulation:{SIM_ID}:result`.
//
// The sandbox MUST stay offline except for that single Dragonfly TCP socket.
// Any other outbound connection would be blocked by the slirp4netns ACL.

use std::env;
use std::time::Duration;

use datafusion::prelude::SessionContext;
use redis::AsyncCommands;
use serde::{Deserialize, Serialize};
use tracing::{info, warn};
use tracing_subscriber::EnvFilter;

const RESULT_TTL_SEC: u64 = 24 * 3600;

#[derive(Debug, thiserror::Error)]
enum SandboxRunError {
    #[error("missing env var: {0}")]
    MissingEnv(&'static str),
    #[error("dragonfly error: {0}")]
    Dragonfly(#[from] redis::RedisError),
    #[error("sample decode error: {0}")]
    SampleDecode(String),
    #[error("datafusion error: {0}")]
    DataFusion(#[from] datafusion::error::DataFusionError),
    #[error("io error: {0}")]
    Io(#[from] std::io::Error),
}

#[derive(Debug, Deserialize)]
struct MaterializedSample {
    #[allow(dead_code)]
    strategy: String,
    size:     usize,
    rows:     Vec<serde_json::Value>,
    #[allow(dead_code)]
    materialized_at: String,
}

#[derive(Debug, Serialize)]
struct SandboxResult {
    sim_id:        String,
    plan_id:       String,
    rows_in:       usize,
    rows_out:      usize,
    duration_ms:   u64,
    completed_at:  String,
    /// Serialized rows (JSON). For Phase 2 we ship row-oriented JSON; Phase 3
    /// will switch to Arrow IPC for zero-copy reads.
    rows:          Vec<serde_json::Value>,
}

#[tokio::main(flavor = "multi_thread", worker_threads = 2)]
async fn main() -> Result<(), Box<dyn std::error::Error>> {
    tracing_subscriber::fmt()
        .with_env_filter(EnvFilter::try_from_default_env().unwrap_or_else(|_| EnvFilter::new("info")))
        .init();

    let sim_id = env::var("SIM_ID").map_err(|_| SandboxRunError::MissingEnv("SIM_ID"))?;
    let dragonfly = env::var("DRAGONFLY_URL")
        .map_err(|_| SandboxRunError::MissingEnv("DRAGONFLY_URL"))?;
    let plan_id = env::var("PLAN_ID").unwrap_or_else(|_| "phase2-passthrough".to_string());

    info!(sim_id, plan_id, "analytics-sandbox starting");

    let started = std::time::Instant::now();

    // 1. Connect to Dragonfly and fetch the sample.
    let client = redis::Client::open(dragonfly.as_str())?;
    let mut conn = client.get_multiplexed_async_connection().await?;
    let sample_key = format!("simulation:{}:sample", sim_id);
    let serialized: String = conn.get(&sample_key).await?;
    let sample: MaterializedSample = serde_json::from_str(&serialized)
        .map_err(|e| SandboxRunError::SampleDecode(e.to_string()))?;

    info!(sim_id, sample_size = sample.size, "fetched sample from dragonfly");

    // 2. Execute the plan via DataFusion. Phase 2 ships a pass-through "scan
    //    + count" because the plan-id resolution lives in analytics-engine
    //    and we deliberately keep this binary offline-from-engine. Phase 3
    //    will deserialize a LogicalPlan stored under `simulation:{sim_id}:plan`.
    let ctx = SessionContext::new();
    let row_count = sample.rows.len();
    let rows_out: Vec<serde_json::Value> = sample.rows.clone();

    // Touch DataFusion ctx to avoid the unused-variable lint; running an
    // explain on an empty table proves the binary is wired.
    if let Err(e) = ctx.sql("SELECT 1").await {
        warn!("datafusion warmup failed: {e}");
    }

    // 3. Write result back to dragonfly.
    let result = SandboxResult {
        sim_id:       sim_id.clone(),
        plan_id:      plan_id.clone(),
        rows_in:      sample.size,
        rows_out:     row_count,
        duration_ms:  started.elapsed().as_millis() as u64,
        completed_at: rfc3339_now(),
        rows:         rows_out,
    };
    let result_key = format!("simulation:{}:result", sim_id);
    let payload = serde_json::to_string(&result)
        .map_err(|e| SandboxRunError::SampleDecode(e.to_string()))?;
    let _: () = conn.set_ex(&result_key, payload, RESULT_TTL_SEC).await?;

    info!(
        sim_id,
        plan_id,
        rows_in = result.rows_in,
        rows_out = result.rows_out,
        duration_ms = result.duration_ms,
        "sandbox completed"
    );

    // Tiny coda : tokio time tick to flush log subscriber buffers before exit.
    tokio::time::sleep(Duration::from_millis(20)).await;
    Ok(())
}

fn rfc3339_now() -> String {
    let secs = std::time::SystemTime::now()
        .duration_since(std::time::UNIX_EPOCH)
        .map(|d| d.as_secs())
        .unwrap_or(0);
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
