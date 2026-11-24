package model;

public interface Renewable {
    boolean canRenew();
    void renewLoan(int extraDays);
}