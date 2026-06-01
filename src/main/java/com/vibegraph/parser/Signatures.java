package com.vibegraph.parser;

import java.util.List;

public final class Signatures {

    private Signatures() {
    }

    public static String method(String ownerFqcn, String methodName, List<String> paramTypes) {
        return ownerFqcn + "." + methodName + "(" + String.join(",", paramTypes) + ")";
    }
}
