// FASO-ANALYTICS-WORKFLOW — analytics-engine (Rust + DataFusion + Tonic gRPC).
//
// Architecture cf. ADR-001 :
//   - Service gRPC Tonic expose 3 RPC : Compile, Validate, Execute(stream).
//   - Streaming Arrow IPC pour les RecordBatch.
//   - Sources externes via SourceHandle (YugabyteDB primary, DragonflyDB, Redpanda).
//
// Phase 1 : Compile + Execute fonctionnent sur une source YugabyteDB SELECT trivial
//           + 1 filtre + 1 agrégat SUM.

use std::net::SocketAddr;
use tonic::{transport::Server, Request, Response, Status};
use tracing::{info, instrument};
use tracing_subscriber::EnvFilter;

// pub mod proto {
//     tonic::include_proto!("faso.analytics.engine.v1");
// }
// use proto::analytics_engine_server::{AnalyticsEngine, AnalyticsEngineServer};

#[derive(Default)]
pub struct AnalyticsEngineService;

// #[tonic::async_trait]
// impl AnalyticsEngine for AnalyticsEngineService {
//     #[instrument(skip(self, request))]
//     async fn compile(&self, request: Request<...>) -> Result<Response<...>, Status> {
//         // Lecture définition JSON → ExecutionPlan DataFusion
//         todo!("phase 1")
//     }
//
//     #[instrument(skip(self, request))]
//     async fn validate(&self, request: Request<...>) -> Result<Response<...>, Status> {
//         todo!("phase 1")
//     }
//
//     type ExecuteStream = ...; // Stream<Item = Result<RecordBatchChunk, Status>>
//     async fn execute(&self, request: Request<...>) -> Result<Response<Self::ExecuteStream>, Status> {
//         todo!("phase 1")
//     }
// }

#[tokio::main]
async fn main() -> Result<(), Box<dyn std::error::Error>> {
    tracing_subscriber::fmt()
        .with_env_filter(EnvFilter::from_default_env())
        .json()
        .init();

    let addr: SocketAddr = std::env::var("ENGINE_GRPC_ADDR")
        .unwrap_or_else(|_| "0.0.0.0:50051".to_string())
        .parse()?;

    info!("analytics-engine starting on {}", addr);

    // Phase 1 livrera l'enregistrement du service Tonic :
    // Server::builder()
    //     .add_service(AnalyticsEngineServer::new(AnalyticsEngineService::default()))
    //     .serve(addr)
    //     .await?;

    info!("analytics-engine scaffold ready — Phase 1 will wire DataFusion + Tonic");
    tokio::signal::ctrl_c().await?;
    Ok(())
}
