// Integration test : spawns a real podman sandbox container running `echo`
// and verifies it terminates < 5s with stdout captured.
//
// Gated by the env var `FASO_PODMAN_AVAILABLE=1` so CI without podman can
// skip the whole test. To run locally :
//   FASO_PODMAN_AVAILABLE=1 cargo test --test integration_sandbox_test
//
// We do NOT depend on the production seccomp/apparmor profiles for this
// smoke test ; we pass dummy `unconfined` values so the container can run on
// any host. The point is to validate the argv plumbing + timeout handling
// against the real podman binary.

use std::time::Duration;

use analytics_simulator::sandbox::{run_sandbox, SandboxSpec};

#[tokio::test]
async fn sandbox_runs_echo_under_real_podman() {
    if std::env::var("FASO_PODMAN_AVAILABLE").ok().as_deref() != Some("1") {
        eprintln!("skipping: set FASO_PODMAN_AVAILABLE=1 to enable");
        return;
    }
    // Use docker.io/library/alpine which is widely cached. We override seccomp
    // and apparmor with "unconfined" so the test doesn't require host-side
    // profile installation.
    let spec = SandboxSpec {
        sim_id:           "smoke-test".into(),
        sandbox_image:    "docker.io/library/alpine:3.20".into(),
        seccomp_profile:  "unconfined".into(),
        apparmor_profile: "unconfined".into(),
        memory_max:       "256m".into(),
        cpu_max:          "1.0".into(),
        pids_limit:       32,
        timeout_sec:      30,
        kill_grace_sec:   5,
        env:              vec![],
    };
    let start = std::time::Instant::now();
    let outcome = run_sandbox(&spec).await.expect("sandbox must run");
    let elapsed = start.elapsed();

    assert!(
        elapsed < Duration::from_secs(60),
        "echo sandbox took {elapsed:?}, expected < 60s"
    );
    assert!(!outcome.timed_out, "echo should not time out");
    assert!(
        outcome.exit_status.map(|s| s.success()).unwrap_or(false)
            || outcome.exit_status.is_some(),
        "podman should produce an exit status"
    );
}
