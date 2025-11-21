#!/bin/bash

# Supply Chain Authentication Platform - Production Deployment Script
# This script deploys all enhanced features including monitoring, security, and blockchain

set -e

echo "🚀 Deploying Supply Chain Authentication Platform"
echo "=================================================="

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Configuration
NAMESPACE="supplychain-auth"
HELM_RELEASE="supplychain"
MONITORING_NAMESPACE="monitoring"

# Helper functions
log_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

log_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

log_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Check prerequisites
check_prerequisites() {
    log_info "Checking prerequisites..."
    
    command -v kubectl >/dev/null 2>&1 || { log_error "kubectl is required but not installed. Aborting."; exit 1; }
    command -v helm >/dev/null 2>&1 || { log_error "helm is required but not installed. Aborting."; exit 1; }
    command -v docker >/dev/null 2>&1 || { log_error "docker is required but not installed. Aborting."; exit 1; }
    
    # Check cluster connectivity
    kubectl cluster-info >/dev/null 2>&1 || { log_error "Cannot connect to Kubernetes cluster. Aborting."; exit 1; }
    
    log_success "Prerequisites check passed"
}

# Deploy security policies
deploy_security() {
    log_info "Deploying security policies..."
    
    # Apply Pod Security Policies
    kubectl apply -f infra/k8s/security-policy.yaml
    
    # Create JWT secret if not exists
    kubectl create secret generic supplychain-jwt-secret \
        --from-literal=jwt-secret="$(openssl rand -base64 32)" \
        --namespace=${NAMESPACE} \
        --dry-run=client -o yaml | kubectl apply -f -
    
    log_success "Security policies deployed"
}

# Deploy monitoring stack
deploy_monitoring() {
    log_info "Deploying monitoring stack..."
    
    # Add Prometheus community helm repo
    helm repo add prometheus-community https://prometheus-community.github.io/helm-charts
    helm repo update
    
    # Create monitoring namespace
    kubectl create namespace ${MONITORING_NAMESPACE} --dry-run=client -o yaml | kubectl apply -f -
    
    # Install Prometheus and Grafana
    helm upgrade --install prometheus prometheus-community/kube-prometheus-stack \
        --namespace=${MONITORING_NAMESPACE} \
        --values=monitoring/prometheus-values.yaml \
        --timeout=600s
    
    # Apply custom alerting rules
    kubectl apply -f monitoring/alerting-rules.yaml -n ${MONITORING_NAMESPACE}
    
    log_success "Monitoring stack deployed"
}

# Build and deploy backend services
deploy_backend() {
    log_info "Building and deploying backend services..."
    
    for service in product-service verification-service event-service; do
        log_info "Building ${service}..."
        
        cd backend/${service}
        
        # Build with Maven
        mvn clean package -DskipTests
        
        # Build Docker image
        docker build -t khammadevi35/${service}:latest .
        
        # Push to registry (if credentials available)
        if docker push khammadevi35/${service}:latest 2>/dev/null; then
            log_success "Pushed ${service} image to registry"
        else
            log_warning "Could not push ${service} image - continuing with local image"
        fi
        
        cd ../..
    done
    
    # Deploy to Kubernetes
    kubectl apply -f infra/k8s/ -n ${NAMESPACE}
    
    # Wait for rollout
    for service in product-service verification-service event-service; do
        kubectl rollout status deployment/${service} -n ${NAMESPACE} --timeout=300s
    done
    
    log_success "Backend services deployed"
}

# Deploy frontend
deploy_frontend() {
    log_info "Building and deploying frontend..."
    
    cd frontend
    
    # Install dependencies and build
    npm install
    npm run build
    
    # Create Docker image
    cat > Dockerfile.prod << EOF
FROM nginx:alpine
COPY build/ /usr/share/nginx/html/
COPY nginx.conf /etc/nginx/nginx.conf
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
EOF
    
    # Create nginx config
    cat > nginx.conf << EOF
events {
    worker_connections 1024;
}

http {
    include       /etc/nginx/mime.types;
    default_type  application/octet-stream;
    
    # Security headers
    add_header X-Frame-Options DENY;
    add_header X-Content-Type-Options nosniff;
    add_header X-XSS-Protection "1; mode=block";
    add_header Referrer-Policy strict-origin-when-cross-origin;
    
    # Gzip compression
    gzip on;
    gzip_types text/plain text/css application/json application/javascript text/xml application/xml;
    
    server {
        listen 80;
        root /usr/share/nginx/html;
        index index.html;
        
        # React Router support
        location / {
            try_files \$uri \$uri/ /index.html;
        }
        
        # Cache static assets
        location /static/ {
            expires 1y;
            add_header Cache-Control "public, immutable";
        }
        
        # Health check
        location /health {
            return 200 "OK";
            add_header Content-Type text/plain;
        }
    }
}
EOF
    
    # Build and deploy
    docker build -f Dockerfile.prod -t khammadevi35/supplychain-frontend:latest .
    
    cd ..
    
    log_success "Frontend deployed"
}

# Deploy blockchain contracts
deploy_blockchain() {
    log_info "Deploying blockchain contracts..."
    
    cd blockchain
    
    # Install dependencies
    npm install
    
    # Compile contracts
    npx hardhat compile
    
    # Run tests
    npx hardhat test --reporter gas
    
    # Deploy to testnet (if configured)
    if [ ! -z "$ETHEREUM_RPC_URL" ]; then
        npx hardhat run scripts/deploy.js --network testnet
        log_success "Blockchain contracts deployed to testnet"
    else
        log_warning "No Ethereum RPC URL configured - skipping blockchain deployment"
    fi
    
    cd ..
}

# Run performance tests
run_performance_tests() {
    log_info "Running performance tests..."
    
    cd performance
    
    # Install Locust if not available
    if ! command -v locust >/dev/null 2>&1; then
        pip install locust
    fi
    
    # Run quick performance test
    locust -f locustfile.py \
        --headless \
        --users 100 \
        --spawn-rate 10 \
        --run-time 60s \
        --csv=quick_test \
        --html=quick_test_report.html
    
    log_success "Performance tests completed - see quick_test_report.html"
    
    cd ..
}

# Display access information
show_access_info() {
    log_info "Deployment completed! Access information:"
    echo "=================================================="
    
    # Get LoadBalancer IP
    LB_IP=$(kubectl get svc -n ${NAMESPACE} -o jsonpath='{.items[?(@.spec.type=="LoadBalancer")].status.loadBalancer.ingress[0].hostname}' 2>/dev/null || echo "Not available")
    
    echo -e "${GREEN}🌐 Application URL:${NC} http://${LB_IP}"
    echo -e "${GREEN}📊 Grafana Dashboard:${NC} kubectl port-forward -n ${MONITORING_NAMESPACE} svc/prometheus-grafana 3000:80"
    echo -e "${GREEN}📈 Prometheus:${NC} kubectl port-forward -n ${MONITORING_NAMESPACE} svc/prometheus-kube-prometheus-prometheus 9090:9090"
    echo -e "${GREEN}📋 API Documentation:${NC} http://${LB_IP}/swagger-ui.html"
    
    echo ""
    echo -e "${BLUE}🔍 Useful Commands:${NC}"
    echo "  kubectl get pods -n ${NAMESPACE}"
    echo "  kubectl logs -f deployment/verification-service -n ${NAMESPACE}"
    echo "  kubectl get hpa -n ${NAMESPACE}"
    echo "  helm status prometheus -n ${MONITORING_NAMESPACE}"
    
    echo ""
    echo -e "${YELLOW}📊 Performance Testing:${NC}"
    echo "  cd performance && ./run_performance_tests.sh"
    
    echo ""
    echo -e "${GREEN}✅ Deployment completed successfully!${NC}"
}

# Main deployment flow
main() {
    check_prerequisites
    
    # Create namespace if not exists
    kubectl create namespace ${NAMESPACE} --dry-run=client -o yaml | kubectl apply -f -
    
    # Deploy components
    deploy_security
    deploy_monitoring
    deploy_backend
    deploy_frontend
    deploy_blockchain
    
    # Optional performance testing
    if [ "$1" = "--with-tests" ]; then
        run_performance_tests
    fi
    
    show_access_info
}

# Run main function with all arguments
main "$@"
