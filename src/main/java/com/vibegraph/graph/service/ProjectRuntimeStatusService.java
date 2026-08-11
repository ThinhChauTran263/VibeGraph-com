package com.vibegraph.graph.service;

import org.springframework.stereotype.Service;

import com.vibegraph.graph.repository.ProjectRuntimeStatusRepository;
import com.vibegraph.graph.websocket.ProjectStatusEvent;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProjectRuntimeStatusService {

    private final ProjectRuntimeStatusRepository repository;

    public void record(ProjectStatusEvent event) {
        try {
            repository.upsert(event);
        } catch (RuntimeException ex) {
            log.warn("Could not persist project runtime status: project={} status={}",
                    event.projectId(), event.status(), ex);
        }
    }
}
