package com.datamate.storefront.application.port;

import java.util.UUID;

public interface IdGenerator {

    UUID next();
}
