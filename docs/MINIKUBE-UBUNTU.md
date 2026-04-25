# Minikube on Ubuntu / WSL (bash) — MSF deployment guide

Use this guide if you work in **Ubuntu** or **WSL** with **bash**. For **Windows PowerShell**, see **`MINIKUBE-WINDOWS.md`**.

Do **not** mix: Minikube here uses **WSL/Ubuntu’s Docker**. Building images in **Windows** Docker while applying from **WSL** Minikube causes **`ImagePullBackOff` / `ErrImagePull`**.

Manifests: **`infra/k8s/msf.yaml`**. Images must be named:

- `msf/backend:latest`
- `msf/auth-node:latest`
- `msf/frontend-react:latest`

---

## Contents

1. [Prerequisites](#1-prerequisites)
2. [First-time Minikube cluster](#2-first-time-minikube-cluster)
3. [First-time deploy of MSF](#3-first-time-deploy-of-msf)
4. [Day-to-day: pull and rebuild](#4-day-to-day-pull-and-rebuild)
5. [Check cluster, namespace, pods, logs](#5-check-cluster-namespace-pods-logs)
6. [Troubleshooting and cleanup](#6-troubleshooting-and-cleanup)
7. [Leave Minikube Docker mode](#7-leave-minikube-docker-mode-optional)
8. [Quick reference](#8-quick-reference)
9. [Nginx + capabilities note](#9-nginx--capabilities-note)

---

## 1. Prerequisites

1. **Docker** — Docker Engine on Linux, or **Docker Desktop** with **WSL integration** enabled for your distro.

   ```bash
   docker ps
   ```

2. **Minikube** — https://minikube.sigs.k8s.io/docs/start/

3. **kubectl** — https://kubernetes.io/docs/tasks/tools/

4. **Clone** (adjust path if needed):

   ```bash
   git clone https://github.com/robbytjhie/msf-regulatory-platform.git ~/msf
   cd ~/msf
   ```

---

## 2. First-time Minikube cluster

```bash
minikube start --driver=docker
minikube status
kubectl config current-context
```

Context should be **`minikube`**.

### SSH errors on `minikube status`

Example: `ssh: unable to authenticate ... no supported methods remain`

Reset the profile:

```bash
minikube delete
minikube start --driver=docker
```

All profiles:

```bash
minikube delete --all
minikube start --driver=docker
```

---

## 3. First-time deploy of MSF

From **repository root** (`~/msf` — folder that contains `backend/`, `frontend-react/`, `infra/`).

```bash
cd ~/msf

# Minikube must see images built in ITS Docker
eval "$(minikube -p minikube docker-env)"

docker build -t msf/backend:latest ./backend
docker build -t msf/auth-node:latest ./auth-node
docker build -t msf/frontend-react:latest ./frontend-react

kubectl apply -f infra/k8s/msf.yaml

kubectl -n msf rollout status deploy/backend
kubectl -n msf rollout status deploy/auth-node
kubectl -n msf rollout status deploy/frontend-react
```

**Open the UI:**

```bash
minikube service frontend-react -n msf --url
```

or (NodePort **30080** in `msf.yaml`):

```bash
echo "http://$(minikube ip):30080"
```

Login: `http://<host>:<port>/login` if the root URL does not redirect.

---

## 4. Day-to-day: pull and rebuild

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

Frontend only:

```bash
kubectl -n msf rollout restart deploy/frontend-react
kubectl -n msf rollout status deploy/frontend-react
```

---

## 5. Check cluster, namespace, pods, logs

```bash
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

```bash
kubectl logs -n msf deploy/backend --tail=100
kubectl logs -n msf deploy/auth-node --tail=100
kubectl logs -n msf deploy/frontend-react --tail=100

kubectl logs -n msf deploy/frontend-react --previous --tail=100

kubectl logs -n msf deploy/backend -f
```

---

## 6. Troubleshooting and cleanup

### ImagePullBackOff / ErrImagePull

```bash
eval "$(minikube -p minikube docker-env)"
cd ~/msf
docker build -t msf/frontend-react:latest ./frontend-react
kubectl -n msf rollout restart deploy/frontend-react
```

(Use `backend` or `auth-node` if that image failed.)

### CrashLoopBackOff / Error

```bash
kubectl describe pod -n msf -l app=frontend-react
kubectl logs -n msf -l app=frontend-react --tail=200
```

### Rollout stuck

```bash
kubectl get pods -n msf
kubectl get rs -n msf
kubectl delete pod PODNAME -n msf --force --grace-period=0
```

Reset one deployment:

```bash
kubectl -n msf delete deployment frontend-react
kubectl apply -f infra/k8s/msf.yaml
```

### Delete namespace `msf` only

```bash
kubectl delete namespace msf
```

Redeploy: `kubectl apply -f infra/k8s/msf.yaml` and rebuild images in Minikube Docker (**section 3**).

### Delete whole Minikube cluster

```bash
minikube delete
# or
minikube delete --all
minikube start --driver=docker
```

Then repeat **section 3**.

### Stop Minikube

```bash
minikube stop
```

---

## 7. Leave Minikube Docker mode (optional)

```bash
eval "$(minikube -p minikube docker-env -u)"
```

If `-u` fails on your Minikube version, open a **new** terminal.

---

## 8. Quick reference

| Goal | Command |
|------|--------|
| Cluster up? | `minikube status` |
| Context | `kubectl config current-context` |
| All resources in `msf` | `kubectl get all -n msf` |
| Pod events | `kubectl describe pod -n msf POD` |
| Logs | `kubectl logs -n msf deploy/DEPLOY --tail=100` |
| UI URL | `minikube service frontend-react -n msf --url` |
| Reset cluster | `minikube delete` then `minikube start --driver=docker` |

---

## 9. Nginx + capabilities note

The **frontend** image is **nginx**. Dropping **all** capabilities in the container `securityContext` can make nginx exit with **code 1**. This repo’s `msf.yaml` relaxes only the frontend container so nginx can start. See `infra/k8s/msf.yaml` if you fork manifests.
