package service;

import dao.LibraryDao;
import exception.*;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import model.*;

public class LibraryService {
    private final LibraryDao dao;
    private final AtomicInteger loanSeq = new AtomicInteger(1000);
    private final AtomicInteger resSeq = new AtomicInteger(5000);
    private final Object circulationLock = new Object();

    public LibraryService(LibraryDao dao) { this.dao = dao; }

    public void registerMember(String id, String name, MembershipType type)
            throws DuplicateEntityException, DataAccessException {
        Member m = switch (type) {
            case STUDENT -> new StudentMember(id, name);
            case FACULTY -> new FacultyMember(id, name);
            case GUEST -> new GuestMember(id, name);
        };
        dao.insertMember(m);
    }

    public void addBook(String isbn, String title, int copies)
            throws DuplicateEntityException, DataAccessException {
        dao.insertBook(new LibraryBook(isbn, title, copies, copies));
    }

    public LoanRecord issueLoan(String memberId, String isbn, int requestedDays, LocalDate issueDate, int lateDays)
            throws EntityNotFoundException, ItemUnavailableException, LoanLimitExceededException, DataAccessException {
        synchronized (circulationLock) {
            Member member = dao.findMemberById(memberId)
                    .orElseThrow(() -> new EntityNotFoundException("Member not found: " + memberId));
            LibraryBook book = dao.findBookByIsbn(isbn)
                    .orElseThrow(() -> new EntityNotFoundException("Book ISBN not found: " + isbn));

            if (!book.isAvailable()) throw new ItemUnavailableException("No copies available for '" + book.getTitle() + "'.");
            if (dao.hasActiveLoanForBook(memberId, isbn)) {
                throw new IllegalStateException("Member " + memberId + " already has an active loan for " + isbn + ".");
            }
            if (!member.canBorrowFor(requestedDays)) {
                throw new LoanLimitExceededException(member.getMembershipType() + " max loan is " + member.getMaximumLoanDays() + " days.");
            }

            String loanId = "LN-" + (loanSeq.incrementAndGet() + dao.findAllLoans().size());
            LoanRecord record = new LoanRecord(
                    loanId, member, book, requestedDays, issueDate, issueDate.plusDays(requestedDays), lateDays, LoanStatus.APPROVED);
            dao.createLoanTransaction(record);
            return record;
        }
    }

    public void returnBook(String loanId) throws EntityNotFoundException, DataAccessException {
        synchronized (circulationLock) { dao.returnLoanTransaction(loanId); }
    }

    public Reservation reserveBook(String memberId, String isbn)
            throws EntityNotFoundException, DataAccessException {
        synchronized (circulationLock) {
            Member member = dao.findMemberById(memberId)
                    .orElseThrow(() -> new EntityNotFoundException("Member not found: " + memberId));
            LibraryBook book = dao.findBookByIsbn(isbn)
                    .orElseThrow(() -> new EntityNotFoundException("Book ISBN not found: " + isbn));

            if (book.isAvailable()) throw new IllegalStateException("Book is currently available; issue a loan directly.");
            String resId = "RS-" + (resSeq.incrementAndGet() + dao.findAllReservations().size());
            Reservation res = new Reservation(resId, member, book, "ACTIVE");
            dao.insertReservation(res);
            return res;
        }
    }

    public void cancelReservation(String reservationId) throws EntityNotFoundException, DataAccessException {
        synchronized (circulationLock) { dao.cancelReservation(reservationId); }
    }

    public Result<LoanRecord> processNextReservationForBook(String isbn, int requestedDays, LocalDate issueDate)
            throws DataAccessException {
        synchronized (circulationLock) {
            Optional<LibraryBook> bookOpt = dao.findBookByIsbn(isbn);
            if (bookOpt.isEmpty()) return Result.fail("Book ISBN not found: " + isbn);
            if (!bookOpt.get().isAvailable()) return Result.fail("Book '" + bookOpt.get().getTitle() + "' has no available copies yet.");
            Optional<Reservation> nextRes = dao.findOldestActiveReservationForBook(isbn);
            if (nextRes.isEmpty()) return Result.fail("No active reservations in queue for ISBN " + isbn + ".");

            Reservation target = nextRes.get();
            try {
                LoanRecord issued = issueLoan(target.getMember().getMemberId(), isbn, requestedDays, issueDate, 0);
                dao.markReservationFulfilled(target.getReservationId());
                return Result.ok(issued, "Fulfilled " + target.getReservationId() + " -> Issued " + issued.getLoanId());
            } catch (EntityNotFoundException | ItemUnavailableException | LoanLimitExceededException | IllegalStateException e) {
                return Result.fail("Queue fulfillment blocked: " + e.getMessage());
            }
        }
    }

    public List<LibraryBook> searchBooks(String keyword) throws DataAccessException {
        return dao.searchBooks(keyword == null ? "" : keyword);
    }

    public List<Member> getAllMembers() throws DataAccessException { return dao.findAllMembers(); }

    public List<LoanRecord> getFilteredAndSortedLoans(String statusFilter, String memberTypeFilter)
            throws DataAccessException {
        return dao.findAllLoans().stream()
                .filter(l -> "ALL".equals(statusFilter) || l.getStatus().name().equals(statusFilter))
                .filter(l -> "ALL".equals(memberTypeFilter) || l.getMember().getMembershipType().name().equals(memberTypeFilter))
                .sorted(Comparator
                        .comparing(LoanRecord::isOverdue).reversed()
                        .thenComparing(LoanRecord::getDueDate)
                        .thenComparing(LoanRecord::getLoanId))
                .toList();
    }

    public List<Reservation> getAllReservations() throws DataAccessException { return dao.findAllReservations(); }

    public CirculationSummary generateSummary() throws DataAccessException {
        synchronized (circulationLock) {
            List<LibraryBook> books = dao.searchBooks("");
            List<LoanRecord> loans = dao.findAllLoans();
            List<Reservation> reservations = dao.findAllReservations();

            int availableCopies = books.stream().mapToInt(LibraryBook::getAvailableCopies).sum();
            int activeLoans = (int) loans.stream().filter(l -> l.getStatus() == LoanStatus.APPROVED).count();
            int activeRes = (int) reservations.stream().filter(r -> "ACTIVE".equals(r.getStatus())).count();

            Map<MembershipType, Long> byType = loans.stream()
                    .filter(l -> l.getStatus() == LoanStatus.APPROVED)
                    .collect(Collectors.groupingBy(
                            l -> l.getMember().getMembershipType(),
                            () -> new EnumMap<>(MembershipType.class),
                            Collectors.counting()));

            double totalFines = loans.stream().mapToDouble(LoanRecord::calculateEstimatedCharge).sum();
            return new CirculationSummary(books.size(), availableCopies, activeLoans, activeRes, byType, totalFines);
        }
    }
}