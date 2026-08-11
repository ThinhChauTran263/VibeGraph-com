package com.vibegraph.parser.flow;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.vibegraph.parser.node.EdgeData;
import com.vibegraph.parser.node.NodeData;

@DisplayName("DynamicDispatchResolver")
class DynamicDispatchResolverTest {

    @Test
    @DisplayName("emits RESOLVES_TO only when one concrete implementation method matches")
    void emitsResolvesToForSingleCandidate() {
        List<NodeData> nodes = List.of(
                node("Interface", "PaymentPort", "com.example.PaymentPort"),
                node("Method", "charge", "com.example.PaymentPort.charge(Order)"),
                node("Class", "StripePayment", "com.example.StripePayment"),
                node("Method", "charge", "com.example.StripePayment.charge(Order)"),
                node("Class", "CheckoutService", "com.example.CheckoutService"),
                node("Method", "checkout", "com.example.CheckoutService.checkout(Order)"));
        List<EdgeData> edges = List.of(
                EdgeData.of("IMPLEMENTS", "com.example.StripePayment", "com.example.PaymentPort"),
                EdgeData.of("CALLS", "com.example.CheckoutService.checkout(Order)",
                        "com.example.PaymentPort.charge(Order)"));

        List<EdgeData> inferred = DynamicDispatchResolver.inferDispatch(nodes, edges);

        assertThat(inferred)
                .singleElement()
                .satisfies(edge -> {
                    assertThat(edge.type()).isEqualTo("RESOLVES_TO");
                    assertThat(edge.sourceFullName()).isEqualTo("com.example.PaymentPort.charge(Order)");
                    assertThat(edge.targetFullName()).isEqualTo("com.example.StripePayment.charge(Order)");
                    assertThat(edge.properties())
                            .containsEntry("inferred", true)
                            .containsEntry("ambiguous", false)
                            .containsEntry("confidence", 1.0);
                });
    }

    @Test
    @DisplayName("ambiguous interface dispatch emits only DISPATCH_CANDIDATES")
    void ambiguousDispatchDoesNotEmitResolvesTo() {
        List<NodeData> nodes = List.of(
                node("Interface", "PaymentPort", "com.example.PaymentPort"),
                node("Method", "charge", "com.example.PaymentPort.charge(Order)"),
                node("Class", "StripePayment", "com.example.StripePayment"),
                node("Method", "charge", "com.example.StripePayment.charge(Order)"),
                node("Class", "PaypalPayment", "com.example.PaypalPayment"),
                node("Method", "charge", "com.example.PaypalPayment.charge(Order)"),
                node("Class", "CashPayment", "com.example.CashPayment"),
                node("Method", "charge", "com.example.CashPayment.charge(Order)"),
                node("Method", "checkout", "com.example.CheckoutService.checkout(Order)"));
        List<EdgeData> edges = List.of(
                EdgeData.of("IMPLEMENTS", "com.example.StripePayment", "com.example.PaymentPort"),
                EdgeData.of("IMPLEMENTS", "com.example.PaypalPayment", "com.example.PaymentPort"),
                EdgeData.of("IMPLEMENTS", "com.example.CashPayment", "com.example.PaymentPort"),
                EdgeData.of("CALLS", "com.example.CheckoutService.checkout(Order)",
                        "com.example.PaymentPort.charge(Order)"));

        List<EdgeData> inferred = DynamicDispatchResolver.inferDispatch(nodes, edges);

        assertThat(inferred).noneMatch(edge -> "RESOLVES_TO".equals(edge.type()));
        assertThat(inferred)
                .filteredOn(edge -> "DISPATCH_CANDIDATES".equals(edge.type()))
                .hasSize(3)
                .allSatisfy(edge -> assertThat(edge.properties())
                        .containsEntry("inferred", true)
                        .containsEntry("ambiguous", true)
                        .containsEntry("reason", "AMBIGUOUS_INTERFACE_DISPATCH"));
    }

    private NodeData node(String type, String name, String fullName) {
        return NodeData.of(type, name, fullName, "Example.java", 1, 1, Map.of());
    }
}
