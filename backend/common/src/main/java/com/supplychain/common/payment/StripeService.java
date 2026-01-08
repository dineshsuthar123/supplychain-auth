package com.supplychain.common.payment;

import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.*;
import com.stripe.param.*;
import com.supplychain.common.model.Tenant;
import com.supplychain.common.repository.TenantRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Stripe payment integration service
 * Handles subscription management, billing, and webhooks
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class StripeService {
    
    private final TenantRepository tenantRepository;
    
    @Value("${stripe.api.key:sk_test_replace_with_your_key}")
    private String stripeApiKey;
    
    @Value("${stripe.webhook.secret:whsec_replace_with_your_secret}")
    private String webhookSecret;
    
    @Value("${app.frontend.url:http://localhost:3000}")
    private String frontendUrl;
    
    // Price IDs for each subscription tier (set these in Stripe dashboard)
    @Value("${stripe.price.starter:price_starter}")
    private String starterPriceId;
    
    @Value("${stripe.price.professional:price_professional}")
    private String professionalPriceId;
    
    @Value("${stripe.price.enterprise:price_enterprise}")
    private String enterprisePriceId;
    
    @PostConstruct
    public void init() {
        Stripe.apiKey = stripeApiKey;
        log.info("Stripe service initialized");
    }
    
    /**
     * Create a new Stripe customer for tenant
     */
    @Transactional
    public Customer createCustomer(Tenant tenant, String email, String name) throws StripeException {
        CustomerCreateParams params = CustomerCreateParams.builder()
                .setEmail(email)
                .setName(name)
                .setDescription("Tenant: " + tenant.getName())
                .putMetadata("tenant_id", tenant.getId().toString())
                .putMetadata("tenant_slug", tenant.getSlug())
                .build();
        
        Customer customer = Customer.create(params);
        
        // Update tenant with Stripe customer ID
        tenant.setStripeCustomerId(customer.getId());
        tenantRepository.save(tenant);
        
        log.info("Created Stripe customer {} for tenant {}", customer.getId(), tenant.getId());
        return customer;
    }
    
    /**
     * Create checkout session for subscription
     */
    public Session createCheckoutSession(
            Tenant tenant,
            Tenant.SubscriptionTier tier,
            String successUrl,
            String cancelUrl
    ) throws StripeException {
        
        String priceId = getPriceIdForTier(tier);
        
        SessionCreateParams.Builder paramsBuilder = SessionCreateParams.builder()
                .setMode(SessionCreateParams.Mode.SUBSCRIPTION)
                .setSuccessUrl(successUrl)
                .setCancelUrl(cancelUrl)
                .addLineItem(
                        SessionCreateParams.LineItem.builder()
                                .setPrice(priceId)
                                .setQuantity(1L)
                                .build()
                )
                .putMetadata("tenant_id", tenant.getId().toString())
                .putMetadata("subscription_tier", tier.name());
        
        // Use existing customer or create new
        if (tenant.getStripeCustomerId() != null) {
            paramsBuilder.setCustomer(tenant.getStripeCustomerId());
        } else {
            paramsBuilder.setCustomerEmail(tenant.getSettings()); // Extract email from settings
        }
        
        // Allow trial period
        paramsBuilder.setSubscriptionData(
                SessionCreateParams.SubscriptionData.builder()
                        .setTrialPeriodDays(14L)
                        .putMetadata("tenant_id", tenant.getId().toString())
                        .build()
        );
        
        Session session = Session.create(paramsBuilder.build());
        log.info("Created checkout session {} for tenant {}", session.getId(), tenant.getId());
        
        return session;
    }
    
    /**
     * Create billing portal session for subscription management
     */
    public com.stripe.model.billingportal.Session createBillingPortalSession(
            Tenant tenant,
            String returnUrl
    ) throws StripeException {
        
        if (tenant.getStripeCustomerId() == null) {
            throw new IllegalStateException("Tenant has no Stripe customer ID");
        }
        
        com.stripe.param.billingportal.SessionCreateParams params =
                com.stripe.param.billingportal.SessionCreateParams.builder()
                        .setCustomer(tenant.getStripeCustomerId())
                        .setReturnUrl(returnUrl)
                        .build();
        
        return com.stripe.model.billingportal.Session.create(params);
    }
    
    /**
     * Update subscription tier
     */
    @Transactional
    public Subscription updateSubscription(
            Tenant tenant,
            Tenant.SubscriptionTier newTier
    ) throws StripeException {
        
        if (tenant.getStripeSubscriptionId() == null) {
            throw new IllegalStateException("Tenant has no active subscription");
        }
        
        Subscription subscription = Subscription.retrieve(tenant.getStripeSubscriptionId());
        String newPriceId = getPriceIdForTier(newTier);
        
        SubscriptionUpdateParams params = SubscriptionUpdateParams.builder()
                .addItem(
                        SubscriptionUpdateParams.Item.builder()
                                .setId(subscription.getItems().getData().get(0).getId())
                                .setPrice(newPriceId)
                                .build()
                )
                .setProrationBehavior(SubscriptionUpdateParams.ProrationBehavior.CREATE_PRORATIONS)
                .build();
        
        Subscription updated = subscription.update(params);
        
        // Update tenant
        updateTenantFromSubscription(tenant, updated);
        
        log.info("Updated subscription for tenant {} to tier {}", tenant.getId(), newTier);
        return updated;
    }
    
    /**
     * Cancel subscription
     */
    @Transactional
    public Subscription cancelSubscription(Tenant tenant, boolean immediate) throws StripeException {
        if (tenant.getStripeSubscriptionId() == null) {
            throw new IllegalStateException("Tenant has no active subscription");
        }
        
        Subscription subscription = Subscription.retrieve(tenant.getStripeSubscriptionId());
        
        if (immediate) {
            subscription = subscription.cancel();
            tenant.setSubscriptionStatus(Tenant.SubscriptionStatus.CANCELED);
        } else {
            SubscriptionUpdateParams params = SubscriptionUpdateParams.builder()
                    .setCancelAtPeriodEnd(true)
                    .build();
            subscription = subscription.update(params);
            // Keep active until period ends
        }
        
        tenantRepository.save(tenant);
        log.info("Canceled subscription for tenant {}", tenant.getId());
        
        return subscription;
    }
    
    /**
     * Record usage for metered billing
     */
    public UsageRecord recordUsage(
            Tenant tenant,
            String subscriptionItemId,
            long quantity,
            String action
    ) throws StripeException {
        
        UsageRecordCreateOnSubscriptionItemParams params =
                UsageRecordCreateOnSubscriptionItemParams.builder()
                        .setQuantity(quantity)
                        .setTimestamp(System.currentTimeMillis() / 1000)
                        .setAction(UsageRecordCreateOnSubscriptionItemParams.Action.valueOf(action.toUpperCase()))
                        .build();
        
        return UsageRecord.createOnSubscriptionItem(subscriptionItemId, params, null);
    }
    
    /**
     * Process Stripe webhook event
     */
    @Transactional
    public void handleWebhookEvent(Event event) {
        log.info("Processing Stripe webhook event: {}", event.getType());
        
        switch (event.getType()) {
            case "checkout.session.completed":
                handleCheckoutCompleted(event);
                break;
                
            case "customer.subscription.created":
            case "customer.subscription.updated":
                handleSubscriptionUpdate(event);
                break;
                
            case "customer.subscription.deleted":
                handleSubscriptionDeleted(event);
                break;
                
            case "invoice.payment_succeeded":
                handlePaymentSucceeded(event);
                break;
                
            case "invoice.payment_failed":
                handlePaymentFailed(event);
                break;
                
            case "customer.subscription.trial_will_end":
                handleTrialEnding(event);
                break;
                
            default:
                log.debug("Unhandled event type: {}", event.getType());
        }
    }
    
    /**
     * Handle checkout session completed
     */
    private void handleCheckoutCompleted(Event event) {
        Session session = (Session) event.getDataObjectDeserializer().getObject().orElse(null);
        if (session == null) return;
        
        String tenantIdStr = session.getMetadata().get("tenant_id");
        if (tenantIdStr == null) {
            log.warn("Checkout session has no tenant_id metadata");
            return;
        }
        
        UUID tenantId = UUID.fromString(tenantIdStr);
        Tenant tenant = tenantRepository.findById(tenantId).orElse(null);
        if (tenant == null) {
            log.error("Tenant not found for checkout session: {}", tenantId);
            return;
        }
        
        // Update tenant with subscription info
        tenant.setStripeCustomerId(session.getCustomer());
        tenant.setStripeSubscriptionId(session.getSubscription());
        tenant.setSubscriptionStatus(Tenant.SubscriptionStatus.ACTIVE);
        tenant.setSubscriptionStartDate(LocalDateTime.now());
        
        tenantRepository.save(tenant);
        log.info("Checkout completed for tenant {}", tenantId);
    }
    
    /**
     * Handle subscription update
     */
    private void handleSubscriptionUpdate(Event event) {
        Subscription subscription = (Subscription) event.getDataObjectDeserializer().getObject().orElse(null);
        if (subscription == null) return;
        
        String tenantIdStr = subscription.getMetadata().get("tenant_id");
        if (tenantIdStr != null) {
            UUID tenantId = UUID.fromString(tenantIdStr);
            Tenant tenant = tenantRepository.findById(tenantId).orElse(null);
            if (tenant != null) {
                updateTenantFromSubscription(tenant, subscription);
            }
        }
    }
    
    /**
     * Handle subscription deleted
     */
    private void handleSubscriptionDeleted(Event event) {
        Subscription subscription = (Subscription) event.getDataObjectDeserializer().getObject().orElse(null);
        if (subscription == null) return;
        
        tenantRepository.findByStripeSubscriptionId(subscription.getId()).ifPresent(tenant -> {
            tenant.setSubscriptionStatus(Tenant.SubscriptionStatus.CANCELED);
            tenant.setSubscriptionEndDate(LocalDateTime.now());
            tenantRepository.save(tenant);
            log.info("Subscription deleted for tenant {}", tenant.getId());
        });
    }
    
    /**
     * Handle successful payment
     */
    private void handlePaymentSucceeded(Event event) {
        Invoice invoice = (Invoice) event.getDataObjectDeserializer().getObject().orElse(null);
        if (invoice == null) return;
        
        tenantRepository.findByStripeCustomerId(invoice.getCustomer()).ifPresent(tenant -> {
            tenant.setLastBillingDate(LocalDateTime.now());
            tenant.setNextBillingDate(
                    LocalDateTime.ofInstant(
                            java.time.Instant.ofEpochSecond(invoice.getPeriodEnd()),
                            ZoneId.systemDefault()
                    )
            );
            tenant.setSubscriptionStatus(Tenant.SubscriptionStatus.ACTIVE);
            tenantRepository.save(tenant);
            
            log.info("Payment succeeded for tenant {}: ${}", tenant.getId(), 
                    invoice.getAmountPaid() / 100.0);
        });
    }
    
    /**
     * Handle failed payment
     */
    private void handlePaymentFailed(Event event) {
        Invoice invoice = (Invoice) event.getDataObjectDeserializer().getObject().orElse(null);
        if (invoice == null) return;
        
        tenantRepository.findByStripeCustomerId(invoice.getCustomer()).ifPresent(tenant -> {
            tenant.setSubscriptionStatus(Tenant.SubscriptionStatus.PAST_DUE);
            tenantRepository.save(tenant);
            
            log.warn("Payment failed for tenant {}", tenant.getId());
            // TODO: Send notification email
        });
    }
    
    /**
     * Handle trial ending soon
     */
    private void handleTrialEnding(Event event) {
        Subscription subscription = (Subscription) event.getDataObjectDeserializer().getObject().orElse(null);
        if (subscription == null) return;
        
        String tenantIdStr = subscription.getMetadata().get("tenant_id");
        if (tenantIdStr != null) {
            log.info("Trial ending soon for tenant: {}", tenantIdStr);
            // TODO: Send notification email
        }
    }
    
    /**
     * Update tenant limits based on subscription
     */
    private void updateTenantFromSubscription(Tenant tenant, Subscription subscription) {
        String status = subscription.getStatus();
        tenant.setSubscriptionStatus(mapStripeStatus(status));
        
        // Update limits based on subscription tier
        String priceId = subscription.getItems().getData().get(0).getPrice().getId();
        Tenant.SubscriptionTier tier = getTierFromPriceId(priceId);
        tenant.setSubscriptionTier(tier);
        
        applyTierLimits(tenant, tier);
        tenantRepository.save(tenant);
    }
    
    /**
     * Map Stripe status to tenant status
     */
    private Tenant.SubscriptionStatus mapStripeStatus(String stripeStatus) {
        return switch (stripeStatus) {
            case "active" -> Tenant.SubscriptionStatus.ACTIVE;
            case "trialing" -> Tenant.SubscriptionStatus.TRIAL;
            case "past_due" -> Tenant.SubscriptionStatus.PAST_DUE;
            case "canceled" -> Tenant.SubscriptionStatus.CANCELED;
            case "unpaid" -> Tenant.SubscriptionStatus.SUSPENDED;
            default -> Tenant.SubscriptionStatus.EXPIRED;
        };
    }
    
    /**
     * Get price ID for subscription tier
     */
    private String getPriceIdForTier(Tenant.SubscriptionTier tier) {
        return switch (tier) {
            case STARTER -> starterPriceId;
            case PROFESSIONAL -> professionalPriceId;
            case ENTERPRISE -> enterprisePriceId;
            default -> throw new IllegalArgumentException("Invalid tier: " + tier);
        };
    }
    
    /**
     * Get tier from Stripe price ID
     */
    private Tenant.SubscriptionTier getTierFromPriceId(String priceId) {
        if (priceId.equals(starterPriceId)) return Tenant.SubscriptionTier.STARTER;
        if (priceId.equals(professionalPriceId)) return Tenant.SubscriptionTier.PROFESSIONAL;
        if (priceId.equals(enterprisePriceId)) return Tenant.SubscriptionTier.ENTERPRISE;
        return Tenant.SubscriptionTier.FREE;
    }
    
    /**
     * Apply resource limits based on tier
     */
    private void applyTierLimits(Tenant tenant, Tenant.SubscriptionTier tier) {
        switch (tier) {
            case FREE:
                tenant.setMonthlyVerificationLimit(1000);
                tenant.setMonthlyRegistrationLimit(100);
                tenant.setMaxProducts(10000);
                tenant.setMaxUsers(5);
                tenant.setAnalyticsEnabled(false);
                tenant.setMultiChainEnabled(false);
                tenant.setIotIntegrationEnabled(false);
                tenant.setMlFraudDetectionEnabled(false);
                break;
                
            case STARTER:
                tenant.setMonthlyVerificationLimit(10000);
                tenant.setMonthlyRegistrationLimit(1000);
                tenant.setMaxProducts(50000);
                tenant.setMaxUsers(10);
                tenant.setAnalyticsEnabled(true);
                tenant.setMultiChainEnabled(false);
                tenant.setIotIntegrationEnabled(false);
                tenant.setMlFraudDetectionEnabled(false);
                break;
                
            case PROFESSIONAL:
                tenant.setMonthlyVerificationLimit(100000);
                tenant.setMonthlyRegistrationLimit(10000);
                tenant.setMaxProducts(500000);
                tenant.setMaxUsers(50);
                tenant.setAnalyticsEnabled(true);
                tenant.setMultiChainEnabled(true);
                tenant.setIotIntegrationEnabled(true);
                tenant.setMlFraudDetectionEnabled(true);
                break;
                
            case ENTERPRISE:
                tenant.setMonthlyVerificationLimit(Integer.MAX_VALUE);
                tenant.setMonthlyRegistrationLimit(Integer.MAX_VALUE);
                tenant.setMaxProducts(Integer.MAX_VALUE);
                tenant.setMaxUsers(Integer.MAX_VALUE);
                tenant.setAnalyticsEnabled(true);
                tenant.setMultiChainEnabled(true);
                tenant.setIotIntegrationEnabled(true);
                tenant.setMlFraudDetectionEnabled(true);
                break;
        }
    }
}
