// Sandbox orchestration : builds the `podman run` argv that enforces every
// hardening flag mandated by ADR-004, spawns the container, and walks the
// SIGTERM→SIGKILL escalation when the wall-clock timeout fires.
//
// IMPORTANT (security rationale) : the argv builder is the single source of
// truth for the hardening posture. Tests assert each flag is present so a
// future refactor cannot silently weaken the sandbox.

use std::process::{ExitStatus, Stdio};
use std::time::{Duration, Instant};

use tokio::io::AsyncReadExt;
use tokio::process::{Child, Command};
use tokio::time::timeout;
use tracing::{info, warn};

use crate::error::SandboxError;

/// Description of one sandbox invocation.
#[derive(Debug, Clone)]
pub struct SandboxSpec {
    pub sim_id:           String,
    pub sandbox_image:    String,
    pub seccomp_profile:  String,
    pub apparmor_profile: String,
    pub memory_max:       String,
    pub cpu_max:          String,
    pub pids_limit:       u32,
    /// Wall-clock budget in seconds before the SIGTERM is sent.
    pub timeout_sec:      u64,
    /// Grace window between SIGTERM and SIGKILL. Per ADR-004 §"Timeouts" we
    /// give the sandbox 10s to flush buffered telemetry before forcing kill.
    pub kill_grace_sec:   u64,
    /// Env vars to forward into the sandbox (notably SIM_ID, DRAGONFLY_URL).
    pub env:              Vec<(String, String)>,
}

/// What happened during one sandbox run.
#[derive(Debug)]
pub struct SandboxOutcome {
    pub exit_status: Option<ExitStatus>,
    pub stdout:      String,
    pub stderr:      String,
    pub duration:    Duration,
    pub timed_out:   bool,
}

/// Build the canonical `podman run` argv per ADR-004 §"Décision".
///
/// SECURITY : every flag below is part of the defense-in-depth posture. Do not
/// drop one without updating ADR-004 and adapting the security test below.
pub fn build_podman_argv(spec: &SandboxSpec) -> Vec<String> {
    let container_name = format!("faso-sandbox-{}", spec.sim_id);
    let mut argv: Vec<String> = vec![
        "run".to_string(),
        "--rm".to_string(),
        "--name".to_string(),
        container_name,
        // Filesystem : read-only root + ephemeral tmpfs writable area.
        "--read-only".to_string(),
        "--tmpfs".to_string(),
        "/tmp:size=256m,mode=1777".to_string(),
        // Network : slirp4netns with host loopback off. The actual egress ACL
        // (only the data source IP allowed) is layered on top by the host
        // firewall — slirp4netns alone does not enforce it.
        "--network=slirp4netns:port_handler=slirp4netns,allow_host_loopback=false".to_string(),
        // Capabilities : drop ALL. The sandbox only does TCP egress + CPU work,
        // it needs zero Linux capabilities.
        "--cap-drop=ALL".to_string(),
        // Prevent suid binaries from gaining privileges inside the namespace.
        "--security-opt=no-new-privileges".to_string(),
        // seccomp filter (allowlist + explicit deny of ptrace/mount/unshare/bpf
        // etc — see containers/seccomp/analytics-sandbox.json).
        format!("--security-opt=seccomp={}", spec.seccomp_profile),
        // AppArmor profile (LSM mediation — see containers/apparmor/).
        format!("--security-opt=apparmor={}", spec.apparmor_profile),
        // cgroupv2 quotas — memory.max + cpu.max + pids.max.
        format!("--memory={}", spec.memory_max),
        format!("--cpus={}", spec.cpu_max),
        format!("--pids-limit={}", spec.pids_limit),
        // UID 65532 = distroless `nonroot`. Matches the sandbox image USER.
        "--user".to_string(),
        "65532:65532".to_string(),
    ];

    for (k, v) in &spec.env {
        argv.push("--env".to_string());
        argv.push(format!("{}={}", k, v));
    }

    argv.push(spec.sandbox_image.clone());
    argv
}

/// Run a sandbox container to completion, enforcing the wall-clock timeout.
///
/// On timeout : we send SIGTERM (graceful shutdown signal) directly to the
/// `podman run` child. After `kill_grace_sec` seconds we escalate by invoking
/// `podman kill --signal=KILL <name>` which removes the container even if the
/// process inside ignored SIGTERM.
pub async fn run_sandbox(spec: &SandboxSpec) -> Result<SandboxOutcome, SandboxError> {
    run_sandbox_with(spec, "podman").await
}

/// Test-friendly variant that allows substituting the `podman` binary.
pub async fn run_sandbox_with(
    spec: &SandboxSpec,
    podman_bin: &str,
) -> Result<SandboxOutcome, SandboxError> {
    let argv = build_podman_argv(spec);
    info!(
        sim_id = %spec.sim_id,
        timeout_sec = spec.timeout_sec,
        "spawning sandbox container"
    );

    let start = Instant::now();
    let mut child = Command::new(podman_bin)
        .args(&argv)
        .stdin(Stdio::null())
        .stdout(Stdio::piped())
        .stderr(Stdio::piped())
        .kill_on_drop(true)
        .spawn()
        .map_err(|e| SandboxError::SpawnFailed(format!("{}: {}", podman_bin, e)))?;

    let outcome = match timeout(Duration::from_secs(spec.timeout_sec), child.wait()).await {
        Ok(Ok(status)) => {
            let (stdout, stderr) = drain_child(&mut child).await;
            SandboxOutcome {
                exit_status: Some(status),
                stdout,
                stderr,
                duration: start.elapsed(),
                timed_out: false,
            }
        }
        Ok(Err(e)) => return Err(SandboxError::Io(e)),
        Err(_) => {
            warn!(sim_id = %spec.sim_id, "sandbox wall-clock timeout reached — escalating to SIGTERM");
            escalate_kill(
                &mut child,
                &spec.sim_id,
                spec.kill_grace_sec,
                podman_bin,
            )
            .await?;
            let (stdout, stderr) = drain_child(&mut child).await;
            SandboxOutcome {
                exit_status: None,
                stdout,
                stderr,
                duration: start.elapsed(),
                timed_out: true,
            }
        }
    };

    Ok(outcome)
}

/// SIGTERM → wait grace window → `podman kill --signal=KILL` + SIGKILL on the
/// `podman run` parent. We use the container name to address the container so
/// it works whether the `podman run` parent is still alive or not. This
/// matches ADR-004's mandate : "Dépassement → SIGTERM puis SIGKILL à T+10s
/// grâce".
async fn escalate_kill(
    child: &mut Child,
    sim_id: &str,
    grace_sec: u64,
    podman_bin: &str,
) -> Result<(), SandboxError> {
    if let Some(pid) = child.id() {
        send_sigterm(pid)?;
    }

    match timeout(Duration::from_secs(grace_sec), child.wait()).await {
        Ok(_) => Ok(()),
        Err(_) => {
            // Still alive — force-kill the container by name (best-effort —
            // the binary may not exist in tests). This bypasses any stuck
            // `podman run` and asks the daemon to reap the workload.
            let container_name = format!("faso-sandbox-{}", sim_id);
            let _ = Command::new(podman_bin)
                .args(["kill", "--signal=KILL", &container_name])
                .stdout(Stdio::null())
                .stderr(Stdio::null())
                .status()
                .await;
            // Drop stdio handles first so any orphan grandchild holding our
            // pipes can't keep `wait()` parked indefinitely.
            drop(child.stdout.take());
            drop(child.stderr.take());
            // Final escalation : SIGKILL on the parent. SIGKILL is uncatchable
            // so the kernel reaps the process. We then cap the post-kill wait
            // to grace_sec to guarantee bounded return even if the process is
            // a zombie that someone else has to reap.
            let _ = child.start_kill();
            let _ = timeout(Duration::from_secs(grace_sec.max(2)), child.wait()).await;
            Err(SandboxError::Timeout { grace_sec })
        }
    }
}

#[cfg(unix)]
fn send_sigterm(pid: u32) -> Result<(), SandboxError> {
    use nix::sys::signal::{kill, Signal};
    use nix::unistd::Pid;
    kill(Pid::from_raw(pid as i32), Signal::SIGTERM)
        .map_err(|e| SandboxError::Signal(e.to_string()))
}

#[cfg(not(unix))]
fn send_sigterm(_pid: u32) -> Result<(), SandboxError> {
    Err(SandboxError::Signal("SIGTERM only supported on unix".into()))
}

async fn drain_child(child: &mut Child) -> (String, String) {
    let mut out_buf = String::new();
    let mut err_buf = String::new();
    if let Some(mut stdout) = child.stdout.take() {
        let _ = stdout.read_to_string(&mut out_buf).await;
    }
    if let Some(mut stderr) = child.stderr.take() {
        let _ = stderr.read_to_string(&mut err_buf).await;
    }
    (out_buf, err_buf)
}

#[cfg(test)]
mod tests {
    use super::*;

    fn spec() -> SandboxSpec {
        SandboxSpec {
            sim_id:           "01933ad0-1c0e-7000-8000-aaaaaaaaaaaa".into(),
            sandbox_image:    "faso/analytics-sandbox:1.0".into(),
            seccomp_profile:  "/etc/faso/seccomp/analytics-sandbox.json".into(),
            apparmor_profile: "faso-analytics-sandbox".into(),
            memory_max:       "2g".into(),
            cpu_max:          "2.0".into(),
            pids_limit:       64,
            timeout_sec:      60,
            kill_grace_sec:   10,
            env:              vec![("SIM_ID".into(), "01933ad0-1c0e-7000-8000-aaaaaaaaaaaa".into())],
        }
    }

    /// SECURITY-CRITICAL : guards every hardening flag mandated by ADR-004.
    /// Adding/removing flags here must be matched by an ADR-004 update.
    #[test]
    fn sandbox_command_includes_all_hardening_flags() {
        let argv = build_podman_argv(&spec());
        let cmdline = argv.join(" ");

        for flag in [
            "run",
            "--rm",
            "--read-only",
            "--tmpfs",
            "/tmp:size=256m,mode=1777",
            "--network=slirp4netns:port_handler=slirp4netns,allow_host_loopback=false",
            "--cap-drop=ALL",
            "--security-opt=no-new-privileges",
            "--security-opt=seccomp=/etc/faso/seccomp/analytics-sandbox.json",
            "--security-opt=apparmor=faso-analytics-sandbox",
            "--memory=2g",
            "--cpus=2.0",
            "--pids-limit=64",
            "--user",
            "65532:65532",
            "faso/analytics-sandbox:1.0",
        ] {
            assert!(
                cmdline.contains(flag),
                "hardening flag {flag:?} missing from argv: {cmdline}"
            );
        }
        // Sim_id is encoded in --name so we can `podman kill` by name later.
        assert!(cmdline.contains("faso-sandbox-01933ad0-1c0e-7000-8000-aaaaaaaaaaaa"));
        // SIM_ID is forwarded as an env var to the sandbox process.
        assert!(cmdline.contains("--env"));
        assert!(cmdline.contains("SIM_ID=01933ad0-1c0e-7000-8000-aaaaaaaaaaaa"));
    }

    /// Build a tiny shell script that emulates `podman` for tests. If invoked
    /// with first arg `kill` (the escalation path), it exits immediately.
    /// Otherwise it runs the `workload` shell snippet.
    fn fake_podman_script(workload: &str) -> std::path::PathBuf {
        use std::io::Write;
        use std::os::unix::fs::PermissionsExt;
        let dir = std::env::temp_dir();
        let path = dir.join(format!(
            "fake-podman-{}-{}.sh",
            std::process::id(),
            uuid::Uuid::now_v7().simple()
        ));
        let mut f = std::fs::File::create(&path).unwrap();
        writeln!(f, "#!/bin/sh").unwrap();
        // First positional arg distinguishes `podman run ...` from `podman kill ...`.
        writeln!(f, "case \"$1\" in").unwrap();
        writeln!(f, "  kill) exit 0 ;;").unwrap();
        writeln!(f, "esac").unwrap();
        writeln!(f, "{}", workload).unwrap();
        drop(f);
        let mut perm = std::fs::metadata(&path).unwrap().permissions();
        perm.set_mode(0o755);
        std::fs::set_permissions(&path, perm).unwrap();
        path
    }

    /// SECURITY-CRITICAL : when the sandbox exceeds its wall-clock budget the
    /// orchestrator MUST send SIGTERM and escalate to SIGKILL after the grace
    /// window. This test mocks `podman` with a shell script that sleeps for
    /// 60s — far longer than `timeout_sec=1` — and verifies the function
    /// returns with `timed_out=true` (SIGTERM honoured) inside the budget.
    #[tokio::test]
    async fn sandbox_timeout_signals_sigterm_then_sigkill() {
        let podman_bin = fake_podman_script("exec sleep 60");
        let mut s = spec();
        s.timeout_sec = 1;
        s.kill_grace_sec = 2;

        let started = Instant::now();
        let outcome = run_sandbox_with(&s, podman_bin.to_str().unwrap())
            .await
            .expect("fake podman must spawn");

        assert!(outcome.timed_out, "outcome must be flagged timed_out");
        // SIGTERM should reap the child well before the deadline below.
        assert!(
            started.elapsed() < Duration::from_secs(10),
            "timeout escalation took too long: {:?}",
            started.elapsed()
        );
        let _ = std::fs::remove_file(&podman_bin);
    }

    /// Companion test : SIGTERM-resistant workload must be SIGKILLed. We trap
    /// SIGTERM in the shell so the workload only dies under SIGKILL (which is
    /// what `child.start_kill()` + `podman kill --signal=KILL` deliver).
    #[tokio::test]
    #[cfg(unix)]
    async fn sandbox_sigterm_resistant_process_gets_sigkilled() {
        let podman_bin = fake_podman_script("trap '' TERM; sleep 60");
        let mut s = spec();
        s.timeout_sec = 1;
        s.kill_grace_sec = 1;

        let started = Instant::now();
        let result = run_sandbox_with(&s, podman_bin.to_str().unwrap()).await;

        match result {
            Err(SandboxError::Timeout { .. }) => {}
            Ok(outcome) => assert!(outcome.timed_out, "must signal timed_out=true"),
            Err(e) => panic!("unexpected error variant: {e:?}"),
        }
        assert!(
            started.elapsed() < Duration::from_secs(15),
            "escalation took too long: {:?}",
            started.elapsed()
        );
        let _ = std::fs::remove_file(&podman_bin);
    }
}
