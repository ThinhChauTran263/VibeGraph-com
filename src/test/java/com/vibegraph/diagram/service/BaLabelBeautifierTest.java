package com.vibegraph.diagram.service;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.vibegraph.diagram.dto.response.UmlUseCaseResponse.Actor;
import com.vibegraph.diagram.dto.response.UmlUseCaseResponse.UseCaseElement;
import com.vibegraph.diagram.service.impl.BaLabelBeautifier;

@DisplayName("BaLabelBeautifier - display-layer label rewriting")
class BaLabelBeautifierTest {

    private final BaLabelBeautifier beautifier = new BaLabelBeautifier();

    @Test
    @DisplayName("formats an owner/repo identifier into a proper system title, preserving acronyms")
    void formatsRepoIdentifier() {
        assertThat(beautifier.formatSystemName("ThinhChauTran263/SPX_Tracking"))
                .isEqualTo("SPX Tracking System");
    }

    @Test
    @DisplayName("appends 'System' to a plain project name")
    void appendsSystemToPlainName() {
        assertThat(beautifier.formatSystemName("Shop")).isEqualTo("Shop System");
    }

    @Test
    @DisplayName("does not double up when the name already ends with System")
    void noDoubleSystemSuffix() {
        assertThat(beautifier.formatSystemName("Inventory System")).isEqualTo("Inventory System");
    }

    @Test
    @DisplayName("blank or null falls back to 'System'")
    void blankFallsBack() {
        assertThat(beautifier.formatSystemName(null)).isEqualTo("System");
        assertThat(beautifier.formatSystemName("   ")).isEqualTo("System");
    }

    @Test
    @DisplayName("elevates actor display names while keeping ids and metadata stable")
    void elevatesActorNames() {
        List<Actor> in = List.of(
                Actor.builder().id("A_Guest").name("Guest").source("anonymous").confidence(0.9).build(),
                Actor.builder().id("A_User").name("User").source("default").confidence(0.7).build(),
                Actor.builder().id("A_Admin").name("Admin").source("path:/admin").confidence(0.9).build());

        List<Actor> out = beautifier.beautifyActors(in);

        assertThat(out).extracting(Actor::getName)
                .containsExactly("Guest", "Registered User", "Administrator");
        // Ids and metadata are untouched (relations reference ids, never names).
        assertThat(out).extracting(Actor::getId).containsExactly("A_Guest", "A_User", "A_Admin");
        assertThat(out.get(1).getConfidence()).isEqualTo(0.7);
    }

    @Test
    @DisplayName("maps known use-case phrases and falls back to the original label otherwise")
    void mapsUseCaseLexicon() {
        List<UseCaseElement> in = List.of(
                uc("UC_RegisterAccount", "Register account"),
                uc("UC_LogIn", "Log in"),
                uc("UC_ViewDashboards", "View Dashboards"),
                uc("UC_ManageTrackings", "Manage Trackings"),
                uc("UC_ViewStats", "View Stats"),
                uc("UC_ManageProducts", "Manage Products"));

        List<UseCaseElement> out = beautifier.beautifyUseCases(in);

        assertThat(out).extracting(UseCaseElement::getName)
                .containsExactly("Register Account", "Log In", "View Dashboard", "Manage Tracking Orders",
                        "Analyze Statistics", "Manage Products");
        // Ids preserved -> dedup and relations keep working.
        assertThat(out).extracting(UseCaseElement::getId)
                .containsExactly("UC_RegisterAccount", "UC_LogIn", "UC_ViewDashboards", "UC_ManageTrackings",
                        "UC_ViewStats", "UC_ManageProducts");
    }

    private UseCaseElement uc(String id, String name) {
        return UseCaseElement.builder().id(id).name(name).domain("d").level("business")
                .source("s").sourceEndpoint(null).confidence(0.8).build();
    }
}
