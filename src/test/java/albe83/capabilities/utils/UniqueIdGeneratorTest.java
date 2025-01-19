package albe83.capabilities.utils;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Instant;
import java.time.Clock;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class UniqueIdGeneratorTest {

    private static final long TEST_EPOCH = 1672531200000L; // Epoch example: 2023-01-01T00:00:00Z
    private static final int NODE_ID_BITS = 32; // Number of bits reserved for the node ID (aligned with UniqueIdGenerator)
    private static final int NODE_ID_SHIFT = 48; // Bit shift to extract the node ID (adjusted for 32 bits)
    private static final int SEQUENCE_BITS = 16; // Bits allocated for the sequence number
    private static final int RANDOM_BITS = 16; // Bits allocated for the random number

    private UniqueIdGenerator uniqueIdGenerator; // Instance of the class under test
    private UniqueIdConfig mockConfig; // Mocked configuration for testing to allow isolation of the UniqueIdGenerator logic and control over test inputs
    private Clock testClock; // Clock instance to control system time in tests

    @BeforeEach
    void setUp() {
        // Create a mock configuration object using Mockito
        mockConfig = Mockito.mock(UniqueIdConfig.class);

        // Configure the mock to return a fixed node ID
        Mockito.when(mockConfig.getNodeId()).thenReturn(Integer.valueOf(1)); // Use Long to match Mockito expectations

        // Configure the mock to return a specific epoch timestamp
        Mockito.when(mockConfig.getEpoch()).thenReturn(Long.valueOf(TEST_EPOCH)); // Use Long to match Mockito expectations

        // Set a fixed clock for testing
        testClock = Clock.fixed(Instant.now(), Clock.systemDefaultZone().getZone());

        // Initialize the UniqueIdGenerator with the mocked configuration
        uniqueIdGenerator = new UniqueIdGenerator.Builder()
                .setConfig(mockConfig)
                .setTimestampProvider(() -> Instant.now(testClock).toEpochMilli())
                .build();
    }

    @Test
    void testGenerateUniqueId() {
        // Generate two unique IDs
        long id1 = uniqueIdGenerator.generateId();
        long id2 = uniqueIdGenerator.generateId();

        // Verify that the IDs are unique
        assertNotEquals(id1, id2, "Generated IDs should be unique");

        // Verify that the generated ID is a positive number
        assertTrue(id1 > 0, "Generated ID should be positive");
    }

    @Test
    void testGenerateMultipleUniqueIds() {
        // Generate a large number of unique IDs
        int idCount = 10000;
        Set<Long> uniqueIds = new HashSet<>();

        for (int i = 0; i < idCount; i++) {
            uniqueIds.add(uniqueIdGenerator.generateId());
        }

        // Verify that the number of unique IDs matches the expected count
        assertEquals(idCount, uniqueIds.size(), "All generated IDs should be unique");
    }

    @Test
    void testNodeIdConfiguration() {
        // Generate a unique ID
        long id = uniqueIdGenerator.generateId();

        // Extract the node ID from the generated ID using bit manipulation
        long nodeId = extractNodeId(id);

        // Verify that the extracted node ID matches the expected value
        assertEquals(1L, nodeId, "Node ID should match the configured value");
    }

    @Test
    void testTimestampIsValid() {
        // Generate a unique ID
        long id = uniqueIdGenerator.generateId();

        // Extract the timestamp from the generated ID
        long timestamp = extractTimestamp(id);

        // Verify that the extracted timestamp is not in the future
        assertTrue(timestamp <= Instant.now(testClock).toEpochMilli(), "Timestamp in ID should not be in the future");
    }

    @Test
    void testSequenceAndRandomBits() {
        // Generate a unique ID
        long id = uniqueIdGenerator.generateId();

        // Extract the sequence and random parts from the ID
        long sequence = extractSequence(id);
        long randomPart = extractRandom(id);

        // Verify the sequence and random values are within valid ranges
        assertTrue(sequence >= 0 && sequence < (1 << SEQUENCE_BITS), "Sequence should be within valid range");
        assertTrue(randomPart >= 0 && randomPart < (1 << RANDOM_BITS), "Random part should be within valid range");
    }

    private long extractNodeId(long id) {
        return (id >> NODE_ID_SHIFT) & ((1L << NODE_ID_BITS) - 1);
    }

    private long extractTimestamp(long id) {
        return (id >>> (NODE_ID_BITS + SEQUENCE_BITS + RANDOM_BITS)) + mockConfig.getEpoch();
    }

    private long extractSequence(long id) {
        return (id >> RANDOM_BITS) & ((1 << SEQUENCE_BITS) - 1);
    }

    private long extractRandom(long id) {
        return id & ((1 << RANDOM_BITS) - 1);
    }
}
