use thiserror::Error;

#[derive(Error, Debug)]
pub enum SimulatorError {
    #[error("config error: {0}")]
    Config(String),

    #[error("invalid request: {0}")]
    InvalidRequest(String),

    #[error("dragonfly error: {0}")]
    Dragonfly(String),

    #[error("sandbox error: {0}")]
    Sandbox(#[from] SandboxError),

    #[error("internal: {0}")]
    Internal(String),
}

#[derive(Error, Debug)]
pub enum SandboxError {
    #[error("podman spawn failure: {0}")]
    SpawnFailed(String),

    #[error("io error while running sandbox: {0}")]
    Io(#[from] std::io::Error),

    #[error("sandbox timed out after {grace_sec}s grace, container killed")]
    Timeout { grace_sec: u64 },

    #[error("sandbox exited with non-zero status: {0}")]
    NonZeroExit(i32),

    #[error("sandbox signal handling error: {0}")]
    Signal(String),
}

impl From<redis::RedisError> for SimulatorError {
    fn from(e: redis::RedisError) -> Self {
        SimulatorError::Dragonfly(e.to_string())
    }
}
