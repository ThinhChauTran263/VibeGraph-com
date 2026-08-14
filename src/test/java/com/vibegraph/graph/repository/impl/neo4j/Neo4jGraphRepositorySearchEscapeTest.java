package com.vibegraph.graph.repository.impl.neo4j;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * S-M4: raw user input must never reach the Lucene fulltext parser unescaped — special
 * characters would be read as operators and malformed input failed with a 500 before.
 */
class Neo4jGraphRepositorySearchEscapeTest {

    @Test
    @DisplayName("escapeLucene leaves plain identifiers untouched")
    void plainInputPassesThrough() {
        assertThat(Neo4jGraphRepository.escapeLucene("UserService")).isEqualTo("UserService");
        assertThat(Neo4jGraphRepository.escapeLucene("")).isEmpty();
    }

    @Test
    @DisplayName("escapeLucene neutralizes every Lucene operator character")
    void specialCharactersAreEscaped() {
        assertThat(Neo4jGraphRepository.escapeLucene("a && b || !c"))
                .isEqualTo("a \\&\\& b \\|\\| \\!c");
        assertThat(Neo4jGraphRepository.escapeLucene("User*?"))
                .isEqualTo("User\\*\\?");
        assertThat(Neo4jGraphRepository.escapeLucene("\"quoted\" (group) [range] {curly} ^~ /path\\ :colon"))
                .isEqualTo("\\\"quoted\\\" \\(group\\) \\[range\\] \\{curly\\} \\^\\~ \\/path\\\\ \\:colon");
    }

    @Test
    @DisplayName("escapeLucene output contains no unescaped special characters")
    void escapedOutputIsOperatorFree() {
        String escaped = Neo4jGraphRepository.escapeLucene("+-!(){}[]^\"~*?:/\\&|");
        // Strip every \X escape pair; nothing special may survive.
        String withoutEscapes = escaped.replaceAll("\\\\.", "");
        for (char c : withoutEscapes.toCharArray()) {
            assertThat("+-!(){}[]^\"~*?:/\\&|".indexOf(c))
                    .as("character '%s' must have been escaped", c)
                    .isEqualTo(-1);
        }
    }
}
