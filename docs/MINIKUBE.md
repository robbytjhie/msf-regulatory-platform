# Minikube: full deployment and operations guide

This guide covers **first-time setup**, **day-to-day “pull code and rebuild”**, **checking cluster / namespace / pods**, **logs**, and **recovery** when something breaks.

Manifests live in **`infra/k8s/`** (main file: `msf.yaml`). Images must be named exactly:

- `msf/backend:latest`
- `msf/auth-node:latest`
- `msf/frontend-react:latest`

**Pick one track and stay on it:** **Track A — Ubuntu / WSL (bash)** or **Track B — Windows (PowerShell)**. Do not mix Docker hosts (see [WSL vs Windows](#wsl-vs-windows-important)).

---

## Contents

| Section | What |
|--------|------|
| [Choose your environment](#choose-your-environment) | Ubuntu vs Windows, paths |
| [Part 1 — Prerequisites](#part-1--prerequisites-one-time-per-machine) | Docker, Minikube, kubectl, clone |
| [Part 2 — First-time Minikube cluster](#part-2--first-time-minikube-cluster) | Same commands in both shells |
| [Part 3 — First-time deploy of MSF](#part-3--first-time-deploy-of-msf-to-minikube) | **Split:** Track A (bash) · Track B (PowerShell) |
| [Part 4 — Day-to-day pull and rebuild](#part-4--day-to-day-pull-latest-code-and-rebuild) | **Split:** Track A · Track B |
| [Part 5 — Check cluster / pods / logs](#part-5--check-cluster-namespace-and-workloads) | **Same** kubectl (both tracks) |
| [Part 6 — Troubleshooting](#part-6--troubleshooting-and-cleanup) | Mostly same; docker-env called out per track |
| [Part 7 — Leave Minikube Docker mode](#part-7--optional-leave-minikube-docker-mode) | **Split:** Track A · Track B |
| [Part 8 — One-shot script (Windows)](#part-8--one-shot-script-windows-only) | `deploy-minikube.ps1` |
| [Part 9 — Quick reference](#part-9--quick-reference-table) | Table |
| [Part 10 — Nginx capabilities gotcha](#part-10--known-gotcha-nginx--capabilities) | Why frontend needed YAML tweak |

---

## Choose your environment

| | **Track A — Ubuntu / WSL (bash)** | **Track B — Windows (PowerShell)** |
|---|-----------------------------------|--------------------------------------|
| **Shell** | Terminal in Ubuntu or WSL | Windows PowerShell (or Windows Terminal → PowerShell) |
| **Typical repo path** | `~/msf` | `D:\MSF` (adjust drive/folder) |
| **Docker** | Docker in WSL / Docker Desktop **WSL integration** | Docker Desktop (Windows engine) |
| **Point Docker at Minikube** | `eval "$(minikube -p minikube docker-env)"` | `minikube -p minikube docker-env --shell powershell \| Invoke-Expression` |
| **Open app URL** | `minikube service … --url` or `echo "http://$(minikube ip):30080"` | Same Minikube commands work in PowerShell |

Everything that is **`kubectl`** or **`minikube start`** is the same in both tracks unless noted.

### WSL vs Windows (important)

- Minikube + Docker **inside WSL** uses **WSL’s Docker**.
- Minikube + **Windows PowerShell** uses **Docker Desktop’s Windows engine**.

**Do not mix:** building images in Windows Docker but applying from WSL Minikube (or the reverse) causes **`ImagePullBackOff` / `ErrImagePull`**. Use one track for **start**, **build**, and **apply**.

---

## Part 1 — Prerequisites (one time per machine)

1. **Install Docker** and ensure it runs:
   - **Track B (Windows):** Docker Desktop.
   - **Track A (Ubuntu / WSL):** Docker Engine, or Docker Desktop with **WSL integration** enabled for your distro.

   Verify (same command both tracks):

   ```bash
   docker ps
   ```

2. **Install Minikube** — https://minikube.sigs.k8s.io/docs/start/

3. **Install kubectl** — https://kubernetes.io/docs/tasks/tools/

4. **Clone the repo** (or use your existing folder).

   **Track A (bash):**

   ```bash
   git clone https://github.com/robbytjhie/msf-regulatory-platform.git ~/msf
   cd ~/msf
   ```

   **Track B (PowerShell):**

   ```powershell
   git clone https://github.com/robbytjhie/msf-regulatory-platform.git D:\MSF
   cd D:\MSF
   ```

---

## Part 2 — First-time Minikube cluster

Commands are the same in **bash** or **PowerShell**.

### 2.1 Start the cluster (Docker driver recommended)

```bash
minikube start --driver=docker
```

Check:

```bash
minikube status
kubectl config current-context
```

You want `current-context` to be **`minikube`** when working with this cluster.

### 2.2 If `minikube status` fails with SSH errors (common on Linux/WSL)

Example: `ssh: unable to authenticate ... no supported methods remain`

The local Minikube profile is broken or from another environment. Reset:

```bash
minikube delete
minikube start --driver=docker
```

Nuclear option (all profiles):

```bash
minikube delete --all
minikube start --driver=docker
```

---

## Part 3 — First-time deploy of MSF to Minikube

Complete steps for each track (repository root = where `backend/`, `frontend-react/`, `infra/` live).

---

### Track A — Ubuntu / WSL (bash)

```bash
cd ~/msf

# 1) Use Minikube’s Docker (required so the cluster sees your images)
eval "$(minikube -p minikube docker-env)"

# 2) Build images
docker build -t msf/backend:latest ./backend
docker build -t msf/auth-node:latest ./auth-node
docker build -t msf/frontend-react:latest ./frontend-react

# 3) Deploy
kubectl apply -f infra/k8s/msf.yaml

# 4) Wait for rollouts
kubectl -n msf rollout status deploy/backend
kubectl -n msf rollout status deploy/auth-node
kubectl -n msf rollout status deploy/frontend-react

# 5) Open URL (pick one)
minikube service frontend-react -n msf --url
# or fixed NodePort from msf.yaml:
echo "http://$(minikube ip):30080"
```

Login (if needed): `http://<host>:<port>/login`

---

### Track B — Windows (PowerShell)

```powershell
cd D:\MSF

# 1) Use Minikube’s Docker
minikube -p minikube docker-env --shell powershell | Invoke-Expression

# 2) Build images
docker build -t msf/backend:latest .\backend
docker build -t msf/auth-node:latest .\auth-node
docker build -t msf/frontend-react:latest .\frontend-react

# 3) Deploy
kubectl apply -f infra\k8s\msf.yaml

# 4) Wait for rollouts
kubectl -n msf rollout status deploy/backend
kubectl -n msf rollout status deploy/auth-node
kubectl -n msf rollout status deploy/frontend-react

# 5) Open URL (pick one)
minikube service frontend-react -n msf --url
# or NodePort 30080:
Write-Host ("http://{0}:30080" -f (minikube ip))
```

Login (if needed): `http://<host>:<port>/login`

---

## Part 4 — Day-to-day: pull latest code and rebuild

Use the **same track** you used for the first deploy (same Docker + Minikube).

---

### Track A — Ubuntu / WSL (bash)

```bash
cd ~/msf
git pull origin main

eval "$(minikube -p minikube docker-env)"

docker build -t msf/backend:latest ./backend
docker build -t msf/auth-node:latest ./auth-node
docker build -t msf/frontend-react:latest ./frontend-react

kubectl apply -f infra/k8s/msf.yaml

kubectl -n msf rollout restart deploy/backend
kubectl -n msf rollout restart deploy/auth-node
kubectl -n msf rollout restart deploy/frontend-react

kubectl -n msf rollout status deploy/backend
kubectl -n msf rollout status deploy/auth-node
kubectl -n msf rollout status deploy/frontend-react
```

If only the frontend changed, restart only:

```bash
kubectl -n msf rollout restart deploy/frontend-react
kubectl -n msf rollout status deploy/frontend-react
```

---

### Track B — Windows (PowerShell)

```powershell
cd D:\MSF
git pull origin main

minikube -p minikube docker-env --shell powershell | Invoke-Expression

docker build -t msf/backend:latest .\backend
docker build -t msf/auth-node:latest .\auth-node
docker build -t msf/frontend-react:latest .\frontend-react

kubectl apply -f infra\k8s\msf.yaml

kubectl -n msf rollout restart deploy/backend
kubectl -n msf rollout restart deploy/auth-node
kubectl -n msf rollout restart deploy/frontend-react

kubectl -n msf rollout status deploy/backend
kubectl -n msf rollout status deploy/auth-node
kubectl -n msf rollout status deploy/frontend-react
```

If only the frontend changed:

```powershell
kubectl -n msf rollout restart deploy/frontend-react
kubectl -n msf rollout status deploy/frontend-react
```

---

## Part 5 — Check cluster, namespace, and workloads

**Same commands in Track A and Track B** (bash or PowerShell).

### 5.1 Cluster / Minikube

```bash
minikube status
kubectl cluster-info
kubectl get nodes
kubectl config current-context
```

### 5.2 Namespace `msf`

```bash
kubectl get ns msf
kubectl get all -n msf
```

### 5.3 Pods (wide + watch)

```bash
kubectl get pods -n msf -o wide
kubectl get pods -n msf -w
```

Stop watching with `Ctrl+C`.

### 5.4 Services and endpoints

```bash
kubectl get svc -n msf
kubectl get endpoints -n msf
```

### 5.5 Describe a resource (events at bottom)

```bash
kubectl describe deployment frontend-react -n msf
kubectl describe pod -n msf -l app=frontend-react
```

### 5.6 Logs

**Current logs:**

```bash
kubectl logs -n msf deploy/backend --tail=100
kubectl logs -n msf deploy/auth-node --tail=100
kubectl logs -n msf deploy/frontend-react --tail=100
```

**Previous crashed instance (after restart):**

```bash
kubectl logs -n msf deploy/frontend-react --previous --tail=100
```

**Follow live:**

```bash
kubectl logs -n msf deploy/backend -f
```

---

## Part 6 — Troubleshooting and cleanup

### 6.1 Pod stuck `ImagePullBackOff` / `ErrImagePull`

Re-point Docker at Minikube, then rebuild and restart:

**Track A:**

```bash
eval "$(minikube -p minikube docker-env)"
cd ~/msf
docker build -t msf/frontend-react:latest ./frontend-react
kubectl -n msf rollout restart deploy/frontend-react
```

**Track B:**

```powershell
minikube -p minikube docker-env --shell powershell | Invoke-Expression
cd D:\MSF
docker build -t msf/frontend-react:latest .\frontend-react
kubectl -n msf rollout restart deploy/frontend-react
```

Use the correct image name for whichever service failed (`backend` / `auth-node` / `frontend-react`).

### 6.2 Pod `CrashLoopBackOff` / `Error` (image pulled but exits)

```bash
kubectl describe pod -n msf -l app=frontend-react
kubectl logs -n msf -l app=frontend-react --tail=200
```

Fix the app or manifest, rebuild on **your track**, restart rollout.

### 6.3 Rollout stuck (“old replicas pending termination” / progress deadline)

```bash
kubectl get pods -n msf
kubectl get rs -n msf
```

Force-delete a stuck pod (replace `PODNAME`):

```bash
kubectl delete pod PODNAME -n msf --force --grace-period=0
```

Or reset one deployment:

```bash
kubectl -n msf delete deployment frontend-react
kubectl apply -f infra/k8s/msf.yaml
```

(On Track B you may use `infra\k8s\msf.yaml` in PowerShell.)

### 6.4 Remove only the `msf` namespace (cluster keeps running)

```bash
kubectl delete namespace msf
```

Redeploy: `kubectl apply -f …/msf.yaml` again and ensure images exist in Minikube’s Docker (**Part 3**).

### 6.5 Remove the whole Minikube cluster (“delete the node”)

Deletes the **local** cluster Minikube created (not your Git repo):

```bash
minikube delete
```

Or all profiles:

```bash
minikube delete --all
```

Then `minikube start --driver=docker` and repeat **[Part 3](#part-3--first-time-deploy-of-msf-to-minikube)** for your track.

### 6.6 Stop Minikube without deleting

```bash
minikube stop
```

---

## Part 7 — Optional: leave Minikube Docker mode

After `docker-env`, normal `docker` commands target **Minikube’s** daemon. To return your default Docker:

**Track A (bash):**

```bash
eval "$(minikube -p minikube docker-env -u)"
```

If `-u` is unsupported on your Minikube version, open a **new** terminal.

**Track B (PowerShell):**

Open a **new** PowerShell window (simplest), or check Minikube docs for “unset” in your version.

---

## Part 8 — One-shot script (Windows only)

From **`infra\k8s`** on **Track B**:

```powershell
cd D:\MSF\infra\k8s
.\deploy-minikube.ps1
```

The script sets PowerShell docker-env, builds `../../backend`, `../../auth-node`, `../../frontend-react`, applies `msf.yaml`, and waits for rollouts.

**Track A:** use the **[Track A — Part 3](#track-a--ubuntu--wsl-bash)** block instead (no PowerShell script in-repo for bash).

---

## Part 9 — Quick reference table

| Goal | Command |
|------|--------|
| Cluster up? | `minikube status` |
| Right kubectl context? | `kubectl config current-context` |
| Everything in `msf` | `kubectl get all -n msf` |
| Pod details + events | `kubectl describe pod -n msf POD` |
| App logs | `kubectl logs -n msf deploy/DEPLOY --tail=100` |
| Open UI URL | `minikube service frontend-react -n msf --url` |
| Reset Minikube completely | `minikube delete` then `minikube start --driver=docker` |

---

## Part 10 — Known gotcha (nginx + capabilities)

The **frontend** image is **nginx**. Dropping **all** Linux capabilities in the container `securityContext` can make nginx exit immediately (**exit code 1**). This repo’s `msf.yaml` relaxes **only** the frontend container caps so nginx can start; pod-level `seccompProfile` remains. If you fork the manifest, avoid `capabilities: drop: ["ALL"]` on nginx unless you use non-root + high port.
