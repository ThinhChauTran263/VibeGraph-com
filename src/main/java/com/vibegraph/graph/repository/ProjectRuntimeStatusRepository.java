package com.vibegraph.graph.repository;

import com.vibegraph.graph.websocket.ProjectStatusEvent;

public interface ProjectRuntimeStatusRepository {

    void upsert(ProjectStatusEvent event);
}
