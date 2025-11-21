# Kubernetes Deployment Guide for Supply Chain Authentication System

## Prerequisites
- kubectl installed and configured for your Kubernetes cluster
- Docker images for all services pushed to a registry (e.g., Docker Hub)
- Access to a Kubernetes cluster (local, cloud, or on-premises)

## Steps
1. **Ensure kubectl is configured:**
   ```powershell
   kubectl cluster-info
   ```
2. **Create Namespace:**
   ```powershell
   kubectl apply -f infra/k8s/namespace.yaml
   ```
4. **Create Secrets:**
   - Use `kubectl create secret` for DB credentials, etc.
5. **Deploy Databases and Infrastructure:**
   ```powershell
   kubectl apply -f infra/k8s/postgresql.yaml
   kubectl apply -f infra/k8s/mongodb.yaml
   kubectl apply -f infra/k8s/redis.yaml
   kubectl apply -f infra/k8s/kafka.yaml
   kubectl apply -f infra/k8s/blockchain.yaml
   ```
6. **Deploy Microservices:**
   ```powershell
   kubectl apply -f infra/k8s/product-service.yaml
   kubectl apply -f infra/k8s/verification-service.yaml
   kubectl apply -f infra/k8s/event-service.yaml
   ```
7. **Deploy Ingress:**
   ```powershell
   kubectl apply -f infra/k8s/ingress.yaml
   ```
8. **Monitor and Scale:**
   - Use HPA (Horizontal Pod Autoscaler) and monitoring tools for auto-scaling.

## Notes
- Update image names in YAMLs to match your registry.
- Use Kubernetes secrets for sensitive data.
- For production, configure persistent storage and backups for databases.
