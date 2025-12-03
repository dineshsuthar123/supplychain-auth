# Kubernetes Manifests for Supply Chain Authentication System

This directory contains Kubernetes YAML files for deploying the backend microservices, blockchain node, Redis, PostgreSQL, MongoDB, and supporting infrastructure on any free Kubernetes distribution (k3d, kind, Minikube, etc.).

## Structure
- `product-service/` - Deployment, Service, HPA
- `verification-service/` - Deployment, Service, HPA
- `event-service/` - Deployment, Service, HPA
- `redis/` - Deployment, Service
- `postgresql/` - StatefulSet, Service, PVC
- `mongodb/` - StatefulSet, Service, PVC
- `kafka/` - StatefulSet, Service, PVC
- `blockchain/` - Deployment, Service (for private Ethereum node or Infura proxy)
- `ingress/` - Ingress resources for API Gateway
- `namespace.yaml` - Namespace definition

## To Do
- Parameterize resource requests/limits per environment
- Wire the manifests to ConfigMaps/Secrets generated from `.env`
- Add kustomize overlays for local/demo deployments
