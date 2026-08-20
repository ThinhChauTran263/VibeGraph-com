package com.vibegraph.common.exception;

/**
 * Raised when the current period's credit balance cannot cover an operation.
 * Carries the required and available amounts when known, so clients can show
 * an actionable message ("requires X, you have Y") instead of a plain denial.
 */
public class InsufficientCreditsException extends RuntimeException {

    private final Long requiredCredits;
    private final Long availableCredits;

    public InsufficientCreditsException(String message) {
        this(message, null, null);
    }

    public InsufficientCreditsException(String message, Long requiredCredits, Long availableCredits) {
        super(message);
        this.requiredCredits = requiredCredits;
        this.availableCredits = availableCredits;
    }

    public String getCode() {
        return "CREDIT_EXHAUSTED";
    }

    /** Credits the operation needs; null when the exact amount is unknown (e.g. concurrent debit race). */
    public Long getRequiredCredits() {
        return requiredCredits;
    }

    /** Credits left in the current period; null when the exact amount is unknown. */
    public Long getAvailableCredits() {
        return availableCredits;
    }
}
