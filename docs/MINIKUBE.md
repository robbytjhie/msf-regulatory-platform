# Minikube — where to find the guides

MSF is deployed to Minikube using **`infra/k8s/msf.yaml`** and three local images: `msf/backend:latest`, `msf/auth-node:latest`, `msf/frontend-react:latest`.

**Pick one environment and stay on it** (do not build images in Windows Docker and deploy from WSL Minikube, or the reverse — you will get `ImagePullBackOff`).

| Your environment | Open this guide |
|--------------------|-----------------|
| **Ubuntu** or **WSL** (bash) | **[`MINIKUBE-UBUNTU.md`](MINIKUBE-UBUNTU.md)** |
| **Windows** (PowerShell) | **[`MINIKUBE-WINDOWS.md`](MINIKUBE-WINDOWS.md)** |

Each file is a **full** walkthrough: prerequisites, first cluster, first deploy, daily pull/rebuild, `kubectl` checks and logs, troubleshooting, and cluster reset.
