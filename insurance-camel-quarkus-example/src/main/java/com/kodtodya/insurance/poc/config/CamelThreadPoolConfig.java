package com.kodtodya.insurance.poc.config;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.apache.camel.CamelContext;
import org.apache.camel.spi.ThreadPoolProfile;
import org.jboss.logging.Logger;

import java.util.concurrent.TimeUnit;

/**
 * Registers custom thread pool profiles in the Camel context.
 * These profiles are referenced by route DSL for controlled concurrency.
 */
@ApplicationScoped
public class CamelThreadPoolConfig {

    private static final Logger LOG = Logger.getLogger(CamelThreadPoolConfig.class);

    @Inject
    CamelContext camelContext;

    @Inject
    PolicyProcessingConfig config;

    /**
     * Called after CamelContext is initialized to register profiles.
     * Invoked from InsurancePolicyRoute via @Inject.
     */
    public void registerThreadPoolProfiles() {
        // Main bulk-processing thread pool
        ThreadPoolProfile bulkProfile = new ThreadPoolProfile();
        bulkProfile.setId("bulkProcessingPool");
        bulkProfile.setPoolSize(config.threadPoolSize());
        bulkProfile.setMaxPoolSize(config.threadPoolMaxSize());
        bulkProfile.setMaxQueueSize(config.threadPoolQueueSize());
        bulkProfile.setKeepAliveTime(60L);
        bulkProfile.setTimeUnit(TimeUnit.SECONDS);
        bulkProfile.setDefaultProfile(false);
        camelContext.getExecutorServiceManager().registerThreadPoolProfile(bulkProfile);

        // Pagination / DB-fetch thread pool (smaller, IO bound)
        ThreadPoolProfile paginationProfile = new ThreadPoolProfile();
        paginationProfile.setId("paginationPool");
        paginationProfile.setPoolSize(5);
        paginationProfile.setMaxPoolSize(10);
        paginationProfile.setMaxQueueSize(100);
        paginationProfile.setKeepAliveTime(30L);
        paginationProfile.setTimeUnit(TimeUnit.SECONDS);
        paginationProfile.setDefaultProfile(false);
        camelContext.getExecutorServiceManager().registerThreadPoolProfile(paginationProfile);

        LOG.infof("Registered Camel thread pool profiles: bulkProcessingPool (core=%d, max=%d), paginationPool (core=5, max=10)",
                  config.threadPoolSize(), config.threadPoolMaxSize());
    }
}
