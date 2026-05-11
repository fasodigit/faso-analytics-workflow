# AppArmor profile — `faso-analytics-sandbox`

This directory ships the AppArmor LSM profile attached to every Podman-rootless
`analytics-sandbox` container, as mandated by **ADR-004 §"Profil AppArmor"**.

## Loading the profile on the host

```bash
sudo install -m 0644 containers/apparmor/faso-analytics-sandbox \
                     /etc/apparmor.d/faso-analytics-sandbox
sudo apparmor_parser -r /etc/apparmor.d/faso-analytics-sandbox
```

Verify the profile is loaded :

```bash
sudo aa-status | grep faso-analytics-sandbox
```

## Attaching the profile at container start

The simulator passes `--security-opt=apparmor=faso-analytics-sandbox` so the
kernel attaches the profile at container start. See
`services/analytics-simulator/src/sandbox.rs::build_podman_argv`.

## Hosts without AppArmor (RHEL / SELinux)

If AppArmor is not available (RHEL / Rocky / SELinux-only hosts), the
simulator falls back to passing `apparmor=unconfined` and relies on the seccomp
profile + SELinux's `container_t` policy. A dedicated SELinux profile will be
shipped in Phase 3 — tracked in ADR-004 §"Conséquences ➔ Négatives".

## Rules covered (high level)

| Rule                          | Why                                              |
|-------------------------------|--------------------------------------------------|
| `network inet/inet6 stream`   | Outbound TCP allowed (Dragonfly only — egress    |
|                               | IP filtering is done by host firewall, not LSM). |
| `/usr/local/bin/analytics-sandbox ix` | Only the sandbox binary may execute.     |
| `/tmp/** rwk`                 | Ephemeral scratch dir mounted as tmpfs.          |
| `/proc/self/** r`             | Self-inspection allowed.                         |
| `deny /proc/[0-9]*/** w`      | Block ptrace / proc tampering of other PIDs.     |
| `deny /sys/** w`              | Block writes to sysfs (e.g. cgroup tampering).   |
| `deny /etc/** w`              | No config tampering.                             |
| `deny /home/** rwklx`         | No access to host homes (volume-leak defense).   |
