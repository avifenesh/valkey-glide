package glide.benchmark.service;

import glide.api.GlideClusterClient;
import glide.api.models.GlideString;
import glide.benchmark.util.DataGenerationUtil;

import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Service responsible for populating test data in the cache
 * using the specified key pattern and value distribution.
 */
@Service
public class DataPopulationService {

    private static final Logger logger = LoggerFactory.getLogger(DataPopulationService.class);

    @Value("${benchmark.key-space-size}")
    private int keySpaceSize;

    private final DataGenerationUtil dataGenerationUtil;
    
    @Autowired
    @Qualifier("valkeyGlideClient")
    private GlideClusterClient valkeyGlideClient;
    
    @Autowired
    @Qualifier("redisGlideClient")
    private GlideClusterClient redisGlideClient;
    
    @Autowired
    @Qualifier("valkeyRedissonClient")
    private RedissonClient valkeyRedissonClient;
    
    @Autowired
    @Qualifier("redisRedissonClient")
    private RedissonClient redisRedissonClient;

    public DataPopulationService(DataGenerationUtil dataGenerationUtil) {
        this.dataGenerationUtil = dataGenerationUtil;
    }

    /**
     * Populate the Valkey database using the Glide client.
     * @param batchSize Number of keys to process in each batch
     * @param threadCount Number of threads to use for population
     * @return Number of keys populated
     */
    public int populateValkeyWithGlide(int batchSize, int threadCount) throws ExecutionException, InterruptedException {
        logger.info("Starting to populate Valkey database using Glide client...");
        return populateWithGlide(valkeyGlideClient, batchSize, threadCount);
    }
    
    /**
     * Populate the Redis database using the Glide client.
     * @param batchSize Number of keys to process in each batch
     * @param threadCount Number of threads to use for population
     * @return Number of keys populated
     */
    public int populateRedisWithGlide(int batchSize, int threadCount) throws ExecutionException, InterruptedException {
        logger.info("Starting to populate Redis database using Glide client...");
        return populateWithGlide(redisGlideClient, batchSize, threadCount);
    }
    
    /**
     * Populate the Valkey database using the Redisson client.
     * @param batchSize Number of keys to process in each batch
     * @param threadCount Number of threads to use for population
     * @return Number of keys populated
     */
    public int populateValkeyWithRedisson(int batchSize, int threadCount) throws ExecutionException, InterruptedException {
        logger.info("Starting to populate Valkey database using Redisson client...");
        return populateWithRedisson(valkeyRedissonClient, batchSize, threadCount);
    }
    
    /**
     * Populate the Redis database using the Redisson client.
     * @param batchSize Number of keys to process in each batch
     * @param threadCount Number of threads to use for population
     * @return Number of keys populated
     */
    public int populateRedisWithRedisson(int batchSize, int threadCount) throws ExecutionException, InterruptedException {
        logger.info("Starting to populate Redis database using Redisson client...");
        return populateWithRedisson(redisRedissonClient, batchSize, threadCount);
    }

    /**
     * Populate database using Glide client.
     */
    private int populateWithGlide(GlideClusterClient client, int batchSize, int threadCount)
            throws ExecutionException, InterruptedException {
        
        int titleCount = dataGenerationUtil.calculateTitleCount(keySpaceSize);
        logger.info("Calculated title count: {} for {} keys", titleCount, keySpaceSize);
        
        AtomicInteger totalKeys = new AtomicInteger(0);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        
        Instant startTime = Instant.now();
        
        // Process titles in batches
        for (int startTitle = 1; startTitle <= titleCount; startTitle += batchSize) {
            int endTitle = Math.min(startTitle + batchSize - 1, titleCount);
            final int start = startTitle;
            final int end = endTitle;
            
            executor.submit(() -> {
                try {
                    int localKeys = populateTitlesRangeWithGlide(client, start, end);
                    int current = totalKeys.addAndGet(localKeys);
                    if (current % 10000 == 0 || current == keySpaceSize) {
                        logger.info("Populated {} keys so far...", current);
                    }
                } catch (Exception e) {
                    logger.error("Error populating keys in range {}-{}: {}", start, end, e.getMessage(), e);
                }
            });
        }
        
        executor.shutdown();
        boolean completed = executor.awaitTermination(1, TimeUnit.HOURS);
        
        Instant endTime = Instant.now();
        double durationSeconds = Duration.between(startTime, endTime).toMillis() / 1000.0;
        
        if (!completed) {
            logger.warn("Data population did not complete within the timeout period");
        }
        
        int populatedKeys = totalKeys.get();
        logger.info("Data population completed in {:.2f} seconds, {} keys populated ({:.2f} keys/sec)",
                durationSeconds, populatedKeys, populatedKeys / durationSeconds);
        
        return populatedKeys;
    }
    
    /**
     * Populate a range of titles using Glide client.
     */
    private int populateTitlesRangeWithGlide(GlideClusterClient client, int startTitle, int endTitle) 
            throws ExecutionException, InterruptedException {
        
        int keysPopulated = 0;
        
        for (int titleId = startTitle; titleId <= endTitle; titleId++) {
            for (String locale : dataGenerationUtil.getLocales()) {
                for (String attribute : dataGenerationUtil.getAttributes()) {
                    String key = dataGenerationUtil.generateKey(titleId, locale, attribute);
                    String value = dataGenerationUtil.generateValueForAttribute(attribute);
                    
                    client.set(GlideString.gs(key), GlideString.gs(value)).get();
                    keysPopulated++;
                }
            }
        }
        
        return keysPopulated;
    }
    
    /**
     * Populate database using Redisson client.
     */
    private int populateWithRedisson(RedissonClient client, int batchSize, int threadCount)
            throws ExecutionException, InterruptedException {
        
        int titleCount = dataGenerationUtil.calculateTitleCount(keySpaceSize);
        logger.info("Calculated title count: {} for {} keys", titleCount, keySpaceSize);
        
        AtomicInteger totalKeys = new AtomicInteger(0);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        
        Instant startTime = Instant.now();
        
        // Process titles in batches
        for (int startTitle = 1; startTitle <= titleCount; startTitle += batchSize) {
            int endTitle = Math.min(startTitle + batchSize - 1, titleCount);
            final int start = startTitle;
            final int end = endTitle;
            
            executor.submit(() -> {
                try {
                    int localKeys = populateTitlesRangeWithRedisson(client, start, end);
                    int current = totalKeys.addAndGet(localKeys);
                    if (current % 10000 == 0 || current == keySpaceSize) {
                        logger.info("Populated {} keys so far...", current);
                    }
                } catch (Exception e) {
                    logger.error("Error populating keys in range {}-{}: {}", start, end, e.getMessage(), e);
                }
            });
        }
        
        executor.shutdown();
        boolean completed = executor.awaitTermination(1, TimeUnit.HOURS);
        
        Instant endTime = Instant.now();
        double durationSeconds = Duration.between(startTime, endTime).toMillis() / 1000.0;
        
        if (!completed) {
            logger.warn("Data population did not complete within the timeout period");
        }
        
        int populatedKeys = totalKeys.get();
        logger.info("Data population completed in {:.2f} seconds, {} keys populated ({:.2f} keys/sec)",
                durationSeconds, populatedKeys, populatedKeys / durationSeconds);
        
        return populatedKeys;
    }
    
    /**
     * Populate a range of titles using Redisson client.
     */
    private int populateTitlesRangeWithRedisson(RedissonClient client, int startTitle, int endTitle) {
        int keysPopulated = 0;
        
        for (int titleId = startTitle; titleId <= endTitle; titleId++) {
            for (String locale : dataGenerationUtil.getLocales()) {
                for (String attribute : dataGenerationUtil.getAttributes()) {
                    String key = dataGenerationUtil.generateKey(titleId, locale, attribute);
                    String value = dataGenerationUtil.generateValueForAttribute(attribute);
                    
                    RBucket<String> bucket = client.getBucket(key);
                    bucket.set(value);
                    keysPopulated++;
                }
            }
        }
        
        return keysPopulated;
    }
    
    /**
     * Verify the data was populated correctly by checking a sample of keys.
     */
    public double verifyDataWithGlide(GlideClusterClient client, int sampleSize) 
            throws ExecutionException, InterruptedException {
        
        int titleCount = dataGenerationUtil.calculateTitleCount(keySpaceSize);
        int hits = 0;
        
        logger.info("Verifying data population with {} sample keys...", sampleSize);
        
        for (int i = 0; i < sampleSize; i++) {
            int titleId = (i % titleCount) + 1;
            String locale = dataGenerationUtil.getLocales()[i % dataGenerationUtil.getLocales().length];
            String attribute = dataGenerationUtil.getAttributes()[i % dataGenerationUtil.getAttributes().length];
            
            String key = dataGenerationUtil.generateKey(titleId, locale, attribute);
            String value = client.get(GlideString.gs(key)).get().toString();
            
            if (value != null) {
                hits++;
            }
        }
        
        double hitRate = (double)hits / sampleSize * 100;
        logger.info("Verification complete: {}/{} keys found ({:.2f}%)", hits, sampleSize, hitRate);
        
        return hitRate;
    }
}
