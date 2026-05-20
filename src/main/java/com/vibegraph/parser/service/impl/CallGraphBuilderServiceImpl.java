package com.vibegraph.parser.service.impl;

import com.vibegraph.parser.service.CallGraphBuilderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Call graph builder implementation.
 *
 * TODO:
 * - Walk MethodDeclaration → find MethodCallExpr inside
 * - Resolve target method via SymbolResolver
 * - Create CALLS edge with confidence (1.0 if resolved, 0.5 if guessed)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CallGraphBuilderServiceImpl implements CallGraphBuilderService {
    // TODO: Implement
}
