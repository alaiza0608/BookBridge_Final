package model;

public class Reservation {
    private final String reservationId;
    private final Member member;
    private final LibraryBook book;
    private final String status;

    public Reservation(String reservationId, Member member, LibraryBook book, String status) {
        this.reservationId = reservationId;
        this.member = member;
        this.book = book;
        this.status = status;
    }

    public String getReservationId() { return reservationId; }
    public Member getMember() { return member; }
    public LibraryBook getBook() { return book; }
    public String getStatus() { return status; }
}