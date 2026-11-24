package model;

import java.time.LocalDate;

public class LoanRecord implements Renewable {
    private final String loanId;
    private final Member member;
    private final LibraryBook book;
    private int requestedDays;
    private final LocalDate issueDate;
    private LocalDate dueDate;
    private final int lateDays;
    private final LoanStatus status;
    private boolean renewed;

    public LoanRecord(String loanId, Member member, LibraryBook book, int requestedDays,
                      LocalDate issueDate, LocalDate dueDate, int lateDays, LoanStatus status) {
        this.loanId = loanId;
        this.member = member;
        this.book = book;
        this.requestedDays = requestedDays;
        this.issueDate = issueDate;
        this.dueDate = dueDate;
        this.lateDays = lateDays;
        this.status = status;
        this.renewed = false;
    }

    public String getLoanId() { return loanId; }
    public Member getMember() { return member; }
    public LibraryBook getBook() { return book; }
    public int getRequestedDays() { return requestedDays; }
    public LocalDate getIssueDate() { return issueDate; }
    public LocalDate getDueDate() { return dueDate; }
    public int getLateDays() { return lateDays; }
    public LoanStatus getStatus() { return status; }
    public boolean isOverdue() { return status == LoanStatus.APPROVED && lateDays > 0; }
    public double calculateEstimatedCharge() { return member.estimateLateFee(lateDays); }

    @Override
    public boolean canRenew() {
        return status == LoanStatus.APPROVED && !renewed && lateDays == 0;
    }

    @Override
    public void renewLoan(int extraDays) {
        if (!canRenew()) throw new IllegalStateException("Loan " + loanId + " cannot be renewed.");
        if (extraDays < 1 || (requestedDays + extraDays) > member.getMaximumLoanDays()) {
            throw new IllegalArgumentException("Renewal exceeds maximum allowed days.");
        }
        this.requestedDays += extraDays;
        this.dueDate = this.dueDate.plusDays(extraDays);
        this.renewed = true;
    }
}