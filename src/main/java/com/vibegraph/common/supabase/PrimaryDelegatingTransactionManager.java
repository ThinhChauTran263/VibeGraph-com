package com.vibegraph.common.supabase;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.TransactionStatus;

/**
 * Routes {@code supabaseTransactionManager} transactions to the primary application
 * transaction manager.
 *
 * <p>When Supabase is disabled the Supabase JDBC repositories run against the primary
 * {@code DataSource}. Driving them through a second {@code DataSourceTransactionManager}
 * would bind a competing {@code ConnectionHolder} on that same {@code DataSource}, so a
 * service mixing a Supabase repository with a JPA repository could end up with two
 * independent transactions or a resource-binding conflict. Resolving the Supabase
 * transaction manager name to the very same manager JPA already uses keeps both halves
 * of such a flow in one transaction.
 *
 * <p>Resolution is deferred to the first transactional call so a context without JPA
 * (for example a slice test that never opens a transaction) still starts.
 */
final class PrimaryDelegatingTransactionManager implements PlatformTransactionManager {

    private final ObjectProvider<PlatformTransactionManager> primaryProvider;
    private volatile PlatformTransactionManager delegate;

    PrimaryDelegatingTransactionManager(ObjectProvider<PlatformTransactionManager> primaryProvider) {
        this.primaryProvider = primaryProvider;
    }

    @Override
    public TransactionStatus getTransaction(TransactionDefinition definition) throws TransactionException {
        return delegate().getTransaction(definition);
    }

    @Override
    public void commit(TransactionStatus status) throws TransactionException {
        delegate().commit(status);
    }

    @Override
    public void rollback(TransactionStatus status) throws TransactionException {
        delegate().rollback(status);
    }

    PlatformTransactionManager delegate() {
        PlatformTransactionManager current = this.delegate;
        if (current != null) {
            return current;
        }
        current = primaryProvider.getIfUnique();
        if (current == null || current == this) {
            throw new IllegalStateException(
                    "Supabase is disabled but no unique primary PlatformTransactionManager is available; "
                            + "Supabase repositories cannot join the primary transaction");
        }
        this.delegate = current;
        return current;
    }
}
