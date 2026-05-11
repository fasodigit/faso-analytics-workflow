# ADR-004 — Sandbox de simulation : Podman rootless + seccomp + AppArmor

| Field | Value |
|---|---|
| **Status** | Proposed — default per ultraplan v0.1-DRAFT |
| **Decision date** | 2026-05-11 |

## Contexte

Une simulation de workflow exécute du **code utilisateur indirect** : transformations DataFusion compilées depuis une définition JSON contrôlée par un analyste. Bien que ce code soit déclaratif (pas de scripting libre), l'attaquant peut tenter :

- de provoquer un DoS via une transformation pathologique (cartésien × N millions),
- d'exfiltrer des données via un canal latéral (DNS, écriture disque),
- de saturer la mémoire/CPU de l'hôte,
- d'accéder à des ressources non autorisées (NFS, métadonnées cloud).

Le sandbox **doit** isoler chaque simulation tout en restant raisonnable en latence (cible : démarrage < 1 s pour échantillon 1 000 lignes).

## Options évaluées

| Option | Isolation | Démarrage | Sovereignty | Complexité |
|---|---|---|---|---|
| **A. Podman rootless + Containerfile durci + seccomp + AppArmor + slirp4netns ACL** | Très bonne (user namespace + caps drop + LSM) | < 1 s | OK on-prem | Moyenne |
| **B. Firecracker microVM** | Excellente (KVM, hypervisor-level) | ~150 ms | OK on-prem | Élevée (build images custom, gestion réseau TAP) |
| **C. gVisor** | Bonne (syscalls interpretés userspace) | < 500 ms | OK | Moyenne (configuration runtime) |
| **D. Wasm sandbox (wasmtime)** | Très bonne (sans syscalls hôte) | < 100 ms | OK | Élevée (DataFusion en WASM = pas trivial, perf dégradée) |
| **E. Pas de sandbox (in-process)** | Aucune | 0 ms | — | Faible |

## Décision

**Option A — Podman rootless** avec hardening multi-couches.

Chaque simulation se déroule dans un container Podman rootless éphémère avec :

```bash
podman run --rm \
  --read-only \
  --tmpfs /tmp:size=256m,mode=1777 \
  --network=slirp4netns:port_handler=slirp4netns,allow_host_loopback=false \
  --cap-drop=ALL \
  --security-opt=no-new-privileges \
  --security-opt=seccomp=/etc/faso/seccomp/analytics-sandbox.json \
  --security-opt=apparmor=faso-analytics-sandbox \
  --memory=2g --cpus=2.0 --pids-limit=64 \
  --user 65532:65532 \
  --rm \
  faso/analytics-sandbox:1.0
```

### Profil seccomp (extrait — JSON complet livré en L5)

Allowlist : syscalls strictement nécessaires à DataFusion + Tonic + lecteur Arrow IPC.

Denylist explicite (override de la base `default`) :
- `ptrace`, `process_vm_readv`, `process_vm_writev` (anti-debugging et exfiltration mémoire)
- `mount`, `umount`, `umount2` (pas de changement de FS)
- `unshare`, `setns` (pas de namespace escape)
- `keyctl`, `add_key`, `request_key` (pas d'accès keyring)
- `bpf`, `perf_event_open` (pas de tracing kernel)
- `personality`, `prctl(PR_SET_DUMPABLE)` (anti-fingerprinting)

### Profil AppArmor `faso-analytics-sandbox`

```
#include <tunables/global>
profile faso-analytics-sandbox flags=(attach_disconnected,mediate_deleted) {
  #include <abstractions/base>
  network inet stream,
  network inet6 stream,
  /usr/local/bin/analytics-sandbox ix,
  /tmp/** rwk,
  /proc/self/** r,
  deny /proc/[0-9]*/** w,
  deny /sys/** w,
  deny /etc/** w,
  deny /home/** rwklx,
}
```

### Quotas

- **Mémoire** : 2 GiB hard (cgroupv2 `memory.max`) → OOM-killer si dépassement.
- **CPU** : 2 vCPU hard (cgroupv2 `cpu.max`).
- **PIDs** : 64 max (anti fork-bomb).
- **Réseau** : `slirp4netns` avec règle ACL n'autorisant que :
  - L'IP de la source de données (YugabyteDB primary, DragonflyDB, Redpanda broker).
  - Le service `analytics-simulator` pour retour résultats.
  - **Bloque tout DNS sortant**, métadonnées cloud (`169.254.169.254`), broadcast LAN.

### Timeouts

- T+60 s pour ≤ 1 000 lignes échantillon.
- T+300 s pour ≤ 5 000 lignes échantillon.
- T+600 s pour ≤ 10 000 lignes échantillon (max simulation Q1).
- Dépassement → `SIGTERM` puis `SIGKILL` à T+10 s grâce → audit `SIMULATION_TIMEOUT`.

## Justification

1. **Démarrage rapide** : un Podman rootless cold-start moyenne 300-700 ms sur les Scale-a7 (sans pull, image en cache). Acceptable face à la cible < 1 s.
2. **Cohérence stack** : la plateforme utilise déjà Podman rootless partout. Pas d'introduction d'une nouvelle techno (Firecracker, gVisor) qui ajouterait un domaine de compétence d'ops.
3. **Sovereignty** : aucun composant tiers externe. Tout tourne sur OVHcloud bare-metal.
4. **Defense in depth** : 5 couches d'isolation (user namespace, caps, seccomp, AppArmor, cgroup limits, network ACL). Une faille de l'une est compensée par les autres.

## Conséquences

### Positives

- Démarrage simulation < 1 s typique.
- Surface d'attaque réduite à l'essentiel : DataFusion + Arrow + Tonic gRPC.
- Aucune exfiltration possible via réseau sortant (DNS bloqué).
- Audit complet : chaque démarrage de sandbox émet `SIMULATION_STARTED` avec `sandbox_id`, `seccomp_hash`, `apparmor_hash`.

### Négatives

- Le profil seccomp est sensible : un upgrade de DataFusion peut introduire un nouveau syscall non listé → simulation cassée. Mitigation : test d'intégration `cargo test --features sandbox` qui vérifie la matrice syscalls × version DataFusion.
- AppArmor doit être chargé au démarrage du host (`apparmor_parser -r /etc/apparmor.d/faso-analytics-sandbox`). Si AppArmor n'est pas dispo (RHEL/SELinux), fallback profil SELinux équivalent à fournir en Phase 1.
- Podman-in-Podman (le simulator lance des sandboxes) requiert `--privileged` minimal côté simulator OU socket Podman exposé en read-only.

## Conditions de réexamen

- Si la latence de cold-start dépasse 2 s p95 sous charge → évaluer Firecracker (option B).
- Si une CVE Podman/runc apparaît sans patch sous 7 j → bascule temporaire vers gVisor.
- Si une simulation doit s'exécuter sur plus de 10 000 lignes en simulation → réévaluer le pattern : peut-être un "shadow deployment" plutôt qu'une simulation est plus adapté.

## Référence

- Podman rootless : <https://docs.podman.io/en/latest/markdown/podman.1.html#rootless-mode>
- seccomp BPF : <https://man7.org/linux/man-pages/man2/seccomp.2.html>
- AppArmor : <https://apparmor.net>
- slirp4netns : <https://github.com/rootless-containers/slirp4netns>
- cgroup v2 : <https://docs.kernel.org/admin-guide/cgroup-v2.html>
