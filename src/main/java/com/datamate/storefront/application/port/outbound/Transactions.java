package com.datamate.storefront.application.port.outbound;

import java.util.function.Supplier;

/**
 * Runs {@code work} as one atomic unit: commits when it returns normally; on any
 * RuntimeException rolls back everything done inside and rethrows that same exception.
 * Calls must not be nested.
 */
public interface Transactions {

    <T> T inTransaction(Supplier<T> work);
}
