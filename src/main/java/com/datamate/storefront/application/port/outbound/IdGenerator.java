package com.datamate.storefront.application.port.outbound;

import java.util.UUID;

public interface IdGenerator {

    UUID next();
}
