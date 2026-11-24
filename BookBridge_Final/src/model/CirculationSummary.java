package model;

import java.util.Map;

public record CirculationSummary(
    int totalCatalogTitles,
    int totalAvailableCopies,
    int activeLoansCount,
    int activeReservationsCount,
    Map<MembershipType, Long> activeLoansByMemberType,
    double totalEstimatedFines
) {}