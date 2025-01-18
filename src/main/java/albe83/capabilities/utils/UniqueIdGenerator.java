package albe83.capabilities.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A generator for 128-bit unique IDs, including a timestamp, node ID, sequence, and random value.
 */
@Component
public class UniqueIdGenerator {

    private static final Logger logger = LoggerFactory.getLogger(UniqueIdGenerator.class);

    // Configuration parameters
    private final long epoch; // Epoch timestamp to base IDs on
    private final int nodeId; // Unique node identifier
    private final TimestampProvider timestampProvider; // Timestamp provider for testability

    // Bit allocation constants
    private final int sequenceBits = 16; // Bits allocated for the sequence number
    private final int randomBits = 16;   // Bits allocated for the random number
    private final int nodeIdBits = 32;  // Bits allocated for the node ID

    private final int maxSequence = (1 << sequenceBits) - 1; // Maximum value for the sequence number
    private int sequence = 0; // Current sequence number
    private long lastTimestamp = -1L; // Last timestamp used to generate an ID
    private final long constantRandom; // Constant random value or indicator for dynamic generation

    // Reused instance of SecureRandom to avoid performance overhead
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    // Cached time updated periodically
    private static final AtomicLong cachedTime = new AtomicLong(System.currentTimeMillis());

    private static boolean enableCaching = true; // Flag to control caching behavior

    static {
        if (enableCaching) {
            // Background thread to update the cached time
            Thread timeUpdater = new Thread(() -> {
                while (true) {
                    try {
                        Thread.sleep(1); // Update every millisecond
                        cachedTime.set(System.currentTimeMillis());
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            });
            timeUpdater.setDaemon(true);
            timeUpdater.start();
        }
    }

    /**
     * Constructs a UniqueIdGenerator instance with the specified configuration and timestamp provider.
     *
     * @param config            The configuration for ID generation.
     * @param timestampProvider The provider for the current timestamp.
     * @param generateRandomPerId If true, a new random value will be generated for each ID.
     */
    private UniqueIdGenerator(UniqueIdConfig config, TimestampProvider timestampProvider, boolean generateRandomPerId) {
        if (config.getEpoch() > timestampProvider.getCurrentTimestamp()) {
            logger.error("Invalid configuration: provided epoch ({}) is in the future compared to the current timestamp ({}). " +
                         "This typically indicates a configuration or synchronization issue in the distributed system.",
                         config.getEpoch(), timestampProvider.getCurrentTimestamp());
            throw new IllegalArgumentException("Epoch cannot be in the future. Ensure the epoch is correctly synchronized with the system clock.");
        }
        this.epoch = config.getEpoch();
        this.nodeId = config.getNodeId();
        this.timestampProvider = timestampProvider;
        this.constantRandom = generateRandomPerId ? -1 : config.getConstantRandom();
        logger.info("UniqueIdGenerator initialized with epoch: {}, nodeId: {}", epoch, nodeId);
    }

    /**
     * Builder class for UniqueIdGenerator to facilitate controlled instantiation.
     */
    public static class Builder {
        private UniqueIdConfig config;
        private TimestampProvider timestampProvider = new SystemTimestampProvider();
        private boolean generateRandomPerId = false;
        private boolean enableCaching = true;

        /**
         * Sets the configuration for the generator.
         *
         * @param config The configuration object.
         * @return This Builder instance.
         */
        public Builder setConfig(UniqueIdConfig config) {
            this.config = config;
            return this;
        }

        /**
         * Sets the timestamp provider for the generator.
         *
         * @param timestampProvider The provider for timestamps.
         * @return This Builder instance.
         */
        public Builder setTimestampProvider(TimestampProvider timestampProvider) {
            this.timestampProvider = timestampProvider;
            return this;
        }

        /**
         * Configures whether a random value is generated for each ID.
         *
         * @param generateRandomPerId If true, generates a new random value for each ID.
         * @return This Builder instance.
         */
        public Builder setGenerateRandomPerId(boolean generateRandomPerId) {
            this.generateRandomPerId = generateRandomPerId;
            return this;
        }

        /**
         * Enables or disables caching for timestamp updates.
         *
         * @param enableCaching If true, caching will be enabled.
         * @return This Builder instance.
         */
        public Builder setEnableCaching(boolean enableCaching) {
            UniqueIdGenerator.enableCaching = enableCaching;
            return this;
        }

        /**
         * Builds the UniqueIdGenerator instance.
         *
         * @return A new UniqueIdGenerator instance.
         * @throws IllegalStateException if the configuration is not set.
         */
        public UniqueIdGenerator build() {
            if (config == null) {
                throw new IllegalStateException("Config must be provided.");
            }
            return new UniqueIdGenerator(config, timestampProvider, generateRandomPerId);
        }
    }

    /**
     * Generates a 128-bit unique ID.
     *
     * @return The generated unique ID.
     */
    public long generateId() {
        long currentTimestamp = getCachedTimestamp();

        // Adjust the timestamp if the clock has moved backwards
        currentTimestamp = adjustClockIfNeeded(currentTimestamp);

        // Update the sequence number
        updateSequence(currentTimestamp);

        // Construct the final unique ID
        return constructId(currentTimestamp);
    }

    /**
     * Retrieves the cached timestamp or falls back to the system timestamp if caching is disabled.
     *
     * @return The current timestamp value.
     */
    private long getCachedTimestamp() {
        return enableCaching ? cachedTime.get() : timestampProvider.getCurrentTimestamp();
    }

    /**
     * Adjusts the timestamp if the clock has moved backwards, ensuring monotonicity.
     *
     * @param currentTimestamp The current timestamp from the provider.
     * @return The adjusted timestamp.
     */
    private long adjustClockIfNeeded(long currentTimestamp) {
        if (currentTimestamp < lastTimestamp) {
            logger.warn("Clock moved backwards on node {}. Adjusting timestamp from {} to {}. " +
                    "Ensure the system clock is synchronized to prevent future issues.", 
                    nodeId, currentTimestamp, lastTimestamp + 1);
            return ++lastTimestamp;
        }
        return currentTimestamp;
    }

    /**
     * Updates the sequence for the current millisecond or resets it if a new millisecond is detected.
     *
     * @param currentTimestamp The current timestamp.
     */
    private void updateSequence(long currentTimestamp) {
        if (currentTimestamp == lastTimestamp) {
            sequence++;
            if (sequence > maxSequence) {
                lastTimestamp = waitForNextMillis(currentTimestamp);
                sequence = 0;
            }
        } else {
            sequence = 0;
        }
    }

    /**
     * Constructs the 128-bit unique ID by combining timestamp, node ID, sequence, and random values.
     *
     * @param currentTimestamp The current timestamp.
     * @return The constructed unique ID.
     */
    private long constructId(long currentTimestamp) {
        lastTimestamp = currentTimestamp;
        long randomPart = (constantRandom != -1) ? constantRandom : SECURE_RANDOM.nextInt(1 << randomBits);

        // Explanation of bitwise operations:
        // 1. The timestamp (64 bits) is shifted left to occupy the highest bits.
        // 2. The node ID (32 bits) follows, shifted to make space for sequence and random bits.
        // 3. The sequence (16 bits) is shifted to make space for the random bits.
        // 4. The random part (16 bits) occupies the lowest bits.
        return ((currentTimestamp - epoch) << (nodeIdBits + sequenceBits + randomBits)) |
               ((long) nodeId << (sequenceBits + randomBits)) |
               ((long) sequence << randomBits) |
               randomPart;
    }

    /**
     * Waits for the next millisecond if the sequence number overflows within the same timestamp.
     *
     * @param currentTimestamp The current timestamp.
     * @return The updated timestamp after waiting.
     *
     * <p>Performance Note:</p>
     * In high-throughput scenarios, this loop can introduce delays. Consider increasing the number of bits for the sequence
     * or implementing sharding techniques to reduce contention on a single generator instance.
     */
    private long waitForNextMillis(long currentTimestamp) {
        while (currentTimestamp <= lastTimestamp) {
            currentTimestamp = getCachedTimestamp();
        }
        return currentTimestamp;
    }

    /**
     * Interface for providing the current timestamp.
     * Allows for testing and mocking in controlled environments.
     */
    public interface TimestampProvider {
        long getCurrentTimestamp();
    }

    /**
     * Default implementation of TimestampProvider using the system clock.
     */
    public static class SystemTimestampProvider implements TimestampProvider {
        @Override
        public long getCurrentTimestamp() {
            return Instant.now().toEpochMilli();
        }
    }
}
