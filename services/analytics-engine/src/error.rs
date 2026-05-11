use thiserror::Error;

#[derive(Error, Debug)]
pub enum EngineError {
    #[error("invalid workflow definition: {0}")]
    InvalidDefinition(String),

    #[error("compilation error: {0}")]
    Compilation(String),

    #[error("execution error: {0}")]
    Execution(String),

    #[error("plan not found: {0}")]
    PlanNotFound(String),

    #[error("internal: {0}")]
    Internal(String),
}

impl From<EngineError> for tonic::Status {
    fn from(e: EngineError) -> Self {
        match e {
            EngineError::InvalidDefinition(m) => tonic::Status::invalid_argument(m),
            EngineError::PlanNotFound(m)      => tonic::Status::not_found(m),
            EngineError::Compilation(m)       => tonic::Status::failed_precondition(m),
            EngineError::Execution(m)         => tonic::Status::internal(m),
            EngineError::Internal(m)          => tonic::Status::internal(m),
        }
    }
}
