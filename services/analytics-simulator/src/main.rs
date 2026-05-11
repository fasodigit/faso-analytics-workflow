// FASO-ANALYTICS-WORKFLOW — analytics-simulator binary entry point.
//
// Cf. ADR-004 (Podman rootless sandbox). The simulator process itself is a
// long-running daemon : it accepts simulation requests over REST/JSON, fans
// each request out to a brand new sandbox container, enforces strict timeouts,
// and emits SIMULATION_* events to Redpanda (event emission stubbed in Phase 2).

use std::net::SocketAddr;

use analytics_simulator::{config::SimulatorConfig, service};
use tracing::info;
use tracing_subscriber::EnvFilter;

#[tokio::main]
async fn main() -> Result<(), Box<dyn std::error::Error>> {
    tracing_subscriber::fmt()
        .with_env_filter(EnvFilter::try_from_default_env().unwrap_or_else(|_| EnvFilter::new("info")))
        .json()
        .init();

    let cfg = SimulatorConfig::from_env()?;
    let addr: SocketAddr = cfg.grpc_addr.parse()?;

    info!(
        addr = %addr,
        sandbox_image = %cfg.sandbox_image,
        seccomp = %cfg.seccomp_profile,
        apparmor = %cfg.apparmor_profile,
        "analytics-simulator starting (Phase 2 — REST API)"
    );

    let app = service::build_router(cfg).await?;
    let listener = tokio::net::TcpListener::bind(addr).await?;
    axum::serve(listener, app)
        .with_graceful_shutdown(shutdown_signal())
        .await?;

    Ok(())
}

async fn shutdown_signal() {
    let _ = tokio::signal::ctrl_c().await;
    info!("analytics-simulator received ctrl-c, shutting down");
}
