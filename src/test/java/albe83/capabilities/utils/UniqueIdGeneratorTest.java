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
    private static final int NODE_ID_BITS = 32; // Number of bits reserved for the node ID. This allows for 2^32 unique node identifiers, ensuring scalability in distributed systems.
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
        Mockito.when(mockConfig.getNodeId()).thenReturn(1L);

        // Configure the mock to return a specific epoch timestamp
        Mockito.when(mockConfig.getEpoch()).thenReturn(TEST_EPOCH);

        // Set a fixed clock for testing to ensure deterministic behavior
        testClock = Clock.fixed(Instant.now(), Clock.systemDefaultZone().getZone());

        // Initialize the UniqueIdGenerator with the mocked configuration
        uniqueIdGenerator = new UniqueIdGenerator.Builder()
                .setConfig(mockConfig) // Use the mocked configuration
                .setTimestampProvider(() -> Instant.now(testClock).toEpochMilli()) // Provide a fixed timestamp for consistency
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
            uniqueIds.add(uniqueIdGenerator.generateId()); // Add each generated ID to the set. Note: HashSet ensures uniqueness but can be slower for large datasets. Consider using alternate data structures like concurrent hash maps for performance optimization in multithreaded environments.
        }

        // Verify that the number of unique IDs matches the expected count
        assertEquals(idCount, uniqueIds.size(), "All generated IDs should be unique");
    }

    @Test
    void testNodeIdConfiguration() {
        // Generate a unique ID
        long id = uniqueIdGenerator.generateId();

        // Extract the node ID from the generated ID using bit manipulation
        long nodeId = extractNodeId(id); // The node ID is tested separately to ensure that it is correctly embedded in the ID, which is critical for distinguishing nodes in a distributed system.

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
        // Note: Precise clock synchronization is assumed. If discrepancies occur due to clock drift or latency,
        // the system should include mechanisms like clock synchronization (e.g., NTP) or error handling to adjust timestamps.
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

    // Helper method to extract the node ID from a generated ID
    private long extractNodeId(long id) {
        return (id >> NODE_ID_SHIFT) & ((1L << NODE_ID_BITS) - 1);
    }

    // Helper method to extract the timestamp from a generated ID
    private long extractTimestamp(long id) {
        // Shift right to remove the bits for node ID, sequence, and random values.
        // This isolates the timestamp bits.
        // Add the epoch to convert the relative timestamp back to an absolute timestamp.
        return (id >>> (NODE_ID_BITS + SEQUENCE_BITS + RANDOM_BITS)) + mockConfig.getEpoch();
    }

    // Helper method to extract the sequence from a generated ID
    private long extractSequence(long id) {
        return (id >> RANDOM_BITS) & ((1 << SEQUENCE_BITS) - 1);
    }

    // Helper method to extract the random part from a generated ID
    private long extractRandom(long id) {
        return id & ((1 << RANDOM_BITS) - 1);
    }
}
