# Minikube on Windows (PowerShell) — MSF deployment guide

Use this guide if you work in **Windows PowerShell** (or Windows Terminal → PowerShell). For **Ubuntu / WSL (bash)**, see **`MINIKUBE-UBUNTU.md`**.

Do **not** mix: Minikube here uses **Docker Desktop’s Windows engine**. Building in **WSL** Docker while deploying from **Windows** Minikube causes **`ImagePullBackOff` / `ErrImagePull`**.

Manifests: **`infra/k8s/msf.yaml`**. Images must be named:

- `msf/backend:latest`
- `msf/auth-node:latest`
- `msf/frontend-react:latest`

Default paths below use **`D:\MSF`** — change to your clone location.

---

## Contents

1. [Prerequisites](#1-prerequisites)
2. [First-time Minikube cluster](#2-first-time-minikube-cluster)
3. [First-time deploy of MSF](#3-first-time-deploy-of-msf)
4. [Day-to-day: pull and rebuild](#4-day-to-day-pull-and-rebuild)
5. [Check cluster, namespace, pods, logs](#5-check-cluster-namespace-pods-logs)
6. [Troubleshooting and cleanup](#6-troubleshooting-and-cleanup)
7. [Leave Minikube Docker mode](#7-leave-minikube-docker-mode-optional)
8. [One-shot script](#8-one-shot-script-deploy-minikubeps1)
9. [Quick reference](#9-quick-reference)
10. [Nginx + capabilities note](#10-nginx--capabilities-note)

---

## 1. Prerequisites

1. **Docker Desktop** for Windows (running).

   ```powershell
   docker ps
   ```

2. **Minikube** — https://minikube.sigs.k8s.io/docs/start/

3. **kubectl** — https://kubernetes.io/docs/tasks/tools/

4. **Clone** (adjust drive/path):

   ```powershell
   git clone https://github.com/robbytjhie/msf-regulatory-platform.git D:\MSF
   cd D:\MSF
   ```

---

## 2. First-time Minikube cluster

```powershell
minikube start --driver=docker
minikube status
kubectl config current-context
```

Context should be **`minikube`**.

If the cluster is broken, reset:

```powershell
minikube delete
minikube start --driver=docker
```

All profiles:

```powershell
minikube delete --all
minikube start --driver=docker
```

---

## 3. First-time deploy of MSF

From **repository root** (`D:\MSF` — contains `backend\`, `frontend-react\`, `infra\`).

```powershell
cd D:\MSF

minikube -p minikube docker-env --shell powershell | Invoke-Expression

docker build -t msf/backend:latest .\backend
docker build -t msf/auth-node:latest .\auth-node
docker build -t msf/frontend-react:latest .\frontend-react

kubectl apply -f infra\k8s\msf.yaml

kubectl -n msf rollout status deploy/backend
kubectl -n msf rollout status deploy/auth-node
kubectl -n msf rollout status deploy/frontend-react
```

**Open the UI:**

```powershell
minikube service frontend-react -n msf --url
```

or NodePort **30080**:

```powershell
Write-Host ("http://{0}:30080" -f (minikube ip))
```

Login: `http://<host>:<port>/login` if needed.

---

## 4. Day-to-day: pull and rebuild

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

Frontend only:

```powershell
kubectl -n msf rollout restart deploy/frontend-react
kubectl -n msf rollout status deploy/frontend-react
```

---

## 5. Check cluster, namespace, pods, logs

```powershell
minikube status
kubectl cluster-info
kubectl get nodes
kubectl config current-context

kubectl get ns msf
kubectl get all -n msf

kubectl get pods -n msf -o wide
kubectl get pods -n msf -w

kubectl get svc -n msf
kubectl get endpoints -n msf

kubectl describe deployment frontend-react -n msf
kubectl describe pod -n msf -l app=frontend-react
```

**Logs:**

```powershell
kubectl logs -n msf deploy/backend --tail=100
kubectl logs -n msf deploy/auth-node --tail=100
kubectl logs -n msf deploy/frontend-react --tail=100

kubectl logs -n msf deploy/frontend-react --previous --tail=100

kubectl logs -n msf deploy/backend -f
```

---

## 6. Troubleshooting and cleanup

### ImagePullBackOff / ErrImagePull

```powershell
minikube -p minikube docker-env --shell powershell | Invoke-Expression
cd D:\MSF
docker build -t msf/frontend-react:latest .\frontend-react
kubectl -n msf rollout restart deploy/frontend-react
```

### CrashLoopBackOff / Error

```powershell
kubectl describe pod -n msf -l app=frontend-react
kubectl logs -n msf -l app=frontend-react --tail=200
```

### Rollout stuck

```powershell
kubectl get pods -n msf
kubectl get rs -n msf
kubectl delete pod PODNAME -n msf --force --grace-period=0
```

Reset one deployment:

```powershell
kubectl -n msf delete deployment frontend-react
kubectl apply -f infra\k8s\msf.yaml
```

### Delete namespace `msf` only

```powershell
kubectl delete namespace msf
```

### Delete whole Minikube cluster

```powershell
minikube delete
minikube delete --all   # all profiles
minikube start --driver=docker
```

Then repeat **section 3**.

### Stop Minikube

```powershell
minikube stop
```

---

## 7. Leave Minikube Docker mode (optional)

Open a **new** PowerShell window so `docker` talks to Docker Desktop again, or see Minikube docs for unsetting `DOCKER_HOST` for your version.

---

## 8. One-shot script (`deploy-minikube.ps1`)

From **`infra\k8s`**:

```powershell
cd D:\MSF\infra\k8s
.\deploy-minikube.ps1
```

Sets docker-env, builds `..\..\backend`, `..\..\auth-node`, `..\..\frontend-react`, applies `msf.yaml`, waits for rollouts.

---

## 9. Quick reference

| Goal | Command |
|------|--------|
| Cluster up? | `minikube status` |
| Context | `kubectl config current-context` |
| All in `msf` | `kubectl get all -n msf` |
| Pod events | `kubectl describe pod -n msf POD` |
| Logs | `kubectl logs -n msf deploy/DEPLOY --tail=100` |
| UI URL | `minikube service frontend-react -n msf --url` |
| Reset cluster | `minikube delete` then `minikube start --driver=docker` |

---

## 10. Nginx + capabilities note

The **frontend** image is **nginx**. Dropping **all** capabilities in the container `securityContext` can make nginx exit with **code 1**. This repo’s `msf.yaml` relaxes only the frontend container so nginx can start. See `infra/k8s/msf.yaml` if you fork manifests.
