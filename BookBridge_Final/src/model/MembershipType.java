package model;

public enum MembershipType {
    STUDENT(14, 5.0),
    FACULTY(30, 2.0),
    GUEST(7, 10.0);

    private final int maxLoanDays;
    private final double dailyFine;

    MembershipType(int maxLoanDays, double dailyFine) {
        this.maxLoanDays = maxLoanDays;
        this.dailyFine = dailyFine;
    }

    public int getMaxLoanDays() { return maxLoanDays; }
    public double getDailyFine() { return dailyFine; }
}