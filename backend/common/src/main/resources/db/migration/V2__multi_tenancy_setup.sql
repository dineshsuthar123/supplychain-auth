-- =====================================================
-- Multi-Tenancy & RBAC Database Setup
-- =====================================================
-- This script sets up row-level security for multi-tenant architecture

-- Enable UUID extension (PostgreSQL specific)
-- Note: Run this manually if your SQL tool doesn't support CREATE EXTENSION:
-- CREATE EXTENSION IF NOT EXISTS "pgcrypto";
-- For now, we'll use gen_random_uuid() which requires pgcrypto

-- =====================================================
-- TENANTS TABLE
-- =====================================================
CREATE TABLE IF NOT EXISTS tenants (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL UNIQUE,
    slug VARCHAR(255) NOT NULL UNIQUE,
    description VARCHAR(500),
    industry VARCHAR(100),
    country VARCHAR(100),
    
    -- Subscription details
    subscription_tier VARCHAR(50) NOT NULL DEFAULT 'FREE',
    subscription_status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    stripe_customer_id VARCHAR(255) UNIQUE,
    stripe_subscription_id VARCHAR(255) UNIQUE,
    
    -- API authentication
    api_key_hash VARCHAR(255) NOT NULL UNIQUE,
    api_key_prefix VARCHAR(255) NOT NULL,
    
    -- Resource limits
    monthly_verification_limit INTEGER NOT NULL DEFAULT 1000,
    monthly_registration_limit INTEGER NOT NULL DEFAULT 100,
    max_products INTEGER NOT NULL DEFAULT 10000,
    max_users INTEGER NOT NULL DEFAULT 5,
    
    -- Feature flags
    analytics_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    api_access_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    multi_chain_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    iot_integration_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    ml_fraud_detection_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    
    -- Usage counters
    current_month_verifications INTEGER NOT NULL DEFAULT 0,
    current_month_registrations INTEGER NOT NULL DEFAULT 0,
    total_products INTEGER NOT NULL DEFAULT 0,
    total_users INTEGER NOT NULL DEFAULT 1,
    
    -- Configuration (JSONB for flexibility)
    settings JSONB DEFAULT '{}',
    blockchain_config JSONB DEFAULT '{}',
    
    -- Timestamps
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    subscription_start_date TIMESTAMP,
    subscription_end_date TIMESTAMP,
    last_billing_date TIMESTAMP,
    next_billing_date TIMESTAMP,
    
    -- Status
    active BOOLEAN NOT NULL DEFAULT TRUE,
    deleted BOOLEAN NOT NULL DEFAULT FALSE
);

-- Indexes for tenants
CREATE INDEX IF NOT EXISTS idx_tenants_api_key ON tenants(api_key_hash);
CREATE INDEX IF NOT EXISTS idx_tenants_stripe_customer ON tenants(stripe_customer_id);
CREATE INDEX IF NOT EXISTS idx_tenants_stripe_subscription ON tenants(stripe_subscription_id);
CREATE INDEX IF NOT EXISTS idx_tenants_slug ON tenants(slug);
CREATE INDEX IF NOT EXISTS idx_tenants_status ON tenants(subscription_status);
CREATE INDEX IF NOT EXISTS idx_tenants_active ON tenants(active, deleted);

-- =====================================================
-- ROLES TABLE
-- =====================================================
CREATE TABLE IF NOT EXISTS roles (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(50) NOT NULL,
    description VARCHAR(255),
    role_type VARCHAR(50) NOT NULL,
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    system_role BOOLEAN NOT NULL DEFAULT FALSE,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    
    CONSTRAINT unique_role_per_tenant UNIQUE(name, tenant_id)
);

CREATE INDEX IF NOT EXISTS idx_roles_tenant ON roles(tenant_id);
CREATE INDEX IF NOT EXISTS idx_roles_type ON roles(role_type);
CREATE INDEX IF NOT EXISTS idx_roles_active ON roles(active);

-- =====================================================
-- ROLE_PERMISSIONS TABLE (For RBAC)
-- =====================================================
CREATE TABLE IF NOT EXISTS role_permissions (
    role_id UUID NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
    permission VARCHAR(100) NOT NULL,
    PRIMARY KEY (role_id, permission)
);

CREATE INDEX IF NOT EXISTS idx_role_permissions_role ON role_permissions(role_id);

-- =====================================================
-- UPDATE EXISTING TABLES FOR MULTI-TENANCY
-- =====================================================

-- Add tenant_id to products table
ALTER TABLE products ADD COLUMN IF NOT EXISTS tenant_id UUID REFERENCES tenants(id);
CREATE INDEX IF NOT EXISTS idx_products_tenant_id ON products(tenant_id);

-- Add tenant_id to users table (if exists)
ALTER TABLE users ADD COLUMN IF NOT EXISTS tenant_id UUID REFERENCES tenants(id);
CREATE INDEX IF NOT EXISTS idx_users_tenant_id ON users(tenant_id);

-- Add tenant_id to verification_logs table (if exists)
ALTER TABLE verification_logs ADD COLUMN IF NOT EXISTS tenant_id UUID REFERENCES tenants(id);
CREATE INDEX IF NOT EXISTS idx_verification_logs_tenant_id ON verification_logs(tenant_id);

-- =====================================================
-- ROW-LEVEL SECURITY (RLS) SETUP
-- =====================================================

-- Function to get current tenant ID from session
CREATE OR REPLACE FUNCTION current_tenant_id()
RETURNS UUID AS $$
BEGIN
    -- Get tenant ID from application-set session variable
    RETURN current_setting('app.current_tenant_id', TRUE)::UUID;
EXCEPTION
    WHEN OTHERS THEN
        RETURN NULL;
END;
$$ LANGUAGE plpgsql STABLE;

-- Enable RLS on products
ALTER TABLE products ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation_policy ON products
    USING (tenant_id = current_tenant_id() OR current_tenant_id() IS NULL)
    WITH CHECK (tenant_id = current_tenant_id());

-- Enable RLS on users (if exists)
DO $$
BEGIN
    IF EXISTS (SELECT FROM pg_tables WHERE tablename = 'users') THEN
        EXECUTE 'ALTER TABLE users ENABLE ROW LEVEL SECURITY';
        EXECUTE 'CREATE POLICY tenant_isolation_policy ON users
                 USING (tenant_id = current_tenant_id() OR current_tenant_id() IS NULL)';
    END IF;
END $$;

-- Enable RLS on verification_logs (if exists)
DO $$
BEGIN
    IF EXISTS (SELECT FROM pg_tables WHERE tablename = 'verification_logs') THEN
        EXECUTE 'ALTER TABLE verification_logs ENABLE ROW LEVEL SECURITY';
        EXECUTE 'CREATE POLICY tenant_isolation_policy ON verification_logs
                 USING (tenant_id = current_tenant_id() OR current_tenant_id() IS NULL)';
    END IF;
END $$;

-- =====================================================
-- AUDIT LOG TABLE
-- =====================================================
CREATE TABLE IF NOT EXISTS audit_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID REFERENCES tenants(id),
    user_id VARCHAR(255),
    action VARCHAR(100) NOT NULL,
    entity_type VARCHAR(100),
    entity_id VARCHAR(255),
    details JSONB,
    ip_address INET,
    user_agent TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_audit_logs_tenant ON audit_logs(tenant_id);
CREATE INDEX IF NOT EXISTS idx_audit_logs_user ON audit_logs(user_id);
CREATE INDEX IF NOT EXISTS idx_audit_logs_action ON audit_logs(action);
CREATE INDEX IF NOT EXISTS idx_audit_logs_created_at ON audit_logs(created_at);

-- =====================================================
-- USAGE_METRICS TABLE (For billing/analytics)
-- =====================================================
CREATE TABLE IF NOT EXISTS usage_metrics (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    metric_date DATE NOT NULL,
    verifications_count INTEGER NOT NULL DEFAULT 0,
    registrations_count INTEGER NOT NULL DEFAULT 0,
    api_calls_count INTEGER NOT NULL DEFAULT 0,
    storage_bytes BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    
    CONSTRAINT unique_metrics_per_day UNIQUE(tenant_id, metric_date)
);

CREATE INDEX IF NOT EXISTS idx_usage_metrics_tenant ON usage_metrics(tenant_id);
CREATE INDEX IF NOT EXISTS idx_usage_metrics_date ON usage_metrics(metric_date);

-- =====================================================
-- STRIPE_EVENTS TABLE (Webhook processing)
-- =====================================================
CREATE TABLE IF NOT EXISTS stripe_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    stripe_event_id VARCHAR(255) UNIQUE NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    tenant_id UUID REFERENCES tenants(id),
    payload JSONB NOT NULL,
    processed BOOLEAN NOT NULL DEFAULT FALSE,
    processed_at TIMESTAMP,
    error_message TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_stripe_events_tenant ON stripe_events(tenant_id);
CREATE INDEX IF NOT EXISTS idx_stripe_events_type ON stripe_events(event_type);
CREATE INDEX IF NOT EXISTS idx_stripe_events_processed ON stripe_events(processed);

-- =====================================================
-- API_KEYS TABLE (Multiple keys per tenant)
-- =====================================================
CREATE TABLE IF NOT EXISTS api_keys (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    name VARCHAR(255) NOT NULL,
    key_hash VARCHAR(255) NOT NULL UNIQUE,
    key_prefix VARCHAR(50) NOT NULL,
    permissions JSONB DEFAULT '[]',
    last_used_at TIMESTAMP,
    expires_at TIMESTAMP,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    created_by VARCHAR(255)
);

CREATE INDEX IF NOT EXISTS idx_api_keys_tenant ON api_keys(tenant_id);
CREATE INDEX IF NOT EXISTS idx_api_keys_hash ON api_keys(key_hash);
CREATE INDEX IF NOT EXISTS idx_api_keys_active ON api_keys(active);

-- =====================================================
-- HELPER FUNCTIONS
-- =====================================================

-- Function to increment tenant verification counter
CREATE OR REPLACE FUNCTION increment_tenant_verifications(p_tenant_id UUID)
RETURNS void AS $$
BEGIN
    UPDATE tenants
    SET current_month_verifications = current_month_verifications + 1,
        updated_at = NOW()
    WHERE id = p_tenant_id;
END;
$$ LANGUAGE plpgsql;

-- Function to increment tenant registration counter
CREATE OR REPLACE FUNCTION increment_tenant_registrations(p_tenant_id UUID)
RETURNS void AS $$
BEGIN
    UPDATE tenants
    SET current_month_registrations = current_month_registrations + 1,
        total_products = total_products + 1,
        updated_at = NOW()
    WHERE id = p_tenant_id;
END;
$$ LANGUAGE plpgsql;

-- Function to reset monthly counters (run via cron)
CREATE OR REPLACE FUNCTION reset_monthly_tenant_counters()
RETURNS void AS $$
BEGIN
    UPDATE tenants
    SET current_month_verifications = 0,
        current_month_registrations = 0,
        last_billing_date = CURRENT_DATE,
        next_billing_date = CURRENT_DATE + INTERVAL '1 month',
        updated_at = NOW()
    WHERE active = TRUE;
END;
$$ LANGUAGE plpgsql;

-- Function to check if tenant can perform action
CREATE OR REPLACE FUNCTION check_tenant_limit(
    p_tenant_id UUID,
    p_action VARCHAR(50)
)
RETURNS BOOLEAN AS $$
DECLARE
    v_tenant tenants%ROWTYPE;
BEGIN
    SELECT * INTO v_tenant FROM tenants WHERE id = p_tenant_id;
    
    IF NOT FOUND THEN
        RETURN FALSE;
    END IF;
    
    IF v_tenant.subscription_status != 'ACTIVE' THEN
        RETURN FALSE;
    END IF;
    
    IF p_action = 'VERIFICATION' THEN
        RETURN v_tenant.current_month_verifications < v_tenant.monthly_verification_limit;
    ELSIF p_action = 'REGISTRATION' THEN
        RETURN v_tenant.current_month_registrations < v_tenant.monthly_registration_limit;
    END IF;
    
    RETURN TRUE;
END;
$$ LANGUAGE plpgsql;

-- =====================================================
-- SEED DATA FOR DEMO TENANT
-- =====================================================

-- Insert demo tenant (for development/testing)
INSERT INTO tenants (
    name, slug, description, industry, country,
    subscription_tier, subscription_status,
    api_key_hash, api_key_prefix,
    monthly_verification_limit, monthly_registration_limit,
    max_products, max_users,
    analytics_enabled, multi_chain_enabled
) VALUES (
    'Demo Pharmaceuticals Inc',
    'demo-pharma',
    'Demo tenant for testing and development',
    'Pharmaceuticals',
    'United States',
    'PROFESSIONAL',
    'ACTIVE',
    encode(sha256('sk_test_demo_key_12345'::bytea), 'base64'),
    'sk_test_',
    100000,
    10000,
    50000,
    25,
    TRUE,
    TRUE
) ON CONFLICT (slug) DO NOTHING;

-- Get demo tenant ID
DO $$
DECLARE
    v_tenant_id UUID;
BEGIN
    SELECT id INTO v_tenant_id FROM tenants WHERE slug = 'demo-pharma';
    
    -- Insert default roles for demo tenant
    INSERT INTO roles (name, description, role_type, tenant_id, system_role) VALUES
        ('Tenant Admin', 'Full administrative access', 'TENANT_ADMIN', v_tenant_id, TRUE),
        ('Manufacturer', 'Can register and manage products', 'MANUFACTURER', v_tenant_id, TRUE),
        ('Quality Auditor', 'Read-only access for auditing', 'QUALITY_AUDITOR', v_tenant_id, TRUE),
        ('Consumer', 'Can verify products', 'CONSUMER', v_tenant_id, TRUE)
    ON CONFLICT (name, tenant_id) DO NOTHING;
END $$;

-- =====================================================
-- VIEWS FOR REPORTING
-- =====================================================

-- View for tenant metrics
CREATE OR REPLACE VIEW tenant_metrics_summary AS
SELECT 
    t.id as tenant_id,
    t.name as tenant_name,
    t.subscription_tier,
    t.subscription_status,
    t.current_month_verifications,
    t.monthly_verification_limit,
    ROUND((t.current_month_verifications::NUMERIC / t.monthly_verification_limit) * 100, 2) as verification_usage_pct,
    t.total_products,
    t.max_products,
    t.total_users,
    t.max_users,
    t.created_at,
    t.next_billing_date
FROM tenants t
WHERE t.active = TRUE AND t.deleted = FALSE;

-- View for audit trail
CREATE OR REPLACE VIEW recent_audit_logs AS
SELECT 
    al.id,
    t.name as tenant_name,
    al.user_id,
    al.action,
    al.entity_type,
    al.entity_id,
    al.ip_address,
    al.created_at
FROM audit_logs al
LEFT JOIN tenants t ON al.tenant_id = t.id
ORDER BY al.created_at DESC
LIMIT 1000;

-- =====================================================
-- PERMISSIONS
-- =====================================================

-- Grant permissions to application user (adjust username as needed)
-- GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO your_app_user;
-- GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA public TO your_app_user;

COMMENT ON TABLE tenants IS 'Multi-tenant isolation - each tenant represents a manufacturer/brand';
COMMENT ON TABLE roles IS 'RBAC roles for fine-grained access control';
COMMENT ON TABLE role_permissions IS 'Permissions assigned to each role';
COMMENT ON TABLE audit_logs IS 'Comprehensive audit trail for compliance';
COMMENT ON TABLE usage_metrics IS 'Daily usage metrics for billing and analytics';
COMMENT ON TABLE stripe_events IS 'Stripe webhook events for payment processing';
COMMENT ON TABLE api_keys IS 'Multiple API keys per tenant with granular permissions';

-- =====================================================
-- COMPLETION
-- =====================================================
SELECT 'Multi-tenancy database setup completed successfully!' as status;
