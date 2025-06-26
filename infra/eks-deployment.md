# AWS EKS Deployment Guide for Supply Chain Authentication System

## Prerequisites
- AWS CLI, kubectl, eksctl installed
- Docker images for all services pushed to a registry (e.g., Docker Hub or ECR)
- AWS account with EKS permissions

## Steps
1. **Create EKS Cluster:**
   ```powershell
   eksctl create cluster --name supplychain-auth --region <your-region> --nodes 3 --node-type t3.large
   ```
2. **Configure kubectl:**
   ```powershell
   aws eks --region <your-region> update-kubeconfig --name supplychain-auth
   ```
3. **Create Namespace:**
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
   - Use HPA and AWS CloudWatch for monitoring and auto-scaling.

## Notes
- Update image names in YAMLs to match your registry.
- Use AWS Secrets Manager or Kubernetes secrets for sensitive data.
- For production, configure persistent storage and backups for databases.
