// Runtime configuration sourced from environment variables. Defaults mirror
// the values pinned by ADR-004 (memory 2g, cpus 2.0, timeouts 60/300/600).

use std::env;

use crate::error::SimulatorError;

#[derive(Debug, Clone)]
pub struct SimulatorConfig {
    pub grpc_addr:        String,
    pub dragonfly_url:    String,
    pub sandbox_image:    String,
    pub seccomp_profile:  String,
    pub apparmor_profile: String,
    pub timeout_small:    u64,
    pub timeout_medium:   u64,
    pub timeout_large:    u64,
    pub memory_max:       String,
    pub cpu_max:          String,
    pub pids_limit:       u32,
    /// Grace window between SIGTERM and SIGKILL (ADR-004 §"Timeouts").
    pub kill_grace_sec:   u64,
}

impl SimulatorConfig {
    pub fn from_env() -> Result<Self, SimulatorError> {
        Ok(Self {
            grpc_addr:        env::var("SIMULATOR_GRPC_ADDR").unwrap_or_else(|_| "0.0.0.0:50052".to_string()),
            dragonfly_url:    env::var("DRAGONFLY_URL").unwrap_or_else(|_| "redis://localhost:6379".to_string()),
            sandbox_image:    env::var("SANDBOX_IMAGE").unwrap_or_else(|_| "faso/analytics-sandbox:1.0".to_string()),
            seccomp_profile:  env::var("SECCOMP_PROFILE").unwrap_or_else(|_| "/etc/faso/seccomp/analytics-sandbox.json".to_string()),
            apparmor_profile: env::var("APPARMOR_PROFILE").unwrap_or_else(|_| "faso-analytics-sandbox".to_string()),
            timeout_small:    parse_u64("SANDBOX_TIMEOUT_SEC_SMALL", 60)?,
            timeout_medium:   parse_u64("SANDBOX_TIMEOUT_SEC_MEDIUM", 300)?,
            timeout_large:    parse_u64("SANDBOX_TIMEOUT_SEC_LARGE", 600)?,
            memory_max:       env::var("SANDBOX_MEMORY_MAX").unwrap_or_else(|_| "2g".to_string()),
            cpu_max:          env::var("SANDBOX_CPU_MAX").unwrap_or_else(|_| "2.0".to_string()),
            pids_limit:       parse_u32("SANDBOX_PIDS_LIMIT", 64)?,
            kill_grace_sec:   parse_u64("SANDBOX_KILL_GRACE_SEC", 10)?,
        })
    }

    /// Choose the wall-clock timeout based on the requested sample size. ADR-004
    /// pins the brackets : ≤1k → 60s, ≤5k → 300s, ≤10k → 600s. Above 10k we
    /// still cap to `timeout_large` so a malformed request can't run forever.
    pub fn timeout_for_sample(&self, sample_size: usize) -> u64 {
        match sample_size {
            0..=1_000  => self.timeout_small,
            1_001..=5_000  => self.timeout_medium,
            _ => self.timeout_large,
        }
    }
}

fn parse_u64(key: &str, default: u64) -> Result<u64, SimulatorError> {
    match env::var(key) {
        Ok(v) => v.parse::<u64>().map_err(|_| {
            SimulatorError::Config(format!("env {} must be a u64, got {:?}", key, v))
        }),
        Err(_) => Ok(default),
    }
}

fn parse_u32(key: &str, default: u32) -> Result<u32, SimulatorError> {
    match env::var(key) {
        Ok(v) => v.parse::<u32>().map_err(|_| {
            SimulatorError::Config(format!("env {} must be a u32, got {:?}", key, v))
        }),
        Err(_) => Ok(default),
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn timeout_for_sample_brackets() {
        let cfg = SimulatorConfig {
            grpc_addr:        "0.0.0.0:50052".into(),
            dragonfly_url:    "redis://x".into(),
            sandbox_image:    "img:1".into(),
            seccomp_profile:  "/s".into(),
            apparmor_profile: "a".into(),
            timeout_small:    60,
            timeout_medium:   300,
            timeout_large:    600,
            memory_max:       "2g".into(),
            cpu_max:          "2.0".into(),
            pids_limit:       64,
            kill_grace_sec:   10,
        };
        assert_eq!(cfg.timeout_for_sample(0),       60);
        assert_eq!(cfg.timeout_for_sample(1_000),   60);
        assert_eq!(cfg.timeout_for_sample(1_001),   300);
        assert_eq!(cfg.timeout_for_sample(5_000),   300);
        assert_eq!(cfg.timeout_for_sample(5_001),   600);
        assert_eq!(cfg.timeout_for_sample(50_000),  600);
    }
}
