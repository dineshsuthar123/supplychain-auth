#!/bin/sh

# Supply Chain Authentication Platform - Docker Compose Deployment
# This script deploys the full stack using Docker Compose

set -e

echo "🚀 Deploying Supply Chain Authentication Platform with Docker Compose"
echo "===================================================================="

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

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
    
    if ! command -v docker >/dev/null 2>&1; then
        log_error "Docker is required but not installed. Aborting."
        exit 1
    fi
    
    if ! docker compose version >/dev/null 2>&1; then
        log_error "Docker Compose is required but not installed. Aborting."
        exit 1
    fi
    
    # Check if Docker daemon is running
    if ! docker info >/dev/null 2>&1; then
        log_error "Docker daemon is not running. Please start Docker and try again."
        exit 1
    fi
    
    log_success "Prerequisites check passed"
}

# Create necessary configuration files
create_configs() {
    log_info "Creating configuration files..."
    
    # Create Nginx configuration
    cat > nginx.conf << 'EOF'
events {
    worker_connections 1024;
}

http {
    upstream frontend {
        server frontend:80;
    }
    
    upstream product_api {
        server product-service:8080;
    }
    
    upstream verification_api {
        server verification-service:8080;
    }
    
    upstream event_api {
        server event-service:8080;
    }

    server {
        listen 80;
        
        # Frontend
        location / {
            proxy_pass http://frontend;
            proxy_set_header Host $host;
            proxy_set_header X-Real-IP $remote_addr;
        }
        
        # API routes
        location /api/products {
            proxy_pass http://product_api;
            proxy_set_header Host $host;
            proxy_set_header X-Real-IP $remote_addr;
        }
        
        location /api/verify {
            proxy_pass http://verification_api;
            proxy_set_header Host $host;
            proxy_set_header X-Real-IP $remote_addr;
        }
        
        location /api/events {
            proxy_pass http://event_api;
            proxy_set_header Host $host;
            proxy_set_header X-Real-IP $remote_addr;
        }
        
        # Health check
        location /health {
            return 200 "OK";
            add_header Content-Type text/plain;
        }
    }
}
EOF

    # Create Prometheus configuration
    mkdir -p monitoring
    cat > monitoring/prometheus.yml << 'EOF'
global:
  scrape_interval: 15s
  evaluation_interval: 15s

rule_files:
  - "rules/*.yaml"

scrape_configs:
  - job_name: 'product-service'
    static_configs:
      - targets: ['product-service:8080']
    metrics_path: '/actuator/prometheus'
    
  - job_name: 'verification-service'
    static_configs:
      - targets: ['verification-service:8080']
    metrics_path: '/actuator/prometheus'
    
  - job_name: 'event-service'
    static_configs:
      - targets: ['event-service:8080']
    metrics_path: '/actuator/prometheus'

alerting:
  alertmanagers:
    - static_configs:
        - targets: []
EOF

    log_success "Configuration files created"
}

# Build services
build_services() {
    log_info "Building backend services..."
    
    # Build each service
    for service in product-service verification-service event-service; do
        log_info "Building ${service}..."
        cd backend/${service}
        
        # Check if Maven is available
        if command -v mvn >/dev/null 2>&1; then
            mvn clean package -DskipTests
        else
            log_warning "Maven not found - using Docker to build ${service}"
        fi
        
        cd ../..
    done
    
    log_info "Building frontend..."
    cd frontend
    
    # Create production Dockerfile if it doesn't exist
    if [ ! -f Dockerfile.prod ]; then
        cat > Dockerfile.prod << 'EOF'
# Build stage
FROM node:18-alpine as build
WORKDIR /app
COPY package*.json ./
RUN npm ci --only=production
COPY . .
RUN npm run build

# Production stage
FROM nginx:alpine
COPY --from=build /app/build /usr/share/nginx/html
COPY nginx.conf /etc/nginx/nginx.conf
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
EOF
    fi
    
    cd ..
    
    log_success "Services prepared for building"
}

# Deploy with Docker Compose
deploy_stack() {
    log_info "Deploying the full stack with Docker Compose..."
    
    # Pull base images
    log_info "Pulling base images..."
    docker compose pull
    
    # Build and start services
    log_info "Building and starting services..."
    docker compose up -d --build
    
    # Wait for services to be healthy
    log_info "Waiting for services to be ready..."
    sleep 30
    
    # Check service health
    check_services_health
    
    log_success "Stack deployed successfully"
}

# Check service health
check_services_health() {
    log_info "Checking service health..."
    
    services="mongodb postgresql redis kafka product-service verification-service event-service frontend"
    
    for service in $services; do
        if docker compose ps $service | grep -q "Up"; then
            log_success "$service is running"
        else
            log_warning "$service may not be running properly"
        fi
    done
}

# Run basic tests
run_tests() {
    log_info "Running basic functionality tests..."
    
    # Wait a bit more for services to stabilize
    sleep 10
    
    # Test product service
    log_info "Testing product service..."
    if docker exec supplychain-product-service wget --spider -q http://localhost:8080/actuator/health; then
        log_success "Product service is healthy"
    else
        log_warning "Product service health check failed"
    fi
    
    # Test verification service
    log_info "Testing verification service..."
    if docker exec supplychain-verification-service wget --spider -q http://localhost:8080/actuator/health; then
        log_success "Verification service is healthy"
    else
        log_warning "Verification service health check failed"
    fi
    
    # Test frontend
    log_info "Testing frontend..."
    if docker exec supplychain-frontend wget --spider -q http://localhost:80/health 2>/dev/null; then
        log_success "Frontend is healthy"
    else
        log_warning "Frontend health check failed"
    fi
    
    log_success "Basic tests completed"
}

# Show access information
show_access_info() {
    log_info "Deployment completed! Access information:"
    echo "=================================================="
    
    echo -e "${GREEN}🌐 Application URL:${NC} http://localhost"
    echo -e "${GREEN}🔧 Product Service API:${NC} http://localhost:8081"
    echo -e "${GREEN}🔐 Verification Service API:${NC} http://localhost:8082"
    echo -e "${GREEN}📡 Event Service API:${NC} http://localhost:8083"
    echo -e "${GREEN}📊 Grafana Dashboard:${NC} http://localhost:3001 (admin/admin123)"
    echo -e "${GREEN}📈 Prometheus:${NC} http://localhost:9090"
    echo -e "${GREEN}🗄️  MongoDB:${NC} localhost:27017 (admin/password123)"
    echo -e "${GREEN}🐘 PostgreSQL:${NC} localhost:5432 (postgres/password123)"
    echo -e "${GREEN}📮 Redis:${NC} localhost:6379 (password123)"
    echo -e "${GREEN}📨 Kafka:${NC} localhost:9092"
    
    echo ""
    echo -e "${BLUE}🔍 Useful Commands:${NC}"
    echo "  docker compose ps                    # Check service status"
    echo "  docker compose logs -f <service>    # View service logs"
    echo "  docker compose down                 # Stop all services"
    echo "  docker compose down -v              # Stop and remove volumes"
    echo "  docker compose exec <service> sh    # Access service shell"
    
    echo ""
    echo -e "${YELLOW}📊 Performance Testing:${NC}"
    echo "  cd performance && python locustfile.py"
    
    echo ""
    echo -e "${GREEN}✅ Deployment completed successfully!${NC}"
    echo -e "${BLUE}💡 The application is now running at http://localhost${NC}"
}

# Cleanup function
cleanup() {
    log_info "Cleaning up..."
    docker compose down
    log_success "Cleanup completed"
}

# Main deployment flow
main() {
    case "$1" in
        "down"|"stop")
            log_info "Stopping all services..."
            docker compose down
            log_success "All services stopped"
            ;;
        "clean")
            log_info "Cleaning up all resources..."
            docker compose down -v
            docker system prune -f
            log_success "Cleanup completed"
            ;;
        "logs")
            docker compose logs -f
            ;;
        *)
            check_prerequisites
            create_configs
            build_services
            deploy_stack
            
            if [ "$1" = "--with-tests" ]; then
                run_tests
            fi
            
            show_access_info
            ;;
    esac
}

# Handle script interruption
trap cleanup INT TERM

# Run main function with all arguments
main "$@"
