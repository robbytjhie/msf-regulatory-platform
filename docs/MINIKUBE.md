# Minikube: full deployment and operations guide

This guide covers **first-time setup**, **day-to-day “pull code and rebuild”**, **checking cluster / namespace / pods**, **logs**, and **recovery** when something breaks.

Manifests live in **`infra/k8s/`** (main file: `msf.yaml`). Images must be named exactly:

- `msf/backend:latest`
- `msf/auth-node:latest`
- `msf/frontend-react:latest`

---

## Part 1 — Prerequisites (one time per machine)

1. **Install Docker** and ensure it runs:
   - **Windows:** Docker Desktop.
   - **Linux / WSL Ubuntu:** Docker Engine; in WSL, enable Docker Desktop **WSL integration** for your distro, or install Docker inside WSL.

   Verify:

   ```bash
   docker ps
   ```

2. **Install Minikube**  
   Follow: https://minikube.sigs.k8s.io/docs/start/

3. **Install kubectl**  
   Follow: https://kubernetes.io/docs/tasks/tools/

4. **Clone the repo** (or use your existing folder):

   ```bash
   git clone https://github.com/robbytjhie/msf-regulatory-platform.git msf
   cd msf
   ```

---

## Part 2 — First-time Minikube cluster

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

### 2.2 If `minikube status` fails with SSH errors (Linux/WSL)

Example: `ssh: unable to authenticate ... no supported methods remain`

The local Minikube profile/VM is broken or was created in a different environment. Reset:

```bash
minikube delete
minikube start --driver=docker
```

Nuclear option (all profiles):

```bash
minikube delete --all
minikube start --driver=docker
```

### 2.3 WSL vs Windows (important)

- Minikube + Docker **inside WSL** uses **WSL’s Docker**.
- Minikube + **Windows PowerShell** uses **Docker Desktop’s Windows engine**.

**Do not mix:** if you build images in Windows Docker but apply manifests from WSL Minikube (or the reverse), you will get **`ImagePullBackOff` / `ErrImagePull`**. Pick **one** environment for: `minikube start`, `docker build`, and `kubectl apply`.

---

## Part 3 — First-time deploy of MSF to Minikube

All commands assume repo root `msf/` (adjust path if yours differs).

### 3.1 Point Docker at Minikube’s Docker daemon

Images must be built **into the same Docker** Minikube uses.

**bash (Linux / WSL):**

```bash
eval "$(minikube -p minikube docker-env)"
```

**PowerShell (Windows):**

```powershell
minikube -p minikube docker-env --shell powershell | Invoke-Expression
```

Keep this shell for builds, or run `eval` / `Invoke-Expression` again in a new shell before building.

### 3.2 Build the three images

From the **repository root**:

```bash
cd ~/msf   # or D:\MSF on Windows — your path

docker build -t msf/backend:latest ./backend
docker build -t msf/auth-node:latest ./auth-node
docker build -t msf/frontend-react:latest ./frontend-react
```

### 3.3 Apply Kubernetes manifests

```bash
kubectl apply -f infra/k8s/msf.yaml
```

This creates namespace **`msf`** and Deployments/Services for **backend**, **auth-node**, **frontend-react**.

### 3.4 Wait for rollouts

```bash
kubectl -n msf rollout status deploy/backend
kubectl -n msf rollout status deploy/auth-node
kubectl -n msf rollout status deploy/frontend-react
```

### 3.5 Open the app in a browser

**Option A — let Minikube print the URL:**

```bash
minikube service frontend-react -n msf --url
```

**Option B — NodePort from manifest (`30080`):**

```bash
echo "http://$(minikube ip):30080"
```

Login path (if root does not redirect):

- `http://<that-host>:<port>/login`

---

## Part 4 — Day-to-day: pull latest code and rebuild

Use the **same** machine context (WSL or Windows) you used for the first deploy.

```bash
cd ~/msf
git pull origin main

# Always use Minikube’s Docker before building
eval "$(minikube -p minikube docker-env)"    # bash / WSL
# OR PowerShell:
# minikube -p minikube docker-env --shell powershell | Invoke-Expression

docker build -t msf/backend:latest ./backend
docker build -t msf/auth-node:latest ./auth-node
docker build -t msf/frontend-react:latest ./frontend-react

kubectl apply -f infra/k8s/msf.yaml

# Pick up new images without changing YAML
kubectl -n msf rollout restart deploy/backend
kubectl -n msf rollout restart deploy/auth-node
kubectl -n msf rollout restart deploy/frontend-react

kubectl -n msf rollout status deploy/backend
kubectl -n msf rollout status deploy/auth-node
kubectl -n msf rollout status deploy/frontend-react
```

If only the frontend changed, you can restart **only** `deploy/frontend-react`.

---

## Part 5 — Check cluster, namespace, and workloads

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

- Run **`eval "$(minikube docker-env)"`** (bash) in the shell you use to **`docker build`**.
- Rebuild the image tag exactly as in `msf.yaml` (e.g. `msf/frontend-react:latest`).
- Restart the deployment:

  ```bash
  kubectl -n msf rollout restart deploy/frontend-react
  ```

### 6.2 Pod `CrashLoopBackOff` / `Error` (image pulled but exits)

```bash
kubectl describe pod -n msf -l app=frontend-react
kubectl logs -n msf -l app=frontend-react --tail=200
```

Fix the app or manifest, rebuild if needed, restart rollout.

### 6.3 Rollout stuck (“old replicas pending termination” / progress deadline)

See what is running:

```bash
kubectl get pods -n msf
kubectl get rs -n msf
```

Force-delete a **stuck** pod name:

```bash
kubectl delete pod PODNAME -n msf --force --grace-period=0
```

Or reset **one** deployment:

```bash
kubectl -n msf delete deployment frontend-react
kubectl apply -f infra/k8s/msf.yaml
```

### 6.4 Remove only the MSF namespace (cluster keeps running)

```bash
kubectl delete namespace msf
```

To redeploy: `kubectl apply -f infra/k8s/msf.yaml` again (and ensure images exist in Minikube Docker).

### 6.5 Remove the whole Minikube cluster (“delete the node”)

This deletes the **local Kubernetes cluster** Minikube created (not your Git repo):

```bash
minikube delete
```

Or all profiles:

```bash
minikube delete --all
```

Then `minikube start --driver=docker` and repeat **Part 3** (docker-env, build, apply).

### 6.6 Stop Minikube without deleting

```bash
minikube stop
```

---

## Part 7 — Optional: leave Minikube Docker mode

If your shell was pointed at Minikube’s Docker and you want your normal Docker back, open a **new terminal**, or (bash):

```bash
eval "$(minikube -p minikube docker-env -u)"
```

(If `-u` is unsupported on your version, a new shell is enough.)

---

## Part 8 — Helper script (Windows)

From **`infra/k8s`**:

```powershell
cd D:\MSF\infra\k8s
.\deploy-minikube.ps1
```

The script: sets PowerShell docker-env, builds three images with paths `../../backend`, etc., applies `msf.yaml`, and waits for rollouts. On **Linux/WSL**, use the **manual** commands in Part 3–4 instead (or translate `docker-env` to bash).

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
| Reset Minikube completely | `minikube delete` then `minikube start` |

---

## Part 10 — Known gotcha (nginx + capabilities)

The **frontend** image is **nginx**. Dropping **all** Linux capabilities in the container `securityContext` can make nginx exit immediately (**exit code 1**). This repo’s `msf.yaml` is adjusted so the frontend can start while keeping other hardening where possible. If you fork the manifest, avoid `capabilities: drop: ["ALL"]` on nginx unless you switch to a non-root + high-port pattern.
