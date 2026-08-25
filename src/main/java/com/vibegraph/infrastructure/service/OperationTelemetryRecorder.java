package com.vibegraph.infrastructure.service;

import java.util.List;

import com.vibegraph.common.exception.ServiceBusyException;
import com.vibegraph.infrastructure.dto.InfrastructureSnapshot.OperationEvidence;
import com.vibegraph.infrastructure.dto.InfrastructureSnapshot;

public interface OperationTelemetryRecorder {

    OperationToken begin(String type, String operation, String projectId, String projectName);

    void complete(OperationToken token, int nodes, int edges, long storageAddedBytes);

    void fail(OperationToken token, Throwable error);

    void stop(OperationToken token, String reason);

    /** Associates an accepted operation with its durable project identity once it exists. */
    default void attach(OperationToken token, String projectId, String projectName) {
    }

    default void observe(InfrastructureSnapshot snapshot) {
    }

    List<OperationEvidence> recent(int limit, String type);

    static void requireAccepted(OperationToken token) {
        if (token != null && token.terminal()) {
            throw new ServiceBusyException("Server is busy processing other heavy operations. Please retry shortly.");
        }
    }

    /**
     * Identifies one terminally-recorded operation. A rejected token is terminal immediately,
     * which keeps callers from silently losing an evidence row when the bounded monitor is full.
     */
    record OperationToken(String id, boolean terminal) {

        public OperationToken(String id) {
            this(id, false);
        }

        public static OperationToken rejected(String id) {
            return new OperationToken(id, true);
        }

    }
}
