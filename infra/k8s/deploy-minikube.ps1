$ErrorActionPreference = "Stop"

minikube status | Out-Null
minikube -p minikube docker-env --shell powershell | Invoke-Expression

docker build -t msf/backend:latest ../../backend
docker build -t msf/auth-node:latest ../../auth-node
docker build -t msf/frontend-react:latest ../../frontend-react

kubectl apply -f ./msf.yaml
kubectl -n msf rollout status deploy/backend
kubectl -n msf rollout status deploy/auth-node
kubectl -n msf rollout status deploy/frontend-react

Write-Host "Open app:"
Write-Host "  minikube service frontend-react -n msf --url"
