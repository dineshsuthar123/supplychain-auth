# Infrastructure (Kubernetes on Free Tooling)

This folder contains everything required to run the platform on zero-cost infrastructure:

- `k8s/` – Kubernetes manifests that work on k3d, kind, Minikube, or any CNCF-compliant cluster
- `k3d-deployment.md` – step-by-step guide for creating a local k3d cluster instead of paid cloud Kubernetes
- `kubernetes-deployment.md` – generic instructions that apply to any cluster

All artifacts have been scrubbed of proprietary cloud dependencies, so you can run the full stack locally without incurring hosting charges.
