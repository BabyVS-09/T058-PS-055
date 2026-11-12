package com.ats.model;

import java.math.BigDecimal;

/**
 * Encapsulates the complete breakdown of an offer calculation (BR-15 through BR-23).
 */
public record OfferCalculationResult(
        BigDecimal expectedCtc,
        BigDecimal offeredCtc,
        BigDecimal joiningBonus,
        BigDecimal buyoutReimbursement,
        BigDecimal applicableFee,
        BigDecimal totalCost,
        boolean outOfBand,
        boolean requiresVpApproval,
        boolean requiresCfoApproval,
        BigDecimal outOfBandThreshold,
        BigDecimal cfoThreshold
) {
}
