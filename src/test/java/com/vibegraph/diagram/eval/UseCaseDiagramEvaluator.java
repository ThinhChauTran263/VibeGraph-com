package com.vibegraph.diagram.eval;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.vibegraph.diagram.dto.response.UmlUseCaseResponse;

/**
 * Offline, deterministic accuracy evaluator for the UML Use Case model (Requirement 1).
 *
 * <p>Compares a generated {@link UmlUseCaseResponse} against a hand-labelled ground truth using
 * canonical keys, and reports precision / recall / F1 plus the concrete false-positive (spurious)
 * and false-negative (missing) elements for actors, use cases, and relations. No LLM, no network.
 *
 * <p>Canonical keys:
 * <ul>
 *   <li>actor &rarr; display name</li>
 *   <li>use case &rarr; display name</li>
 *   <li>relation &rarr; {@code type|fromName|toName} (ids resolved to display names so the key is
 *       stable across id churn)</li>
 * </ul>
 */
public final class UseCaseDiagramEvaluator {

    private UseCaseDiagramEvaluator() {
    }

    /** Per-dimension metrics with the concrete diff so failures are actionable. */
    public record Metrics(
            int truePositives,
            int falsePositives,
            int falseNegatives,
            double precision,
            double recall,
            double f1,
            List<String> spurious,
            List<String> missing) {
    }

    /** Full report across the three model dimensions. */
    public record Report(Metrics actors, Metrics useCases, Metrics relations) {
        public String pretty() {
            StringBuilder sb = new StringBuilder();
            sb.append(line("Actors", actors));
            sb.append(line("UseCases", useCases));
            sb.append(line("Relations", relations));
            return sb.toString();
        }

        private String line(String dim, Metrics m) {
            return String.format(
                    "%-10s P=%.2f R=%.2f F1=%.2f  (tp=%d fp=%d fn=%d)%n  spurious=%s%n  missing=%s%n",
                    dim, m.precision(), m.recall(), m.f1(), m.truePositives(),
                    m.falsePositives(), m.falseNegatives(), m.spurious(), m.missing());
        }
    }

    /** Hand-labelled ground truth for one project fixture. */
    public record ExpectedModel(Set<String> actors, Set<String> useCases, Set<String> relations) {
    }

    public static Report evaluate(UmlUseCaseResponse actual, ExpectedModel expected) {
        Set<String> actualActors = new LinkedHashSet<>();
        Map<String, String> nameById = new LinkedHashMap<>();
        if (actual.getActors() != null) {
            for (UmlUseCaseResponse.Actor a : actual.getActors()) {
                actualActors.add(a.getName());
                nameById.put(a.getId(), a.getName());
            }
        }
        Set<String> actualUseCases = new LinkedHashSet<>();
        if (actual.getUseCases() != null) {
            for (UmlUseCaseResponse.UseCaseElement uc : actual.getUseCases()) {
                actualUseCases.add(uc.getName());
                nameById.put(uc.getId(), uc.getName());
            }
        }
        Set<String> actualRelations = new LinkedHashSet<>();
        if (actual.getRelations() != null) {
            for (UmlUseCaseResponse.Relation r : actual.getRelations()) {
                String from = nameById.getOrDefault(r.getFrom(), r.getFrom());
                String to = nameById.getOrDefault(r.getTo(), r.getTo());
                actualRelations.add(r.getType() + "|" + from + "|" + to);
            }
        }

        return new Report(
                compare(actualActors, expected.actors()),
                compare(actualUseCases, expected.useCases()),
                compare(actualRelations, expected.relations()));
    }

    private static Metrics compare(Set<String> actual, Set<String> expected) {
        List<String> spurious = new ArrayList<>();
        for (String a : actual) {
            if (!expected.contains(a)) {
                spurious.add(a);
            }
        }
        List<String> missing = new ArrayList<>();
        for (String e : expected) {
            if (!actual.contains(e)) {
                missing.add(e);
            }
        }
        int tp = actual.size() - spurious.size();
        int fp = spurious.size();
        int fn = missing.size();
        double precision = (tp + fp) == 0 ? 1.0 : (double) tp / (tp + fp);
        double recall = (tp + fn) == 0 ? 1.0 : (double) tp / (tp + fn);
        double f1 = (precision + recall) == 0 ? 0.0 : 2 * precision * recall / (precision + recall);
        return new Metrics(tp, fp, fn, precision, recall, f1, spurious, missing);
    }
}
