# Kubernetes Manifests for Supply Chain Authentication System

This directory contains Kubernetes YAML files for deploying the backend microservices, blockchain node, Redis, PostgreSQL, MongoDB, and supporting infrastructure on AWS EKS.

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
- Add manifests for each component
- Configure resource requests/limits and auto-scaling
- Add secrets/configmaps for sensitive data
- Document deployment steps
