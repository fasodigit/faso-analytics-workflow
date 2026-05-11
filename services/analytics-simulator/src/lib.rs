// FASO-ANALYTICS-WORKFLOW — analytics-simulator (library entry point).
//
// Exposes modules used by both the binary and integration tests. The simulator
// orchestrates ephemeral Podman-rootless sandboxes per ADR-004.

pub mod config;
pub mod error;
pub mod sampling;
pub mod sandbox;
pub mod service;
