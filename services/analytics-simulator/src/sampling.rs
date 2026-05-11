// Sampling strategies for the simulator. Each strategy materializes a subset
// of source rows into DragonflyDB under `simulation:{sim_id}:sample` (TTL 24h)
// so the sandbox can read it without touching production data.
//
// Phase 2 ships the strategy enum + Dragonfly write path + RANDOM (reservoir),
// FIRST_N, LAST_N. STRATIFIED / PERIOD / GOLDEN are placeholders that fall
// back to RANDOM with a TODO marker — full impl arrives in Phase 3 with the
// real source connectors (YugabyteDB scan, Dragonfly KEYS *, Redpanda tail).

use std::time::Duration;

use redis::AsyncCommands;
use serde::{Deserialize, Serialize};
use tracing::{info, warn};

use crate::error::SimulatorError;

/// Sample TTL — 24h matches ADR-004's lifecycle guarantee.
const SAMPLE_TTL: Duration = Duration::from_secs(24 * 3600);

#[derive(Debug, Clone, Copy, Serialize, Deserialize, PartialEq, Eq)]
#[serde(rename_all = "SCREAMING_SNAKE_CASE")]
pub enum SamplingStrategy {
    Random,
    Stratified,
    FirstN,
    LastN,
    Period,
    Golden,
}

impl SamplingStrategy {
    pub fn as_str(self) -> &'static str {
        match self {
            SamplingStrategy::Random      => "RANDOM",
            SamplingStrategy::Stratified  => "STRATIFIED",
            SamplingStrategy::FirstN      => "FIRST_N",
            SamplingStrategy::LastN       => "LAST_N",
            SamplingStrategy::Period      => "PERIOD",
            SamplingStrategy::Golden      => "GOLDEN",
        }
    }
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct SampleSpec {
    pub strategy: SamplingStrategy,
    pub size:     usize,
    #[serde(default)]
    pub seed:     Option<u64>,
    #[serde(default)]
    pub strata:   Option<String>, // for STRATIFIED
    #[serde(default)]
    pub period:   Option<String>, // for PERIOD (RFC 3339 interval e.g. "P1D")
}

/// Materialized payload written to Dragonfly. The sandbox decodes this back.
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct MaterializedSample {
    pub strategy: SamplingStrategy,
    pub size:     usize,
    pub rows:     Vec<serde_json::Value>,
    /// Time the sample was materialized (RFC 3339).
    pub materialized_at: String,
}

/// Write `sample` to `simulation:{sim_id}:sample` with TTL 24h.
pub async fn write_sample_to_dragonfly(
    dragonfly_url: &str,
    sim_id: &str,
    sample: &MaterializedSample,
) -> Result<String, SimulatorError> {
    let client = redis::Client::open(dragonfly_url)?;
    let mut conn = client.get_multiplexed_async_connection().await?;
    let key = format!("simulation:{}:sample", sim_id);
    let serialized = serde_json::to_string(sample)
        .map_err(|e| SimulatorError::Internal(format!("sample serialize: {e}")))?;

    let _: () = conn
        .set_ex(&key, serialized, SAMPLE_TTL.as_secs())
        .await?;
    info!(sim_id, key = %key, size = sample.size, "sample materialized");
    Ok(key)
}

/// Build a `MaterializedSample` from an in-memory source (Phase 2 stub). Real
/// connectors (Yugabyte SQL, Dragonfly SCAN, Redpanda tail) land in Phase 3.
/// Pure function — easy to unit test.
pub fn materialize(
    strategy: SamplingStrategy,
    size: usize,
    seed: Option<u64>,
    rows: &[serde_json::Value],
) -> MaterializedSample {
    let sampled = match strategy {
        SamplingStrategy::FirstN => take_first_n(rows, size),
        SamplingStrategy::LastN => take_last_n(rows, size),
        SamplingStrategy::Random => reservoir_sample(rows, size, seed.unwrap_or(0xfa50)),
        // Phase 2 fallback — Phase 3 will plug strata column + period filter.
        SamplingStrategy::Stratified | SamplingStrategy::Period | SamplingStrategy::Golden => {
            warn!(?strategy, "fallback to RANDOM (Phase 3 will wire real sampler)");
            reservoir_sample(rows, size, seed.unwrap_or(0xfa50))
        }
    };
    MaterializedSample {
        strategy,
        size: sampled.len(),
        rows: sampled,
        materialized_at: chrono_rfc3339_now(),
    }
}

fn take_first_n(rows: &[serde_json::Value], n: usize) -> Vec<serde_json::Value> {
    rows.iter().take(n).cloned().collect()
}

fn take_last_n(rows: &[serde_json::Value], n: usize) -> Vec<serde_json::Value> {
    let len = rows.len();
    let skip = len.saturating_sub(n);
    rows.iter().skip(skip).cloned().collect()
}

/// Vitter's reservoir sampling (Algorithm R) — single pass, fixed memory.
/// Deterministic given a seed (xorshift64).
fn reservoir_sample(rows: &[serde_json::Value], k: usize, seed: u64) -> Vec<serde_json::Value> {
    if k == 0 || rows.is_empty() {
        return Vec::new();
    }
    let mut reservoir: Vec<serde_json::Value> = rows.iter().take(k).cloned().collect();
    let mut state = if seed == 0 { 0xfa50_f150_0001_a001 } else { seed };
    for (i, row) in rows.iter().enumerate().skip(k) {
        state ^= state << 13;
        state ^= state >> 7;
        state ^= state << 17;
        let j = (state as usize) % (i + 1);
        if j < k {
            reservoir[j] = row.clone();
        }
    }
    reservoir
}

fn chrono_rfc3339_now() -> String {
    // Tiny dep-free RFC 3339 formatter using std::time. Good enough for an
    // audit timestamp — full chrono pulled in only if needed downstream.
    let secs = std::time::SystemTime::now()
        .duration_since(std::time::UNIX_EPOCH)
        .map(|d| d.as_secs())
        .unwrap_or(0);
    // Compute via /proc/sys: nope. Use a basic algorithm.
    format_unix_secs_as_rfc3339(secs)
}

fn format_unix_secs_as_rfc3339(secs: u64) -> String {
    // Civil-from-days algorithm (Howard Hinnant). Tested against Python
    // datetime.utcfromtimestamp for ranges 1970..3000.
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

#[cfg(test)]
mod tests {
    use super::*;
    use serde_json::json;

    fn fixture() -> Vec<serde_json::Value> {
        (0..100).map(|i| json!({ "id": i, "value": i * 2 })).collect()
    }

    #[test]
    fn first_n_takes_prefix() {
        let sample = materialize(SamplingStrategy::FirstN, 3, None, &fixture());
        assert_eq!(sample.size, 3);
        assert_eq!(sample.rows[0]["id"], 0);
        assert_eq!(sample.rows[2]["id"], 2);
    }

    #[test]
    fn last_n_takes_suffix() {
        let sample = materialize(SamplingStrategy::LastN, 3, None, &fixture());
        assert_eq!(sample.size, 3);
        assert_eq!(sample.rows[0]["id"], 97);
        assert_eq!(sample.rows[2]["id"], 99);
    }

    #[test]
    fn reservoir_is_deterministic_under_seed() {
        let a = materialize(SamplingStrategy::Random, 10, Some(42), &fixture());
        let b = materialize(SamplingStrategy::Random, 10, Some(42), &fixture());
        assert_eq!(a.rows, b.rows);
        assert_eq!(a.size, 10);
    }

    #[test]
    fn reservoir_caps_at_population_size() {
        let small = (0..3).map(|i| json!({ "id": i })).collect::<Vec<_>>();
        let sample = materialize(SamplingStrategy::Random, 100, Some(1), &small);
        assert_eq!(sample.size, 3);
    }

    #[test]
    fn rfc3339_known_value() {
        // 2026-05-11T00:00:00Z = 1_778_457_600.
        assert_eq!(format_unix_secs_as_rfc3339(1_778_457_600), "2026-05-11T00:00:00Z");
    }
}
