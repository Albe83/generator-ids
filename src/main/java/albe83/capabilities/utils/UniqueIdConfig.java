package albe83.capabilities.utils;

/**
 * Configuration class for UniqueIdGenerator.
 * This encapsulates all configurable parameters required for ID generation.
 */
public class UniqueIdConfig {
    private final long epoch;
    private final int nodeId;
    private final long constantRandom;

    /**
     * Constructs a configuration object for UniqueIdGenerator.
     *
     * @param epoch          The epoch timestamp.
     * @param nodeId         The unique identifier for the node.
     * @param constantRandom A constant random value (optional).
     */
    public UniqueIdConfig(long epoch, int nodeId, long constantRandom) {
        // Validate that the node ID is within the valid range of 32 bits
        if (nodeId < 0 || nodeId > 0xFFFFFFFFL) {
            throw new IllegalArgumentException("Node ID must be between 0 and 2^32 - 1.");
        }
        this.epoch = epoch;
        this.nodeId = nodeId;
        this.constantRandom = constantRandom;
    }

    /**
     * Returns the configured epoch timestamp.
     *
     * @return The epoch timestamp.
     */
    public long getEpoch() {
        return epoch;
    }

    /**
     * Returns the configured node ID.
     *
     * @return The node ID.
     */
    public int getNodeId() {
        return nodeId;
    }

    /**
     * Returns the constant random value.
     *
     * @return The constant random value.
     */
    public long getConstantRandom() {
        return constantRandom;
    }
}
