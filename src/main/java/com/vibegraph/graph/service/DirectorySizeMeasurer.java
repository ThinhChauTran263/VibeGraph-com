package com.vibegraph.graph.service;

import java.nio.file.Path;

public interface DirectorySizeMeasurer {

    long measureBytes(Path root);
}
