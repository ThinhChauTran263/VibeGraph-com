package com.vibegraph.diagram.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * B-M2 gate work (cheap cluster): behaviour tests for the pure string/domain/actor helpers inside
 * {@link UseCaseInferenceEngine}. Every assertion pins an input → output semantic documented in
 * the engine's own javadoc — this protects the heuristics before the class is split, it does not
 * merely burn jacoco branches. Reflection is used only to reach private pure functions and the
 * private {@code Endpoint}/{@code DomainGuess} records; the assertions themselves are behavioural.
 */
class UseCaseInferenceEngineHelperTest {

    private final UseCaseInferenceEngine engine = new UseCaseInferenceEngine();

    // ---- reflection plumbing (post B-M2 split: Endpoint lives in UseCaseEndpointRules,
    //      string helpers in UseCaseNameNormalizer; the engine keeps thin delegators) --------------

    private static Object endpoint(String method, String path, String controller, String role) {
        // Same package as the record, so no reflection is needed to construct it.
        return new UseCaseEndpointRules.Endpoint(
                "route-" + path, method, path, controller, controller, role);
    }

    private static Object domainGuess(UseCaseInferenceEngine engine, Object ep) throws Exception {
        Method m = UseCaseInferenceEngine.class.getDeclaredMethod(
                "inferDomainGuess", UseCaseEndpointRules.Endpoint.class);
        m.setAccessible(true);
        return m.invoke(engine, ep);
    }

    private static String guessName(Object guess) throws Exception {
        return (String) guess.getClass().getMethod("name").invoke(guess);
    }

    private static double guessConfidence(Object guess) throws Exception {
        return (Double) guess.getClass().getMethod("confidence").invoke(guess);
    }

    private static String guessSource(Object guess) throws Exception {
        return (String) guess.getClass().getMethod("source").invoke(guess);
    }

    private static Object inferActor(UseCaseInferenceEngine engine, Object ep) throws Exception {
        Method m = UseCaseInferenceEngine.class.getDeclaredMethod(
                "inferActor", UseCaseEndpointRules.Endpoint.class);
        m.setAccessible(true);
        return m.invoke(engine, ep);
    }

    private static String actorField(Object actorGuess, String component) throws Exception {
        return (String) actorGuess.getClass().getMethod(component).invoke(actorGuess);
    }

    private static String invokeStr(String method, String arg) throws Exception {
        Method m = UseCaseNameNormalizer.class.getDeclaredMethod(method, String.class);
        m.setAccessible(true);
        return (String) m.invoke(null, arg);
    }

    private static String invokeStr(String method, String arg, Set<String> used) throws Exception {
        Method m = UseCaseNameNormalizer.class.getDeclaredMethod(method, String.class, Set.class);
        m.setAccessible(true);
        return (String) m.invoke(null, arg, used);
    }

    // ---- singularize ----------------------------------------------------------------------------

    @Nested
    @DisplayName("singularize: documented English suffix rules")
    class Singularize {

        @Test
        void iesBecomesY() throws Exception {
            assertThat(invokeStr("singularize", "Categories")).isEqualTo("Category");
        }

        @Test
        void threeLetterIesWordFallsToPlainSRule() throws Exception {
            // the ies rule requires length > 3, so "ies" itself hits the plain-s rule: "ie".
            // Pins the real behaviour, not ideal English.
            assertThat(invokeStr("singularize", "ies")).isEqualTo("ie");
        }

        @Test
        void sibilantPluralsDropEs() throws Exception {
            assertThat(invokeStr("singularize", "Boxes")).isEqualTo("Box");
            assertThat(invokeStr("singularize", "Classes")).isEqualTo("Class");
            assertThat(invokeStr("singularize", "Churches")).isEqualTo("Church");
            assertThat(invokeStr("singularize", "Dishes")).isEqualTo("Dish");
            // double-z is NOT simplified: "Quizzes" drops only "es" -> "Quizz" (real behaviour)
            assertThat(invokeStr("singularize", "Quizzes")).isEqualTo("Quizz");
        }

        @Test
        void plainSDropsOneLetterButDoubleSStays() throws Exception {
            assertThat(invokeStr("singularize", "Products")).isEqualTo("Product");
            assertThat(invokeStr("singularize", "Class")).isEqualTo("Class");
        }

        @Test
        void singleLetterWordStays() throws Exception {
            assertThat(invokeStr("singularize", "S")).isEqualTo("S");
        }
    }

    // ---- pluralizeWord / pluralName --------------------------------------------------------------

    @Nested
    @DisplayName("pluralize: inverse rules, casing preserved on multi-word domains")
    class Pluralize {

        @Test
        void consonantYBecomesIes() throws Exception {
            assertThat(invokeStr("pluralizeWord", "Category")).isEqualTo("Categories");
        }

        @Test
        void vowelYJustAddsS() throws Exception {
            assertThat(invokeStr("pluralizeWord", "Day")).isEqualTo("Days");
        }

        @Test
        void sibilantEndingsAddEs() throws Exception {
            assertThat(invokeStr("pluralizeWord", "Box")).isEqualTo("Boxes");
            assertThat(invokeStr("pluralizeWord", "Church")).isEqualTo("Churches");
            assertThat(invokeStr("pluralizeWord", "Dish")).isEqualTo("Dishes");
            assertThat(invokeStr("pluralizeWord", "Bus")).isEqualTo("Buses");
        }

        @Test
        void plainNounAddsS() throws Exception {
            assertThat(invokeStr("pluralizeWord", "Product")).isEqualTo("Products");
        }

        @Test
        void nullAndEmptyPassThrough() throws Exception {
            assertThat(invokeStr("pluralizeWord", "")).isEmpty();
            assertThat(invokeStr("pluralizeWord", null)).isNull();
        }

        @Test
        void pluralNameOnlyPluralizesTheLastWordAndCapitalizes() throws Exception {
            assertThat(invokeStr("pluralName", "Order Item")).isEqualTo("Order Items");
            assertThat(invokeStr("pluralName", "category")).isEqualTo("Categories");
        }

        @Test
        void pluralNameBlankPassesThrough() throws Exception {
            assertThat(invokeStr("pluralName", "")).isEmpty();
        }
    }

    // ---- camel / capitalize / pascal --------------------------------------------------------------

    @Nested
    @DisplayName("splitCamel / capitalize / pascal: identifier shaping")
    class IdentifierShaping {

        @Test
        void splitCamelInsertsSpaceOnLowerUpperBoundary() throws Exception {
            assertThat(invokeStr("splitCamel", "OrderService")).isEqualTo("Order Service");
            assertThat(invokeStr("splitCamel", "order")).isEqualTo("order");
        }

        @Test
        void capitalizeNullEmptyAndNormal() throws Exception {
            assertThat(invokeStr("capitalize", null)).isNull();
            assertThat(invokeStr("capitalize", "")).isEmpty();
            assertThat(invokeStr("capitalize", "abc")).isEqualTo("Abc");
        }

        @Test
        void pascalJoinsTokensAndGuardsIdentifiers() throws Exception {
            assertThat(invokeStr("pascal", "order-item")).isEqualTo("OrderItem");
            assertThat(invokeStr("pascal", null)).isEqualTo("X");
            assertThat(invokeStr("pascal", "   ")).isEqualTo("X");
            assertThat(invokeStr("pascal", "---")).isEqualTo("X");
            // digit-leading output gets the X guard so it stays a valid identifier
            assertThat(invokeStr("pascal", "123abc")).isEqualTo("X123abc");
        }
    }

    // ---- uniqueId --------------------------------------------------------------------------------

    @Nested
    @DisplayName("uniqueId: deterministic collision suffixes")
    class UniqueId {

        @Test
        void firstUseKeepsBaseName() throws Exception {
            Set<String> used = new HashSet<>();
            assertThat(invokeStr("uniqueId", "UC_Order", used)).isEqualTo("UC_Order");
            assertThat(used).containsExactly("UC_Order");
        }

        @Test
        void collisionsGetIncrementingSuffixes() throws Exception {
            Set<String> used = new HashSet<>();
            assertThat(invokeStr("uniqueId", "UC_Order", used)).isEqualTo("UC_Order");
            assertThat(invokeStr("uniqueId", "UC_Order", used)).isEqualTo("UC_Order_2");
            assertThat(invokeStr("uniqueId", "UC_Order", used)).isEqualTo("UC_Order_3");
        }
    }

    // ---- inferDomainGuess -------------------------------------------------------------------------

    // NOTE: Endpoint.controller receives the CONTROLLER_NAME-stripped simple name
    // (controllerName(): "ProductController" -> "Product"), so inputs below are bare names.

    @Nested
    @DisplayName("inferDomainGuess: evidence-based confidence levels (R3)")
    class InferDomainGuess {

        @Test
        void controllerDerivedDomainIsStrongConfidence() throws Exception {
            Object guess = domainGuess(engine, endpoint("GET", "/whatever", "Product", null));
            assertThat(guessName(guess)).isEqualTo("Product");
            assertThat(guessConfidence(guess)).isEqualTo(0.9);
            assertThat(guessSource(guess)).isEqualTo("controller");
        }

        @Test
        void leadingRoleWordsAreStrippedFromControllerDomain() throws Exception {
            Object guess = domainGuess(engine, endpoint("GET", "/whatever", "AdminProduct", null));
            assertThat(guessName(guess)).isEqualTo("Product");
        }

        @Test
        void trailingTechWordsAreStrippedFromControllerDomain() throws Exception {
            Object guess = domainGuess(engine, endpoint("GET", "/whatever", "ProductRest", null));
            assertThat(guessName(guess)).isEqualTo("Product");
            Object guess2 = domainGuess(engine, endpoint("GET", "/whatever", "UserApi", null));
            assertThat(guessName(guess2)).isEqualTo("User");
        }

        @Test
        void purelyTechnicalControllerFallsBackToPath() throws Exception {
            // "ApiRest" strips down to the tech word "Api", which is rejected as a business domain,
            // so inference falls through to the path segments.
            Object guess = domainGuess(engine, endpoint("GET", "/api/orders", "ApiRest", null));
            assertThat(guessName(guess)).isEqualTo("Order");
            assertThat(guessSource(guess)).isEqualTo("path");
            assertThat(guessConfidence(guess)).isEqualTo(0.6);
        }

        @Test
        void pathFallbackSkipsApiVersionAdminAuthAndPathVars() throws Exception {
            Object guess = domainGuess(engine,
                    endpoint("GET", "/api/v1/admin/auth/orders/{id}", null, null));
            assertThat(guessName(guess)).isEqualTo("Order");
            assertThat(guessConfidence(guess)).isEqualTo(0.6);
        }

        @Test
        void noSignalAtAllYieldsWeakResourceDefault() throws Exception {
            Object guess = domainGuess(engine, endpoint("GET", "/", null, null));
            assertThat(guessName(guess)).isEqualTo("Resource");
            assertThat(guessConfidence(guess)).isEqualTo(0.3);
            assertThat(guessSource(guess)).isEqualTo("fallback");
        }

        @Test
        void multiWordControllerDomainRejoinsWithoutTechTail() throws Exception {
            // "OrderManagementApi" -> "Order Management Api" -> tech tail dropped -> "Order Management"
            Object guess = domainGuess(engine, endpoint("GET", "/whatever", "OrderManagementApi", null));
            assertThat(guessName(guess)).isEqualTo("Order Management");
        }

        @Test
        void inferDomainWrapperDelegatesToGuessName() throws Exception {
            // pins the package-private wrapper used by callers that only want the name
            Method m = UseCaseInferenceEngine.class.getDeclaredMethod(
                    "inferDomain", UseCaseEndpointRules.Endpoint.class);
            m.setAccessible(true);
            Object ep = endpoint("GET", "/api/orders", null, null);
            assertThat((String) m.invoke(engine, ep)).isEqualTo("Order");
        }
    }

    // ---- inferActor --------------------------------------------------------------------------------

    @Nested
    @DisplayName("inferActor: security facts beat path heuristics beat the default")
    class InferActor {

        @Test
        void adminLikeRolesCollapseToAdminActor() throws Exception {
            for (String role : new String[]{"ADMIN", "SUPERUSER", "ROLE_ROOT"}) {
                Object actor = inferActor(engine, endpoint("GET", "/x", null, role));
                assertThat(actorField(actor, "name")).as("role %s", role).isEqualTo("Admin");
                // a mined security role is a fact, never flagged as a heuristic guess
                assertThat(actorField(actor, "source")).as("role %s", role).startsWith("security:");
            }
        }

        @Test
        void genericAuthenticatedRolesCollapseToUserActor() throws Exception {
            for (String role : new String[]{"ROLE_USER", "USERS", "MEMBER", "AUTHENTICATED", "ROLE_"}) {
                Object actor = inferActor(engine, endpoint("GET", "/x", null, role));
                assertThat(actorField(actor, "name")).as("role %s", role).isEqualTo("User");
            }
        }

        @Test
        void namedBusinessRoleBecomesItsOwnActor() throws Exception {
            Object actor = inferActor(engine, endpoint("GET", "/x", null, "ROLE_STORE_MANAGER"));
            assertThat(actorField(actor, "name")).isEqualTo("Store Manager");
            Object seller = inferActor(engine, endpoint("GET", "/x", null, "SELLER"));
            assertThat(actorField(seller, "name")).isEqualTo("Seller");
        }

        @Test
        void roleTokenThatBlanksOutFallsBackToUserActorName() throws Exception {
            // "___" is not admin-like and not a generic auth role, so it reaches roleToActorName,
            // where underscores blank the token out -> the User actor name is the fallback.
            Object actor = inferActor(engine, endpoint("GET", "/x", null, "___"));
            assertThat(actorField(actor, "name")).isEqualTo("User");
        }

        @Test
        void adminPathWithoutSecurityAnnotationIsPathHeuristic() throws Exception {
            Object actor = inferActor(engine, endpoint("GET", "/api/admin/orders", null, null));
            assertThat(actorField(actor, "name")).isEqualTo("Admin");
            assertThat(actorField(actor, "source")).isEqualTo("path:/admin");
        }

        @Test
        void defaultIsAuthenticatedUserFlaggedAsGuess() throws Exception {
            Object actor = inferActor(engine, endpoint("GET", "/api/orders", null, null));
            assertThat(actorField(actor, "name")).isEqualTo("User");
            assertThat(actorField(actor, "source")).isEqualTo("default-authenticated");
        }
    }
}
