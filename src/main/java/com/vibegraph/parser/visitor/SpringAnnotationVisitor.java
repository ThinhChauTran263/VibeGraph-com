package com.vibegraph.parser.visitor;

import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

/**
 * Detects Spring Boot annotations and assigns layer to Class nodes.
 *
 * Annotations detected:
 * - @RestController, @Controller → layer = "controller"
 * - @Service → layer = "service"
 * - @Repository → layer = "repository"
 * - @Component, @Configuration → layer = "component" / "config"
 * - @Entity → layer = "entity"
 *
 * Method annotations:
 * - @GetMapping, @PostMapping, @PutMapping, @DeleteMapping → Route
 * - @Scheduled → System actor
 * - @KafkaListener → Message Queue actor
 *
 * TODO: Implement annotation detection
 */
public class SpringAnnotationVisitor extends VoidVisitorAdapter<Object> {

    @Override
    public void visit(ClassOrInterfaceDeclaration n, Object arg) {
        // TODO: Detect Spring annotations
        super.visit(n, arg);
    }
}
