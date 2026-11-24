package model;

public class LibraryBook {
    private final String isbn;
    private final String title;
    private final int totalCopies;
    private final int availableCopies;

    public LibraryBook(String isbn, String title, int totalCopies, int availableCopies) {
        if (isbn == null || isbn.trim().isEmpty()) throw new IllegalArgumentException("ISBN cannot be blank.");
        if (title == null || title.trim().isEmpty()) throw new IllegalArgumentException("Title cannot be blank.");
        if (totalCopies < 1 || availableCopies < 0 || availableCopies > totalCopies) {
            throw new IllegalArgumentException("Invalid copy counts.");
        }
        this.isbn = isbn.trim();
        this.title = title.trim();
        this.totalCopies = totalCopies;
        this.availableCopies = availableCopies;
    }

    public String getIsbn() { return isbn; }
    public String getTitle() { return title; }
    public int getTotalCopies() { return totalCopies; }
    public int getAvailableCopies() { return availableCopies; }
    public boolean isAvailable() { return availableCopies > 0; }
}