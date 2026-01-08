# 🎯 PHASE 1 EXECUTION PLAN (Months 0-3)

> **Goal**: Launch revenue-ready SaaS MVP with paying customers

**Target Metrics:**
- ✅ 5-10 paying customers
- ✅ $500-$2,000 MRR
- ✅ Multi-tenant architecture fully operational
- ✅ Production blockchain deployed (mainnet)
- ✅ Polished UI/UX
- ✅ Security audit completed

---

## 📅 WEEK-BY-WEEK EXECUTION PLAN

### 🔷 MONTH 1: Foundation (Multi-Tenancy + RBAC)

#### **Week 1: Database Multi-Tenancy Setup**

**Monday (8 hours): Design & Planning**
- [7:00-9:00] Review current database schema
- [9:00-12:00] Design multi-tenant architecture
  - Decision: Use **Row-Level Security (RLS)** approach (shared DB, tenant_id column)
  - Alternative considered: Schema-per-tenant (too complex), DB-per-tenant (cost prohibitive)
- [13:00-16:00] Create migration plan document
- [16:00-17:00] Set up development branch: `git checkout -b feature/multi-tenancy`

**Tuesday (8 hours): Schema Migration**
- [7:00-10:00] Add `tenants` table:
  ```sql
  CREATE TABLE tenants (
      id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
      name VARCHAR(255) NOT NULL UNIQUE,
      subscription_tier VARCHAR(50) DEFAULT 'FREE',
      subscription_status VARCHAR(50) DEFAULT 'ACTIVE',
      stripe_customer_id VARCHAR(255),
      stripe_subscription_id VARCHAR(255),
      api_key_hash VARCHAR(255) UNIQUE,
      settings JSONB DEFAULT '{}',
      created_at TIMESTAMP DEFAULT NOW(),
      updated_at TIMESTAMP DEFAULT NOW()
  );
  
  CREATE INDEX idx_tenants_api_key ON tenants(api_key_hash);
  ```

- [10:00-13:00] Add `tenant_id` to existing tables:
  ```sql
  ALTER TABLE products ADD COLUMN tenant_id UUID REFERENCES tenants(id);
  ALTER TABLE users ADD COLUMN tenant_id UUID REFERENCES tenants(id);
  ALTER TABLE verification_logs ADD COLUMN tenant_id UUID REFERENCES tenants(id);
  
  -- Add indexes for performance
  CREATE INDEX idx_products_tenant_id ON products(tenant_id);
  CREATE INDEX idx_users_tenant_id ON users(tenant_id);
  ```

- [14:00-17:00] Implement Row-Level Security (RLS):
  ```sql
  -- Enable RLS
  ALTER TABLE products ENABLE ROW LEVEL SECURITY;
  ALTER TABLE users ENABLE ROW LEVEL SECURITY;
  ALTER TABLE verification_logs ENABLE ROW LEVEL SECURITY;
  
  -- Create function to get current tenant ID from session
  CREATE OR REPLACE FUNCTION current_tenant_id()
  RETURNS UUID AS $$
  BEGIN
      RETURN current_setting('app.current_tenant_id', TRUE)::UUID;
  EXCEPTION
      WHEN OTHERS THEN
          RETURN NULL;
  END;
  $$ LANGUAGE plpgsql STABLE;
  
  -- Create RLS policy for products
  CREATE POLICY tenant_isolation_policy ON products
      USING (tenant_id = current_tenant_id())
      WITH CHECK (tenant_id = current_tenant_id());
  
  -- Repeat for other tables
  CREATE POLICY tenant_isolation_policy ON users
      USING (tenant_id = current_tenant_id());
  
  CREATE POLICY tenant_isolation_policy ON verification_logs
      USING (tenant_id = current_tenant_id());
  ```

**Wednesday (8 hours): Backend Integration**
- [7:00-10:00] Create `TenantContext` service:
  ```java
  @Service
  public class TenantContext {
      private static final ThreadLocal<UUID> currentTenant = new ThreadLocal<>();
      
      public static void setCurrentTenant(UUID tenantId) {
          currentTenant.set(tenantId);
      }
      
      public static UUID getCurrentTenant() {
          return currentTenant.get();
      }
      
      public static void clear() {
          currentTenant.remove();
      }
  }
  ```

- [10:00-13:00] Create tenant extraction filter:
  ```java
  @Component
  @Order(1)
  public class TenantFilter extends OncePerRequestFilter {
      
      @Autowired
      private JwtTokenProvider tokenProvider;
      
      @Override
      protected void doFilterInternal(HttpServletRequest request, 
                                      HttpServletResponse response, 
                                      FilterChain filterChain) throws ServletException, IOException {
          try {
              String token = getJwtFromRequest(request);
              if (StringUtils.hasText(token) && tokenProvider.validateToken(token)) {
                  UUID tenantId = tokenProvider.getTenantIdFromToken(token);
                  TenantContext.setCurrentTenant(tenantId);
                  
                  // Set PostgreSQL session variable for RLS
                  jdbcTemplate.execute("SET app.current_tenant_id = '" + tenantId + "'");
              }
              
              filterChain.doFilter(request, response);
          } finally {
              TenantContext.clear();
          }
      }
  }
  ```

- [14:00-17:00] Update JWT token to include `tenant_id`:
  ```java
  public String generateToken(Authentication authentication, UUID tenantId) {
      UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();
      
      Date now = new Date();
      Date expiryDate = new Date(now.getTime() + jwtExpirationInMs);
      
      return Jwts.builder()
              .setSubject(Long.toString(userPrincipal.getId()))
              .claim("tenant_id", tenantId.toString())
              .setIssuedAt(new Date())
              .setExpiration(expiryDate)
              .signWith(SignatureAlgorithm.HS512, jwtSecret)
              .compact();
  }
  ```

**Thursday (8 hours): Tenant Management API**
- [7:00-10:00] Create `TenantController`:
  ```java
  @RestController
  @RequestMapping("/api/tenants")
  public class TenantController {
      
      @Autowired
      private TenantService tenantService;
      
      @PostMapping("/register")
      public ResponseEntity<?> registerTenant(@RequestBody TenantRegistrationRequest request) {
          Tenant tenant = tenantService.createTenant(request);
          return ResponseEntity.ok(new TenantResponse(tenant));
      }
      
      @GetMapping("/me")
      public ResponseEntity<?> getCurrentTenant() {
          UUID tenantId = TenantContext.getCurrentTenant();
          Tenant tenant = tenantService.getTenantById(tenantId);
          return ResponseEntity.ok(new TenantResponse(tenant));
      }
      
      @PutMapping("/me")
      public ResponseEntity<?> updateTenant(@RequestBody TenantUpdateRequest request) {
          UUID tenantId = TenantContext.getCurrentTenant();
          Tenant updated = tenantService.updateTenant(tenantId, request);
          return ResponseEntity.ok(new TenantResponse(updated));
      }
  }
  ```

- [10:00-13:00] Implement `TenantService`:
  ```java
  @Service
  public class TenantService {
      
      @Autowired
      private TenantRepository tenantRepository;
      
      @Transactional
      public Tenant createTenant(TenantRegistrationRequest request) {
          // Validate tenant name uniqueness
          if (tenantRepository.existsByName(request.getName())) {
              throw new BadRequestException("Tenant name already exists");
          }
          
          // Generate API key
          String apiKey = "sk_live_" + UUID.randomUUID().toString().replace("-", "");
          String apiKeyHash = BCrypt.hashpw(apiKey, BCrypt.gensalt());
          
          Tenant tenant = new Tenant();
          tenant.setName(request.getName());
          tenant.setSubscriptionTier("FREE");
          tenant.setApiKeyHash(apiKeyHash);
          
          tenant = tenantRepository.save(tenant);
          
          // Store plain API key temporarily for response (show once)
          tenant.setApiKey(apiKey);
          
          return tenant;
      }
      
      public Tenant getTenantById(UUID id) {
          return tenantRepository.findById(id)
                  .orElseThrow(() -> new ResourceNotFoundException("Tenant", "id", id));
      }
  }
  ```

- [14:00-17:00] Write integration tests:
  ```java
  @SpringBootTest
  @AutoConfigureMockMvc
  class TenantControllerTest {
      
      @Test
      void shouldCreateTenant() throws Exception {
          String request = "{\"name\":\"AcmeCorp\",\"email\":\"admin@acme.com\"}";
          
          mockMvc.perform(post("/api/tenants/register")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(request))
                  .andExpect(status().isOk())
                  .andExpect(jsonPath("$.name").value("AcmeCorp"))
                  .andExpect(jsonPath("$.apiKey").exists());
      }
      
      @Test
      void shouldIsolateTenantData() {
          // Create two tenants
          Tenant tenant1 = createTenant("Tenant1");
          Tenant tenant2 = createTenant("Tenant2");
          
          // Create products for each tenant
          TenantContext.setCurrentTenant(tenant1.getId());
          Product product1 = productService.createProduct(...);
          
          TenantContext.setCurrentTenant(tenant2.getId());
          Product product2 = productService.createProduct(...);
          
          // Verify isolation
          TenantContext.setCurrentTenant(tenant1.getId());
          List<Product> tenant1Products = productService.getAllProducts();
          assertEquals(1, tenant1Products.size());
          assertEquals(product1.getId(), tenant1Products.get(0).getId());
          
          TenantContext.setCurrentTenant(tenant2.getId());
          List<Product> tenant2Products = productService.getAllProducts();
          assertEquals(1, tenant2Products.size());
          assertEquals(product2.getId(), tenant2Products.get(0).getId());
      }
  }
  ```

**Friday (8 hours): Testing & Documentation**
- [7:00-10:00] Manual testing of tenant isolation
- [10:00-13:00] Performance testing (ensure RLS doesn't slow queries)
- [14:00-16:00] Write API documentation (Swagger)
- [16:00-17:00] Code review, merge PR

**Weekend**: Rest + light reading on RBAC patterns

---

#### **Week 2: Role-Based Access Control (RBAC)**

**Monday (8 hours): RBAC Schema Design**
- [7:00-10:00] Define roles and permissions:
  ```
  ROLES:
  - SUPER_ADMIN (platform admin, can see all tenants)
  - TENANT_ADMIN (tenant owner, full control within tenant)
  - BRAND_USER (can register products, view analytics)
  - AUDITOR (read-only access to all data)
  - CONSUMER (can only verify products)
  
  PERMISSIONS:
  - products:create, products:read, products:update, products:delete
  - users:create, users:read, users:update, users:delete
  - analytics:read
  - settings:update
  - billing:manage
  ```

- [10:00-13:00] Create RBAC tables:
  ```sql
  CREATE TABLE roles (
      id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
      name VARCHAR(50) NOT NULL UNIQUE,
      description TEXT,
      created_at TIMESTAMP DEFAULT NOW()
  );
  
  CREATE TABLE permissions (
      id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
      name VARCHAR(100) NOT NULL UNIQUE,
      resource VARCHAR(50) NOT NULL,
      action VARCHAR(50) NOT NULL,
      created_at TIMESTAMP DEFAULT NOW()
  );
  
  CREATE TABLE role_permissions (
      role_id UUID REFERENCES roles(id) ON DELETE CASCADE,
      permission_id UUID REFERENCES permissions(id) ON DELETE CASCADE,
      PRIMARY KEY (role_id, permission_id)
  );
  
  CREATE TABLE user_roles (
      user_id UUID REFERENCES users(id) ON DELETE CASCADE,
      role_id UUID REFERENCES roles(id) ON DELETE CASCADE,
      tenant_id UUID REFERENCES tenants(id) ON DELETE CASCADE,
      PRIMARY KEY (user_id, role_id, tenant_id)
  );
  ```

- [14:00-17:00] Seed default roles:
  ```sql
  INSERT INTO roles (name, description) VALUES
      ('SUPER_ADMIN', 'Platform administrator with full access'),
      ('TENANT_ADMIN', 'Tenant administrator with full tenant access'),
      ('BRAND_USER', 'Standard user who can manage products'),
      ('AUDITOR', 'Read-only access for compliance/audit'),
      ('CONSUMER', 'End-user who can verify products');
  
  -- Seed permissions
  INSERT INTO permissions (name, resource, action) VALUES
      ('products:create', 'products', 'create'),
      ('products:read', 'products', 'read'),
      ('products:update', 'products', 'update'),
      ('products:delete', 'products', 'delete'),
      ('users:create', 'users', 'create'),
      ('users:read', 'users', 'read'),
      ('analytics:read', 'analytics', 'read'),
      ('billing:manage', 'billing', 'manage');
  
  -- Assign permissions to roles
  INSERT INTO role_permissions (role_id, permission_id)
  SELECT r.id, p.id
  FROM roles r, permissions p
  WHERE r.name = 'TENANT_ADMIN'; -- Admin gets all permissions
  
  INSERT INTO role_permissions (role_id, permission_id)
  SELECT r.id, p.id
  FROM roles r, permissions p
  WHERE r.name = 'BRAND_USER' AND p.name IN ('products:create', 'products:read', 'products:update');
  ```

**Tuesday (8 hours): RBAC Backend Implementation**
- [7:00-10:00] Create authorization service:
  ```java
  @Service
  public class AuthorizationService {
      
      @Autowired
      private UserRoleRepository userRoleRepository;
      
      public boolean hasPermission(UUID userId, String permission) {
          UUID tenantId = TenantContext.getCurrentTenant();
          
          return userRoleRepository.findByUserIdAndTenantId(userId, tenantId)
                  .stream()
                  .flatMap(userRole -> userRole.getRole().getPermissions().stream())
                  .anyMatch(p -> p.getName().equals(permission));
      }
      
      public boolean hasRole(UUID userId, String roleName) {
          UUID tenantId = TenantContext.getCurrentTenant();
          
          return userRoleRepository.findByUserIdAndTenantId(userId, tenantId)
                  .stream()
                  .anyMatch(userRole -> userRole.getRole().getName().equals(roleName));
      }
      
      public Set<String> getUserPermissions(UUID userId) {
          UUID tenantId = TenantContext.getCurrentTenant();
          
          return userRoleRepository.findByUserIdAndTenantId(userId, tenantId)
                  .stream()
                  .flatMap(userRole -> userRole.getRole().getPermissions().stream())
                  .map(Permission::getName)
                  .collect(Collectors.toSet());
      }
  }
  ```

- [10:00-13:00] Create authorization annotations:
  ```java
  @Target({ElementType.METHOD, ElementType.TYPE})
  @Retention(RetentionPolicy.RUNTIME)
  public @interface RequiresPermission {
      String value();
  }
  
  @Aspect
  @Component
  public class PermissionAspect {
      
      @Autowired
      private AuthorizationService authorizationService;
      
      @Around("@annotation(requiresPermission)")
      public Object checkPermission(ProceedingJoinPoint joinPoint, RequiresPermission requiresPermission) throws Throwable {
          Authentication auth = SecurityContextHolder.getContext().getAuthentication();
          UUID userId = ((UserPrincipal) auth.getPrincipal()).getId();
          
          if (!authorizationService.hasPermission(userId, requiresPermission.value())) {
              throw new ForbiddenException("Insufficient permissions");
          }
          
          return joinPoint.proceed();
      }
  }
  ```

- [14:00-17:00] Apply authorization to controllers:
  ```java
  @RestController
  @RequestMapping("/api/products")
  public class ProductController {
      
      @PostMapping
      @RequiresPermission("products:create")
      public ResponseEntity<?> createProduct(@RequestBody ProductRequest request) {
          // Only users with 'products:create' permission can access
          Product product = productService.createProduct(request);
          return ResponseEntity.ok(product);
      }
      
      @GetMapping
      @RequiresPermission("products:read")
      public ResponseEntity<?> getAllProducts() {
          List<Product> products = productService.getAllProducts();
          return ResponseEntity.ok(products);
      }
      
      @DeleteMapping("/{id}")
      @RequiresPermission("products:delete")
      public ResponseEntity<?> deleteProduct(@PathVariable UUID id) {
          productService.deleteProduct(id);
          return ResponseEntity.ok().build();
      }
  }
  ```

**Wednesday-Thursday (16 hours): Role Management UI + Testing**
- Build admin UI for role assignment
- Write comprehensive tests for all permission combinations
- Performance testing (ensure authorization checks are fast)

**Friday (8 hours): Review & Merge**
- Code review
- Security audit of RBAC logic
- Merge to main

---

### 🔷 MONTH 2: Payments & Subscription

#### **Week 5-6: Stripe Integration (16 hours)**

**Day 1-2: Stripe Setup**
- [ ] Create Stripe account (test + live mode)
- [ ] Install Stripe SDK: `implementation 'com.stripe:stripe-java:24.0.0'`
- [ ] Configure Stripe keys in application.properties:
  ```properties
  stripe.api.key=sk_test_xxxxx
  stripe.webhook.secret=whsec_xxxxx
  ```
- [ ] Create subscription products in Stripe Dashboard:
  - Starter: $49/month
  - Professional: $199/month
  - Enterprise: $999/month

**Day 3-4: Checkout Flow Implementation**
- [ ] Create `SubscriptionController`:
  ```java
  @RestController
  @RequestMapping("/api/subscriptions")
  public class SubscriptionController {
      
      @PostMapping("/create-checkout-session")
      public ResponseEntity<?> createCheckoutSession(@RequestBody CheckoutRequest request) {
          Stripe.apiKey = stripeApiKey;
          
          SessionCreateParams params = SessionCreateParams.builder()
              .setMode(SessionCreateParams.Mode.SUBSCRIPTION)
              .setSuccessUrl("https://yourapp.com/success?session_id={CHECKOUT_SESSION_ID}")
              .setCancelUrl("https://yourapp.com/cancel")
              .addLineItem(SessionCreateParams.LineItem.builder()
                  .setPrice(request.getPriceId()) // price_1xxxxx from Stripe
                  .setQuantity(1L)
                  .build())
              .setClientReferenceId(TenantContext.getCurrentTenant().toString())
              .build();
          
          Session session = Session.create(params);
          
          return ResponseEntity.ok(Map.of("sessionId", session.getId()));
      }
  }
  ```

**Day 5-6: Webhook Handling**
- [ ] Implement webhook endpoint:
  ```java
  @PostMapping("/api/webhooks/stripe")
  public ResponseEntity<?> handleStripeWebhook(@RequestBody String payload, 
                                               @RequestHeader("Stripe-Signature") String sigHeader) {
      Event event;
      
      try {
          event = Webhook.constructEvent(payload, sigHeader, webhookSecret);
      } catch (SignatureVerificationException e) {
          return ResponseEntity.status(400).body("Invalid signature");
      }
      
      switch (event.getType()) {
          case "checkout.session.completed":
              handleCheckoutCompleted((Session) event.getData().getObject());
              break;
          case "customer.subscription.updated":
              handleSubscriptionUpdated((Subscription) event.getData().getObject());
              break;
          case "customer.subscription.deleted":
              handleSubscriptionDeleted((Subscription) event.getData().getObject());
              break;
          case "invoice.payment_succeeded":
              handlePaymentSucceeded((Invoice) event.getData().getObject());
              break;
          case "invoice.payment_failed":
              handlePaymentFailed((Invoice) event.getData().getObject());
              break;
      }
      
      return ResponseEntity.ok().build();
  }
  
  private void handleCheckoutCompleted(Session session) {
      UUID tenantId = UUID.fromString(session.getClientReferenceId());
      Subscription subscription = session.getSubscription();
      
      tenantService.updateSubscription(tenantId, 
          subscription.getId(), 
          "ACTIVE", 
          subscription.getCurrentPeriodEnd());
  }
  ```

**Day 7-8: Usage Metering**
- [ ] Implement API usage tracking:
  ```java
  @Aspect
  @Component
  public class UsageTrackingAspect {
      
      @Autowired
      private UsageService usageService;
      
      @After("@annotation(MeteredEndpoint)")
      public void trackUsage(JoinPoint joinPoint) {
          UUID tenantId = TenantContext.getCurrentTenant();
          String endpoint = joinPoint.getSignature().getName();
          
          usageService.incrementUsage(tenantId, endpoint);
          
          // Check if over limit
          if (usageService.isOverLimit(tenantId)) {
              // Send warning email or block request
          }
      }
  }
  ```

**Day 9-10: Crypto Payments (Stripe USDC)**
- [ ] Enable Stripe Crypto in dashboard
- [ ] Update checkout to accept crypto:
  ```java
  SessionCreateParams params = SessionCreateParams.builder()
      .setMode(SessionCreateParams.Mode.SUBSCRIPTION)
      .addPaymentMethodType(SessionCreateParams.PaymentMethodType.CARD)
      .addPaymentMethodType(SessionCreateParams.PaymentMethodType.US_BANK_ACCOUNT)
      .addPaymentMethodType(SessionCreateParams.PaymentMethodType.LINK)
      // Crypto enabled automatically when configured in Stripe
      // ...rest of params
      .build();
  ```
- [ ] Test crypto payment flow (testnet USDC)

---

#### **Week 7-8: UI/UX Polish (16 hours)**

**Day 1-2: Hire Designer / Get Template**
- [ ] Option A: Hire on Upwork ($500-$1500 for full design)
  - Post job: "Need SaaS dashboard design (Figma)"
  - Review portfolios (look for SaaS experience)
  - Award to designer with best fit
- [ ] Option B: Buy premium template ($30-$100)
  - Sources: Creative Tim, ThemeForest, MUI Store
  - Choose React-based template with dashboard

**Day 3-6: Implement Designs**
- [ ] Landing page:
  - Hero section with demo video
  - Features section (3-4 key features with icons)
  - Pricing table (3 tiers)
  - Testimonials (use placeholder text for now)
  - CTA button: "Start Free Trial"
- [ ] Dashboard:
  - Overview cards (total products, verifications, revenue)
  - Charts (Chart.js or Recharts):
    - Verifications over time (line chart)
    - Products by category (pie chart)
  - Recent activity feed
  - Quick actions (register product, view analytics)
- [ ] Product pages:
  - Product list (table with search, filters)
  - Product registration form (multi-step wizard)
  - Product detail view (history, blockchain link)
- [ ] Settings:
  - Account settings (company name, logo)
  - Billing (current plan, payment method, invoices)
  - Team management (invite users, assign roles)
  - API keys (generate, revoke)

**Day 7-8: Responsive Design + Testing**
- [ ] Test on mobile (iPhone, Android)
- [ ] Test on tablet (iPad)
- [ ] Test on desktop (various screen sizes)
- [ ] Fix layout issues
- [ ] Add loading states, error messages
- [ ] Conduct usability testing with 5 users

---

### 🔷 MONTH 3: Production Deployment & Launch

#### **Week 9-10: Production Blockchain (16 hours)**

**Day 1-2: Choose Production Network**
- [ ] Decision matrix:
  
  | Chain | Pros | Cons | Cost/Mint | Speed |
  |-------|------|------|-----------|-------|
  | **Ethereum** | Most secure, highest trust | High gas ($5-$50) | $5-$50 | 15s |
  | **Polygon** | Low gas, fast, growing | Less secure than ETH | $0.01 | 2s |
  | **Arbitrum** | Ethereum security, lower gas | Newer, less adoption | $0.10 | 1s |
  
  **Recommendation: Start with Polygon, add Ethereum for enterprise**

- [ ] Set up production wallet:
  - Create new wallet (MetaMask or hardware wallet)
  - **CRITICAL**: Backup seed phrase securely (hardware security key, safe deposit box)
  - Fund with MATIC tokens (need ~1000 MATIC = $500 for first 50K mints)
  - Never commit private key to git!

**Day 3-4: Deploy Smart Contracts**
- [ ] Configure Hardhat for Polygon mainnet:
  ```javascript
  // hardhat.config.js
  module.exports = {
    networks: {
      polygon: {
        url: "https://polygon-rpc.com",
        accounts: [process.env.DEPLOYER_PRIVATE_KEY],
        gasPrice: 50000000000, // 50 gwei
      }
    },
    etherscan: {
      apiKey: process.env.POLYGONSCAN_API_KEY
    }
  };
  ```

- [ ] Deploy contracts:
  ```bash
  # Test deployment first on Mumbai testnet
  npx hardhat run scripts/deploy.js --network mumbai
  
  # If successful, deploy to mainnet
  npx hardhat run scripts/deploy.js --network polygon
  ```

- [ ] Verify on Polygonscan:
  ```bash
  npx hardhat verify --network polygon <CONTRACT_ADDRESS> <CONSTRUCTOR_ARGS>
  ```

**Day 5-6: Gas Optimization**
- [ ] Implement gas-efficient patterns:
  ```solidity
  // Use events instead of storage for data that doesn't need on-chain queries
  event ProductMinted(uint256 indexed tokenId, string serial, address owner);
  
  // Batch minting for lower per-unit cost
  function batchMint(address[] calldata recipients, string[] calldata serials) external {
      require(recipients.length == serials.length, "Length mismatch");
      for (uint i = 0; i < recipients.length; i++) {
          _mint(recipients[i], serials[i]);
      }
  }
  
  // Use EIP-2309 for batch minting (90% gas savings)
  emit ConsecutiveTransfer(startTokenId, endTokenId, address(0), recipient);
  ```

- [ ] Set up gas price monitoring:
  ```java
  @Scheduled(fixedRate = 60000) // Every minute
  public void updateGasPrice() {
      Web3j web3j = Web3j.build(new HttpService("https://polygon-rpc.com"));
      EthGasPrice gasPrice = web3j.ethGasPrice().send();
      
      // Update in-memory cache
      currentGasPrice = gasPrice.getGasPrice();
      
      // Alert if gas too high
      if (currentGasPrice.compareTo(maxAcceptableGas) > 0) {
          alertService.sendAlert("Gas price too high: " + currentGasPrice);
      }
  }
  ```

**Day 7-8: Contract Upgrade Mechanism**
- [ ] Implement OpenZeppelin Upgrades:
  ```solidity
  // ProductNFTUpgradeable.sol
  import "@openzeppelin/contracts-upgradeable/token/ERC721/ERC721Upgradeable.sol";
  import "@openzeppelin/contracts-upgradeable/proxy/utils/UUPSUpgradeable.sol";
  
  contract ProductNFT is ERC721Upgradeable, UUPSUpgradeable {
      function initialize() initializer public {
          __ERC721_init("ProductNFT", "PNFT");
          __UUPSUpgradeable_init();
      }
      
      function _authorizeUpgrade(address newImplementation) internal override onlyOwner {}
  }
  ```

- [ ] Deploy proxy:
  ```javascript
  const { deployProxy } = require('@openzeppelin/hardhat-upgrades');
  
  const ProductNFT = await ethers.getContractFactory("ProductNFT");
  const proxy = await deployProxy(ProductNFT, [], { kind: 'uups' });
  
  console.log("Proxy deployed to:", proxy.address);
  ```

---

#### **Week 11: Kubernetes Production Setup (8 hours)**

**Day 1-2: Cloud Provider Setup**
- [ ] Choose cloud provider:
  - **AWS EKS**: Most mature, expensive ($70/month cluster + nodes)
  - **GCP GKE**: Cheaper, good for startups ($50/month)
  - **DigitalOcean**: Simplest, cheapest ($10/month), good for MVP
  
  **Recommendation: Start with DigitalOcean for Phase 1**

- [ ] Create Kubernetes cluster:
  ```bash
  # DigitalOcean
  doctl kubernetes cluster create supplychain-prod \
    --region nyc1 \
    --version 1.28.2-do.0 \
    --node-pool "name=worker-pool;size=s-2vcpu-4gb;count=3"
  
  # Get kubeconfig
  doctl kubernetes cluster kubeconfig save supplychain-prod
  ```

**Day 3-4: Deploy Application**
- [ ] Apply Kubernetes manifests:
  ```bash
  # Create namespaces
  kubectl apply -f infra/k8s/namespace.yaml
  
  # Deploy databases
  kubectl apply -f infra/k8s/postgresql.yaml
  kubectl apply -f infra/k8s/redis.yaml
  kubectl apply -f infra/k8s/kafka.yaml
  
  # Deploy services
  kubectl apply -f infra/k8s/product-service.yaml
  kubectl apply -f infra/k8s/event-service.yaml
  kubectl apply -f infra/k8s/verification-service.yaml
  
  # Deploy frontend
  kubectl apply -f infra/k8s/frontend.yaml
  
  # Set up ingress
  kubectl apply -f infra/k8s/ingress.yaml
  ```

- [ ] Configure Horizontal Pod Autoscaler:
  ```yaml
  apiVersion: autoscaling/v2
  kind: HorizontalPodAutoscaler
  metadata:
    name: product-service-hpa
  spec:
    scaleTargetRef:
      apiVersion: apps/v1
      kind: Deployment
      name: product-service
    minReplicas: 2
    maxReplicas: 10
    metrics:
    - type: Resource
      resource:
        name: cpu
        target:
          type: Utilization
          averageUtilization: 70
    behavior:
      scaleDown:
        stabilizationWindowSeconds: 300
        policies:
        - type: Percent
          value: 50
          periodSeconds: 60
      scaleUp:
        stabilizationWindowSeconds: 0
        policies:
        - type: Percent
          value: 100
          periodSeconds: 30
  ```

**Day 5-6: Monitoring Setup**
- [ ] Install Prometheus:
  ```bash
  helm repo add prometheus-community https://prometheus-community.github.io/helm-charts
  helm install prometheus prometheus-community/kube-prometheus-stack \
    --namespace monitoring --create-namespace
  ```

- [ ] Configure Grafana dashboards:
  - Pod CPU/Memory usage
  - Request rate (RPS)
  - Latency (p50, p95, p99)
  - Error rate
  - Database connections
  - Cache hit rate

- [ ] Set up alerting:
  ```yaml
  # alerting-rules.yaml
  groups:
  - name: supplychain-alerts
    interval: 30s
    rules:
    - alert: HighErrorRate
      expr: rate(http_requests_total{status=~"5.."}[5m]) > 0.05
      for: 5m
      annotations:
        summary: "High error rate detected"
    
    - alert: HighLatency
      expr: histogram_quantile(0.99, rate(http_request_duration_seconds_bucket[5m])) > 1
      for: 5m
      annotations:
        summary: "p99 latency above 1 second"
    
    - alert: PodCrashLooping
      expr: rate(kube_pod_container_status_restarts_total[15m]) > 0
      annotations:
        summary: "Pod is crash looping"
  ```

---

#### **Week 12: Security Audit & Launch (8 hours)**

**Day 1-3: Security Audit**
- [ ] Hire security auditor:
  - **Smart Contracts**: OpenZeppelin ($15K-$50K) or Consensys Diligence
  - **Backend**: HackerOne bug bounty ($5K initial deposit)
  
- [ ] Run automated security scans:
  ```bash
  # Dependency vulnerabilities
  npm audit
  mvn dependency-check:check
  
  # Container scanning
  trivy image product-service:latest
  
  # Static analysis
  sonar-scanner \
    -Dsonar.projectKey=supplychain-auth \
    -Dsonar.sources=. \
    -Dsonar.host.url=http://localhost:9000
  ```

- [ ] Fix critical/high issues

**Day 4: Pre-Launch Checklist**
- [ ] Performance testing:
  ```bash
  # Load test with 1000 concurrent users
  locust -f performance/locustfile.py --host=https://api.supplychainauth.com
  ```
  - Target: 5,000+ RPS, p99 < 50ms
  
- [ ] Database backup configured
- [ ] SSL certificates (Let's Encrypt)
- [ ] Error tracking (Sentry)
- [ ] Log aggregation (ELK or CloudWatch)
- [ ] DNS configured (yourapp.com → Kubernetes ingress IP)

**Day 5-6: Launch!**
- [ ] **Monday 9 AM**: Press publish on Product Hunt
- [ ] Post launch announcement:
  - Twitter (thread with demo video)
  - LinkedIn (article format)
  - Reddit: r/blockchain, r/startups, r/SaaS
  - Hacker News (Show HN: SupplyChain Auth)
  - Dev.to blog post
  - Medium article
  
- [ ] Email outreach (50 potential customers):
  ```
  Subject: We built blockchain product authentication - 7.6K RPS
  
  Hi [Name],
  
  I noticed [Company] manufactures [products]. Counterfeiting is a huge issue in [industry].
  
  We built SupplyChain Auth - a blockchain-backed product verification system. 
  
  Key features:
  - NFT certificates for every product
  - Mobile app for consumers to verify authenticity
  - Real-time analytics dashboard
  - 7,652 verifications per second (proven in load tests)
  
  Would love to show you a quick demo. 15 minutes this week?
  
  [Your Name]
  [Calendly link]
  ```

**Day 7: Post-Launch Monitoring**
- [ ] Watch dashboards (Grafana)
- [ ] Respond to support requests
- [ ] Monitor social media mentions
- [ ] Track signups, conversions
- [ ] Fix any critical bugs immediately

---

## 🎯 SUCCESS METRICS TRACKING

### Daily Metrics (Check Every Morning)
- [ ] New signups (target: 2-5/day after launch)
- [ ] Active users (DAU)
- [ ] API requests (growing)
- [ ] System uptime (99.9%+)
- [ ] Error rate (<0.1%)

### Weekly Metrics
- [ ] MRR (Monthly Recurring Revenue)
- [ ] Customer acquisition cost (CAC)
- [ ] Churn rate
- [ ] Feature adoption rates
- [ ] Support ticket volume

### Tools to Set Up
- [ ] Mixpanel or Amplitude (product analytics)
- [ ] Stripe Dashboard (revenue)
- [ ] Google Analytics (website traffic)
- [ ] Grafana (technical metrics)

---

## 📞 IMMEDIATE ACTION ITEMS

### This Week:
1. [ ] Set up multi-tenancy branch
2. [ ] Create tenants table migration
3. [ ] Implement tenant context service
4. [ ] Write first integration test

### Next Week:
1. [ ] Complete RBAC schema
2. [ ] Implement authorization service
3. [ ] Apply to first grant program
4. [ ] Reach out to 10 potential customers

### This Month:
1. [ ] Complete Phase 1 (all features working)
2. [ ] Deploy to production
3. [ ] Get first paying customer
4. [ ] Apply to 3 accelerators

---

## 💡 TIPS FOR STAYING ON TRACK

### Daily Routine
- **7:00-7:30**: Review yesterday, plan today
- **7:30-12:00**: Deep work (coding, no interruptions)
- **12:00-13:00**: Lunch + quick walk
- **13:00-16:00**: Meetings, support, admin
- **16:00-17:00**: Marketing/outreach
- **17:00-18:00**: Review progress, update metrics

### Weekly Review (Friday 4-5 PM)
- [ ] What got done this week?
- [ ] What didn't get done? Why?
- [ ] Are we on track for Phase 1 goals?
- [ ] What should change next week?
- [ ] Celebrate small wins!

### When You're Stuck
1. **Technical blocker**: Post on Stack Overflow, ask in Discord
2. **Business question**: DM founders on Twitter, book advisor call
3. **Motivation low**: Watch founder story videos, talk to friends
4. **Overwhelmed**: Focus on ONE task, ignore the rest

---

**Remember: Done is better than perfect. Ship fast, iterate.**

**Next Document: [FUNDING-STRATEGY.md](./FUNDING-STRATEGY.md)** →
