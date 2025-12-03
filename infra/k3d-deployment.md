# Cost-Free Kubernetes Deployment (k3d + Docker)

This guide replaces the old AWS EKS workflow with a completely free alternative built on [k3d](https://k3d.io/) (Kubernetes in Docker). It runs entirely on your local machine and mirrors the production topology described in this repository.

## Prerequisites
- Docker Desktop 24+
- k3d 5+
- kubectl 1.30+
- This repository cloned locally

## 1. Create a Lightweight Cluster
```powershell
k3d cluster create supplychain-auth ^
  --servers 1 ^
  --agents 2 ^
  --port "80:80@loadbalancer" ^
  --port "443:443@loadbalancer"
```
This provisions a 3-node cluster with an embedded Traefik ingress controller and a free load balancer bound to localhost.

## 2. Point kubectl to k3d
```powershell
kubectl config use-context k3d-supplychain-auth
kubectl get nodes
```

## 3. Create Namespace & Shared Resources
```powershell
kubectl apply -f infra/k8s/namespace.yaml
kubectl apply -f infra/k8s/mongodb.yaml
kubectl apply -f infra/k8s/postgresql.yaml
kubectl apply -f infra/k8s/redis.yaml
kubectl apply -f infra/k8s/kafka.yaml
```

## 4. Deploy Microservices & Frontend
```powershell
kubectl apply -f infra/k8s/product-service.yaml
kubectl apply -f infra/k8s/verification-service.yaml
kubectl apply -f infra/k8s/event-service.yaml
kubectl apply -f infra/k8s/frontend.yaml
```

## 5. Expose Traffic with Free TLS
Update `infra/k8s/ingress-https.yaml` with your domain (or keep `localhost`) and apply:
```powershell
kubectl apply -f infra/k8s/ingress-https.yaml
```
For automatic certificates, install [cert-manager](https://cert-manager.io/) with the ACME HTTP-01 solver (all free) and reference its ClusterIssuer inside the ingress annotations.

## 6. Verify & Monitor
```powershell
kubectl get pods -n supplychain-auth
kubectl get ingress -n supplychain-auth
kubectl logs deployment/product-service -n supplychain-auth
```
Use the bundled Prometheus + Grafana stack (`docker-compose up prometheus grafana`) or install the kube-prometheus stack to visualize cluster metrics.

## 7. Tear Down When Finished
```powershell
k3d cluster delete supplychain-auth
```
This deletes every resource—no lingering cloud bills.

---
**Tip:** k3d can also import the Docker images you built locally via `k3d image import supplychain-auth-product-service`. That keeps the workflow completely offline and cost-free.
