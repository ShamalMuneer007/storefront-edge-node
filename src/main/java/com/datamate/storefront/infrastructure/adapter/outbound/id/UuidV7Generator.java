package com.datamate.storefront.infrastructure.adapter.outbound.id;

import com.datamate.storefront.application.port.outbound.IdGenerator;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.UUID;

/** RFC 9562 version 7: 48-bit Unix epoch millis followed by 74 random bits. */
public final class UuidV7Generator implements IdGenerator {

    private final SecureRandom random = new SecureRandom();
    private final Clock clock;

    public UuidV7Generator(Clock clock) {
        this.clock = clock;
    }

    @Override
    public UUID next() {
        long mostSignificant = (clock.millis() << 16) | 0x7000L | (random.nextInt() & 0x0FFFL);
        long leastSignificant = (random.nextLong() & 0x3FFF_FFFF_FFFF_FFFFL) | 0x8000_0000_0000_0000L;
        return new UUID(mostSignificant, leastSignificant);
    }
}
