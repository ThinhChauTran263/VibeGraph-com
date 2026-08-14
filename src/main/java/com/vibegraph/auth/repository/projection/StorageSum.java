package com.vibegraph.auth.repository.projection;

import java.util.UUID;

/**
 * Batch projection: total tracked storage per owner (H9). Lets the admin user listing sum
 * storage for a whole page in one GROUP BY query instead of one SUM per user.
 */
public interface StorageSum {

    UUID getOwnerId();

    Long getTotal();
}
