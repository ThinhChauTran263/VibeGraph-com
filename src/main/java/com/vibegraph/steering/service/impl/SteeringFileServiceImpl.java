package com.vibegraph.steering.service.impl;

import com.vibegraph.steering.service.SteeringFileService;
import com.vibegraph.steering.writer.SteeringWriter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class SteeringFileServiceImpl implements SteeringFileService {

    private final List<SteeringWriter> writers;

    // TODO: Implement
}
