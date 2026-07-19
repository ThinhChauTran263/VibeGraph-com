# Module: diagram

## Purpose

The diagram module generates architecture views from the project knowledge graph.
The supported product surface is the as-built UML use-case view.

Only the UML use-case diagram is supported. Do not add UI, controllers, or
services for additional diagram types unless the feature is explicitly
reintroduced.

## Current Structure

```text
diagram/
  controller/
    DiagramController.java        - GET /api/projects/{id}/diagrams/usecase
  service/
    UseCaseDiagramService.java    - use-case generation contract
    impl/
      UseCaseDiagramServiceImpl.java
      UseCaseInferenceEngine.java
      BaLabelBeautifier.java
      UmlUseCaseRenderer.java
      UseCaseViewProjector.java
  dto/
    response/
      UmlUseCaseResponse.java
```

## Endpoint

- `GET /api/projects/{id}/diagrams/usecase?style=uml&mode=detailed|grouped`

The controller validates project ownership, feature availability, and analyzed
project status before generating the diagram.

## Frontend Rendering

The backend returns the inferred UML model plus PlantUML/Mermaid fallback text.
The web app renders the primary diagram as SVG via:

```text
vibegraph-web/src/lib/umlUseCaseSvg.ts
```

## Acceptance

- Use-case diagram reflects source-derived actors, use cases, and relations.
- Project not found returns `PROJECT_NOT_FOUND`.
- Project not analyzed returns `PROJECT_NOT_ANALYZED`.
- Feature disabled returns `FEATURE_DISABLED`.
- The web diagram panel does not expose controls for additional diagram types.
