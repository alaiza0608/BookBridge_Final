package model;

public abstract class Member {
    private final String memberId;
    private final String name;

    public Member(String memberId, String name) {
        if (memberId == null || memberId.trim().isEmpty()) throw new IllegalArgumentException("Member ID cannot be blank.");
        if (name == null || name.trim().isEmpty()) throw new IllegalArgumentException("Member name cannot be blank.");
        this.memberId = memberId.trim();
        this.name = name.trim();
    }

    public String getMemberId() { return memberId; }
    public String getName() { return name; }
    public abstract MembershipType getMembershipType();
    public int getMaximumLoanDays() { return getMembershipType().getMaxLoanDays(); }
    public double getDailyFine() { return getMembershipType().getDailyFine(); }

    public boolean canBorrowFor(int requestedDays) {
        return requestedDays >= 1 && requestedDays <= getMaximumLoanDays();
    }

    public double estimateLateFee(int lateDays) {
        if (lateDays < 0) throw new IllegalArgumentException("Late days cannot be negative.");
        return lateDays * getDailyFine();
    }

    @Override
    public String toString() {
        return name + " (" + memberId + " - " + getMembershipType() + ")";
    }
}