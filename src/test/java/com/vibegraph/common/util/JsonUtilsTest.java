package com.vibegraph.common.util;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for JsonUtils - JSON serialization helpers.
 *
 * Run: mvn test -Dtest=JsonUtilsTest
 */
@DisplayName("JsonUtils")
@Disabled("Chờ JsonUtils implement toJson()/fromJson() (Jackson ObjectMapper)")
class JsonUtilsTest {

    record SampleDto(String name, int age, List<String> tags) {}

    @Test
    @DisplayName("should serialize record to JSON")
    void shouldSerializeRecordToJson() {
        // Arrange
        SampleDto dto = new SampleDto("Alice", 30, List.of("admin", "user"));

        // Act
        String json = JsonUtils.toJson(dto);

        // Assert
        assertNotNull(json);
        assertTrue(json.contains("\"name\":\"Alice\""));
        assertTrue(json.contains("\"age\":30"));
        assertTrue(json.contains("\"tags\""));
    }

    @Test
    @DisplayName("should deserialize JSON to record")
    void shouldDeserializeJsonToRecord() {
        // Arrange
        String json = "{\"name\":\"Bob\",\"age\":25,\"tags\":[\"dev\"]}";

        // Act
        SampleDto dto = JsonUtils.fromJson(json, SampleDto.class);

        // Assert
        assertEquals("Bob", dto.name());
        assertEquals(25, dto.age());
        assertEquals(List.of("dev"), dto.tags());
    }

    @Test
    @DisplayName("should serialize map")
    void shouldSerializeMap() {
        // Arrange
        Map<String, Object> map = Map.of("key1", "value1", "count", 42);

        // Act
        String json = JsonUtils.toJson(map);

        // Assert
        assertTrue(json.contains("\"key1\":\"value1\""));
        assertTrue(json.contains("\"count\":42"));
    }

    @Test
    @DisplayName("should round-trip preserve data")
    void shouldRoundTripPreserveData() {
        // Arrange
        SampleDto original = new SampleDto("Charlie", 40, List.of("a", "b", "c"));

        // Act
        String json = JsonUtils.toJson(original);
        SampleDto restored = JsonUtils.fromJson(json, SampleDto.class);

        // Assert
        assertEquals(original, restored);
    }

    @Test
    @DisplayName("should throw on invalid JSON")
    void shouldThrowOnInvalidJson() {
        // Act & Assert
        assertThrows(RuntimeException.class,
            () -> JsonUtils.fromJson("{invalid json", SampleDto.class));
    }
}
