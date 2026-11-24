package dao;

import exception.DataAccessException;
import exception.DuplicateEntityException;
import exception.EntityNotFoundException;
import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import model.*;

public class LibraryDao {
    private final ConnectionFactory connectionFactory;

    public LibraryDao(ConnectionFactory connectionFactory) {
        this.connectionFactory = connectionFactory;
    }

    public void insertMember(Member member) throws DuplicateEntityException, DataAccessException {
        String sql = "INSERT INTO members (member_id, name, membership_type) VALUES (?, ?, ?)";
        try (Connection conn = connectionFactory.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, member.getMemberId());
            ps.setString(2, member.getName());
            ps.setString(3, member.getMembershipType().name());
            if (ps.executeUpdate() != 1) throw new DataAccessException("Failed to insert member.", null);
        } catch (SQLException e) {
            if (e.getMessage() != null && e.getMessage().toLowerCase().contains("unique")) {
                throw new DuplicateEntityException("Duplicate Member ID: " + member.getMemberId(), e);
            }
            throw new DataAccessException("Database error inserting member: " + e.getMessage(), e);
        }
    }

    public Optional<Member> findMemberById(String memberId) throws DataAccessException {
        String sql = "SELECT member_id, name, membership_type FROM members WHERE member_id = ?";
        try (Connection conn = connectionFactory.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, memberId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapMember(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new DataAccessException("Database error querying member: " + e.getMessage(), e);
        }
    }

    public List<Member> findAllMembers() throws DataAccessException {
        String sql = "SELECT member_id, name, membership_type FROM members ORDER BY member_id ASC";
        List<Member> list = new ArrayList<>();
        try (Connection conn = connectionFactory.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) list.add(mapMember(rs));
            return list;
        } catch (SQLException e) {
            throw new DataAccessException("Database error loading members: " + e.getMessage(), e);
        }
    }

    public void insertBook(LibraryBook book) throws DuplicateEntityException, DataAccessException {
        String sql = "INSERT INTO books (isbn, title, total_copies, available_copies) VALUES (?, ?, ?, ?)";
        try (Connection conn = connectionFactory.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, book.getIsbn());
            ps.setString(2, book.getTitle());
            ps.setInt(3, book.getTotalCopies());
            ps.setInt(4, book.getAvailableCopies());
            if (ps.executeUpdate() != 1) throw new DataAccessException("Failed to insert book.", null);
        } catch (SQLException e) {
            if (e.getMessage() != null && e.getMessage().toLowerCase().contains("unique")) {
                throw new DuplicateEntityException("Duplicate Book ISBN: " + book.getIsbn(), e);
            }
            throw new DataAccessException("Database error inserting book: " + e.getMessage(), e);
        }
    }

    public Optional<LibraryBook> findBookByIsbn(String isbn) throws DataAccessException {
        String sql = "SELECT isbn, title, total_copies, available_copies FROM books WHERE isbn = ?";
        try (Connection conn = connectionFactory.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, isbn);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapBook(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new DataAccessException("Database error querying book: " + e.getMessage(), e);
        }
    }

    public List<LibraryBook> searchBooks(String keyword) throws DataAccessException {
        String sql = "SELECT isbn, title, total_copies, available_copies FROM books " +
                     "WHERE LOWER(title) LIKE ? OR LOWER(isbn) LIKE ? ORDER BY isbn ASC";
        String pattern = "%" + keyword.trim().toLowerCase() + "%";
        List<LibraryBook> list = new ArrayList<>();
        try (Connection conn = connectionFactory.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, pattern);
            ps.setString(2, pattern);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapBook(rs));
            }
            return list;
        } catch (SQLException e) {
            throw new DataAccessException("Database error searching books: " + e.getMessage(), e);
        }
    }

    public boolean hasActiveLoanForBook(String memberId, String isbn) throws DataAccessException {
        String sql = "SELECT COUNT(*) FROM loans WHERE member_id = ? AND isbn = ? AND status = 'APPROVED'";
        try (Connection conn = connectionFactory.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, memberId);
            ps.setString(2, isbn);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Database error checking duplicate active loan: " + e.getMessage(), e);
        }
    }

    public void createLoanTransaction(LoanRecord loan) throws DataAccessException {
        String decCopySql = "UPDATE books SET available_copies = available_copies - 1 WHERE isbn = ? AND available_copies > 0";
        String insertLoanSql = "INSERT INTO loans (loan_id, member_id, isbn, requested_days, issue_date, due_date, late_days, status) " +
                               "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = connectionFactory.getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement psBook = conn.prepareStatement(decCopySql);
                 PreparedStatement psLoan = conn.prepareStatement(insertLoanSql)) {
                psBook.setString(1, loan.getBook().getIsbn());
                if (psBook.executeUpdate() != 1) {
                    conn.rollback();
                    throw new DataAccessException("Book copy unavailable during checkout.", null);
                }
                psLoan.setString(1, loan.getLoanId());
                psLoan.setString(2, loan.getMember().getMemberId());
                psLoan.setString(3, loan.getBook().getIsbn());
                psLoan.setInt(4, loan.getRequestedDays());
                psLoan.setString(5, loan.getIssueDate().toString());
                psLoan.setString(6, loan.getDueDate().toString());
                psLoan.setInt(7, loan.getLateDays());
                psLoan.setString(8, loan.getStatus().name());
                if (psLoan.executeUpdate() != 1) {
                    conn.rollback();
                    throw new DataAccessException("Loan insert failed.", null);
                }
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new DataAccessException("Database error issuing loan: " + e.getMessage(), e);
        }
    }

    public void returnLoanTransaction(String loanId) throws EntityNotFoundException, DataAccessException {
        String findSql = "SELECT isbn, status FROM loans WHERE loan_id = ?";
        String updateLoanSql = "UPDATE loans SET status = 'RETURNED' WHERE loan_id = ? AND status = 'APPROVED'";
        String incCopySql = "UPDATE books SET available_copies = available_copies + 1 WHERE isbn = ?";

        try (Connection conn = connectionFactory.getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement psFind = conn.prepareStatement(findSql)) {
                psFind.setString(1, loanId);
                String isbn;
                try (ResultSet rs = psFind.executeQuery()) {
                    if (!rs.next()) {
                        conn.rollback();
                        throw new EntityNotFoundException("Loan ID not found: " + loanId);
                    }
                    if (!"APPROVED".equals(rs.getString("status"))) {
                        conn.rollback();
                        throw new IllegalStateException("Loan " + loanId + " is already returned.");
                    }
                    isbn = rs.getString("isbn");
                }
                try (PreparedStatement psUpdLoan = conn.prepareStatement(updateLoanSql);
                     PreparedStatement psUpdBook = conn.prepareStatement(incCopySql)) {
                    psUpdLoan.setString(1, loanId);
                    psUpdBook.setString(1, isbn);
                    if (psUpdLoan.executeUpdate() != 1 || psUpdBook.executeUpdate() != 1) {
                        conn.rollback();
                        throw new DataAccessException("Failed to complete return transaction.", null);
                    }
                }
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
                } catch (SQLException e) {
            throw new DataAccessException("Database error returning loan: " + e.getMessage(), e);
        }
    }

    public List<LoanRecord> findAllLoans() throws DataAccessException {
        String sql = "SELECT l.loan_id, l.requested_days, l.issue_date, l.due_date, l.late_days, l.status, " +
                     "m.member_id, m.name, m.membership_type, " +
                     "b.isbn, b.title, b.total_copies, b.available_copies " +
                     "FROM loans l JOIN members m ON l.member_id = m.member_id JOIN books b ON l.isbn = b.isbn " +
                     "ORDER BY l.loan_id ASC";
        List<LoanRecord> list = new ArrayList<>();
        try (Connection conn = connectionFactory.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(new LoanRecord(
                        rs.getString("loan_id"), mapMember(rs), mapBook(rs),
                        rs.getInt("requested_days"), LocalDate.parse(rs.getString("issue_date")),
                        LocalDate.parse(rs.getString("due_date")), rs.getInt("late_days"),
                        LoanStatus.valueOf(rs.getString("status"))));
            }
            return list;
        } catch (SQLException e) {
            throw new DataAccessException("Database error loading loans: " + e.getMessage(), e);
        }
    }

    public void insertReservation(Reservation res) throws DataAccessException {
        String sql = "INSERT INTO reservations (reservation_id, member_id, isbn, status) VALUES (?, ?, ?, ?)";
        try (Connection conn = connectionFactory.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, res.getReservationId());
            ps.setString(2, res.getMember().getMemberId());
            ps.setString(3, res.getBook().getIsbn());
            ps.setString(4, res.getStatus());
            if (ps.executeUpdate() != 1) throw new DataAccessException("Failed to create reservation.", null);
        } catch (SQLException e) {
            throw new DataAccessException("Database error inserting reservation: " + e.getMessage(), e);
        }
    }

    public void cancelReservation(String reservationId) throws EntityNotFoundException, DataAccessException {
        String sql = "UPDATE reservations SET status = 'CANCELLED' WHERE reservation_id = ? AND status = 'ACTIVE'";
        try (Connection conn = connectionFactory.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, reservationId);
            if (ps.executeUpdate() == 0) throw new EntityNotFoundException("Active reservation not found: " + reservationId);
        } catch (SQLException e) {
            throw new DataAccessException("Database error cancelling reservation: " + e.getMessage(), e);
        }
    }

    public Optional<Reservation> findOldestActiveReservationForBook(String isbn) throws DataAccessException {
        String sql = "SELECT r.reservation_id, r.status, m.member_id, m.name, m.membership_type, " +
                     "b.isbn, b.title, b.total_copies, b.available_copies " +
                     "FROM reservations r JOIN members m ON r.member_id = m.member_id JOIN books b ON r.isbn = b.isbn " +
                     "WHERE r.isbn = ? AND r.status = 'ACTIVE' ORDER BY r.created_at ASC, r.reservation_id ASC LIMIT 1";
        try (Connection conn = connectionFactory.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, isbn);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next()
                        ? Optional.of(new Reservation(rs.getString("reservation_id"), mapMember(rs), mapBook(rs), rs.getString("status")))
                        : Optional.empty();
            }
        } catch (SQLException e) {
            throw new DataAccessException("Database error querying reservation queue: " + e.getMessage(), e);
        }
    }

    public void markReservationFulfilled(String reservationId) throws DataAccessException {
        String sql = "UPDATE reservations SET status = 'FULFILLED' WHERE reservation_id = ? AND status = 'ACTIVE'";
        try (Connection conn = connectionFactory.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, reservationId);
            if (ps.executeUpdate() != 1) throw new DataAccessException("Failed to mark reservation fulfilled: " + reservationId, null);
        } catch (SQLException e) {
            throw new DataAccessException("Database error updating reservation: " + e.getMessage(), e);
        }
    }

    public List<Reservation> findAllReservations() throws DataAccessException {
        String sql = "SELECT r.reservation_id, r.status, m.member_id, m.name, m.membership_type, " +
                     "b.isbn, b.title, b.total_copies, b.available_copies " +
                     "FROM reservations r JOIN members m ON r.member_id = m.member_id JOIN books b ON r.isbn = b.isbn " +
                     "ORDER BY r.reservation_id ASC";
        List<Reservation> list = new ArrayList<>();
        try (Connection conn = connectionFactory.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(new Reservation(rs.getString("reservation_id"), mapMember(rs), mapBook(rs), rs.getString("status")));
            }
            return list;
        } catch (SQLException e) {
            throw new DataAccessException("Database error loading reservations: " + e.getMessage(), e);
        }
    }

    private Member mapMember(ResultSet rs) throws SQLException {
        String id = rs.getString("member_id");
        String name = rs.getString("name");
        return switch (MembershipType.valueOf(rs.getString("membership_type"))) {
            case STUDENT -> new StudentMember(id, name);
            case FACULTY -> new FacultyMember(id, name);
            case GUEST -> new GuestMember(id, name);
        };
    }

    private LibraryBook mapBook(ResultSet rs) throws SQLException {
        return new LibraryBook(rs.getString("isbn"), rs.getString("title"), rs.getInt("total_copies"), rs.getInt("available_copies"));
    }
}
