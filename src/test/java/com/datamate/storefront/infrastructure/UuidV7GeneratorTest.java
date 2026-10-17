package com.datamate.storefront.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class UuidV7GeneratorTest {

    private static final Instant NOW = Instant.parse("2026-10-17T10:15:30.123Z");

    @Test
    void encodesVersionVariantAndTimestamp() {
        UUID id = new UuidV7Generator(Clock.fixed(NOW, ZoneOffset.UTC)).next();

        assertThat(id.version()).isEqualTo(7);
        assertThat(id.variant()).isEqualTo(2);
        assertThat(id.getMostSignificantBits() >>> 16).isEqualTo(NOW.toEpochMilli());
    }

    @Test
    void sortsByCreationTime() {
        UUID earlier = new UuidV7Generator(Clock.fixed(NOW, ZoneOffset.UTC)).next();
        UUID later = new UuidV7Generator(Clock.fixed(NOW.plusMillis(1), ZoneOffset.UTC)).next();

        assertThat(earlier.toString()).isLessThan(later.toString());
    }

    @Test
    void staysUniqueWithinTheSameMillisecond() {
        UuidV7Generator generator = new UuidV7Generator(Clock.fixed(NOW, ZoneOffset.UTC));
        Set<UUID> ids = new HashSet<>();

        IntStream.range(0, 100_000).forEach(i -> ids.add(generator.next()));

        assertThat(ids).hasSize(100_000);
    }
}
