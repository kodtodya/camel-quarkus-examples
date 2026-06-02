package com.kodtodya.insurance.poc.config;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

/**
 * Type-safe configuration for the bulk processing pipeline.
 * Maps to properties under "insurance.processing.*"
 */
@ConfigMapping(prefix = "insurance.processing")
public interface PolicyProcessingConfig {

    /** Records per DB page (LIMIT clause) */
    @WithDefault("1000")
    int pageSize();

    /** Number of concurrent threads consuming from the SEDA queue */
    @WithDefault("10")
    int sedaConcurrency();

    /** Max records SEDA can buffer in memory */
    @WithDefault("50000")
    int sedaQueueSize();

    /** Core thread pool size for parallel processors */
    @WithDefault("20")
    int threadPoolSize();

    /** Max thread pool size */
    @WithDefault("40")
    int threadPoolMaxSize();

    /** Thread pool queue depth */
    @WithDefault("10000")
    int threadPoolQueueSize();

    /** Log a progress message every N records */
    @WithDefault("5000")
    int batchLogInterval();
}
