package ui;

import exception.*;
import java.awt.*;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.ExecutionException;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import model.*;
import service.LibraryService;
import service.Result;

public class BookBridgeFrame extends JFrame {
    private final LibraryService service;

    private final DefaultTableModel catalogModel = new DefaultTableModel(new String[]{"ISBN", "Title", "Total Copies", "Available"}, 0);
    private final DefaultTableModel memberModel = new DefaultTableModel(new String[]{"Member ID", "Name", "Type", "Max Days", "Daily Fine"}, 0);
    private final DefaultTableModel loanModel = new DefaultTableModel(new String[]{"Loan ID", "Member", "Book", "Days", "Due Date", "Late Days", "Status", "Est. Fine"}, 0);
    private final DefaultTableModel reservationModel = new DefaultTableModel(new String[]{"Reservation ID", "Member", "Book", "Status"}, 0);

    private final JComboBox<String> loanStatusFilter = new JComboBox<>(new String[]{"ALL", "APPROVED", "RETURNED"});
    private final JComboBox<String> loanTypeFilter = new JComboBox<>(new String[]{"ALL", "STUDENT", "FACULTY", "GUEST"});

    private final JLabel statusLabel = new JLabel("Ready.");
    private final JProgressBar progressBar = new JProgressBar();
    private final JButton refreshAllBtn = new JButton("Refresh All (Background)");
    private final JButton runReportBtn = new JButton("Generate Report (Background)");
    private final JTextArea reportArea = new JTextArea(12, 50);

    public BookBridgeFrame(LibraryService service) {
        super("BookBridge Integrated Library System (Final Release)");
        this.service = service;

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1020, 640);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout(8, 8));

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Catalog", buildCatalogPanel());
        tabs.addTab("Members", buildMembersPanel());
        tabs.addTab("Circulation", buildCirculationPanel());
        tabs.addTab("Reservations", buildReservationsPanel());
        tabs.addTab("Reports", buildReportsPanel());

        add(tabs, BorderLayout.CENTER);
        add(buildStatusBar(), BorderLayout.SOUTH);

        refreshAllInBackground("");
    }

    private JPanel buildCatalogPanel() {
        JPanel panel = new JPanel(new BorderLayout(6, 6));
        JPanel form = new JPanel(new FlowLayout(FlowLayout.LEFT));

        JTextField isbnField = new JTextField(10);
        JTextField titleField = new JTextField(16);
        JTextField copiesField = new JTextField("1", 4);
        JButton addBtn = new JButton("Add Book");
        JTextField searchField = new JTextField(12);
        JButton searchBtn = new JButton("Search");

        form.add(new JLabel("ISBN:")); form.add(isbnField);
        form.add(new JLabel("Title:")); form.add(titleField);
        form.add(new JLabel("Copies:")); form.add(copiesField);
        form.add(addBtn);
        form.add(new JLabel(" | Search:")); form.add(searchField);
        form.add(searchBtn);

        addBtn.addActionListener(e -> {
            try {
                int copies = Integer.parseInt(copiesField.getText().trim());
                service.addBook(isbnField.getText(), titleField.getText(), copies);
                isbnField.setText(""); titleField.setText(""); copiesField.setText("1");
                setStatus("Book added successfully.", false);
                refreshAllInBackground("");
            } catch (NumberFormatException ex) {
                showError("Validation Error", "Copies must be a valid integer.");
            } catch (IllegalArgumentException | DuplicateEntityException ex) {
                showError("Domain / Duplicate Error", ex.getMessage());
            } catch (DataAccessException ex) {
                showError("Database Failure", ex.getMessage());
            }
        });

        searchBtn.addActionListener(e -> refreshAllInBackground(searchField.getText()));
        panel.add(form, BorderLayout.NORTH);
        panel.add(new JScrollPane(new JTable(catalogModel)), BorderLayout.CENTER);
        return panel;
    }

    private JPanel buildMembersPanel() {
        JPanel panel = new JPanel(new BorderLayout(6, 6));
        JPanel form = new JPanel(new FlowLayout(FlowLayout.LEFT));

        JTextField idField = new JTextField(10);
        JTextField nameField = new JTextField(16);
        JComboBox<MembershipType> typeBox = new JComboBox<>(MembershipType.values());
        JButton regBtn = new JButton("Register Member");

        form.add(new JLabel("Member ID:")); form.add(idField);
        form.add(new JLabel("Name:")); form.add(nameField);
        form.add(new JLabel("Type:")); form.add(typeBox);
        form.add(regBtn);

        regBtn.addActionListener(e -> {
            try {
                service.registerMember(idField.getText(), nameField.getText(), (MembershipType) typeBox.getSelectedItem());
                idField.setText(""); nameField.setText("");
                setStatus("Member registered successfully.", false);
                refreshAllInBackground("");
            } catch (IllegalArgumentException | DuplicateEntityException ex) {
                showError("Validation / Duplicate Error", ex.getMessage());
            } catch (DataAccessException ex) {
                showError("Database Failure", ex.getMessage());
            }
        });

        panel.add(form, BorderLayout.NORTH);
        panel.add(new JScrollPane(new JTable(memberModel)), BorderLayout.CENTER);
        return panel;
    }

    private JPanel buildCirculationPanel() {
        JPanel panel = new JPanel(new BorderLayout(6, 6));
        JPanel top = new JPanel(new GridLayout(2, 1));

        JPanel row1 = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JTextField memberIdField = new JTextField(8);
        JTextField isbnField = new JTextField(8);
        JTextField daysField = new JTextField("7", 3);
        JTextField lateField = new JTextField("0", 3);
        JButton issueBtn = new JButton("Issue Loan");
        JTextField returnLoanIdField = new JTextField(8);
        JButton returnBtn = new JButton("Return Book");

        row1.add(new JLabel("Member ID:")); row1.add(memberIdField);
        row1.add(new JLabel("ISBN:")); row1.add(isbnField);
        row1.add(new JLabel("Days:")); row1.add(daysField);
        row1.add(new JLabel("Late:")); row1.add(lateField);
        row1.add(issueBtn);
        row1.add(new JLabel(" | Loan ID:")); row1.add(returnLoanIdField);
        row1.add(returnBtn);

        JPanel row2 = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton applyFilterBtn = new JButton("Filter & Sort (Overdue First)");
        row2.add(new JLabel("Filter Status:")); row2.add(loanStatusFilter);
        row2.add(new JLabel("Member Type:")); row2.add(loanTypeFilter);
        row2.add(applyFilterBtn);

        top.add(row1);
        top.add(row2);

        JTable loanTable = new JTable(loanModel);

        issueBtn.addActionListener(e -> {
            try {
                int days = Integer.parseInt(daysField.getText().trim());
                int late = Integer.parseInt(lateField.getText().trim());
                LoanRecord rec = service.issueLoan(memberIdField.getText().trim(), isbnField.getText().trim(),
                        days, LocalDate.now(), late);
                setStatus("Issued Loan " + rec.getLoanId() + " (Due: " + rec.getDueDate() + ").", false);
                refreshAllInBackground("");
            } catch (NumberFormatException ex) {
                showError("Validation Error", "Days and Late Days must be whole numbers.");
            } catch (EntityNotFoundException ex) {
                showError("Not Found", ex.getMessage());
            } catch (ItemUnavailableException | LoanLimitExceededException | IllegalArgumentException | IllegalStateException ex) {
                showError("Circulation Rule Violation", ex.getMessage());
            } catch (DataAccessException ex) {
                showError("Database Failure", ex.getMessage());
            }
        });

        returnBtn.addActionListener(e -> {
            String loanId = returnLoanIdField.getText().trim();
            if (loanId.isEmpty() && loanTable.getSelectedRow() >= 0) {
                loanId = loanModel.getValueAt(loanTable.getSelectedRow(), 0).toString();
            }
            if (loanId.isEmpty()) {
                showError("Validation Error", "Enter or select a Loan ID to return.");
                return;
            }
            if (JOptionPane.showConfirmDialog(this, "Confirm return of loan " + loanId + "?",
                    "Confirm Return", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) return;

            try {
                service.returnBook(loanId);
                returnLoanIdField.setText("");
                setStatus("Returned Loan " + loanId + ".", false);
                refreshAllInBackground("");
            } catch (EntityNotFoundException ex) {
                showError("Not Found", ex.getMessage());
            } catch (IllegalStateException ex) {
                showError("Invalid Return State", ex.getMessage());
            } catch (DataAccessException ex) {
                showError("Database Failure", ex.getMessage());
            }
        });

        applyFilterBtn.addActionListener(e -> refreshAllInBackground(""));

        panel.add(top, BorderLayout.NORTH);
        panel.add(new JScrollPane(loanTable), BorderLayout.CENTER);
        return panel;
    }

        private JPanel buildReservationsPanel() {
        JPanel panel = new JPanel(new BorderLayout(6, 6));
        JPanel form = new JPanel(new FlowLayout(FlowLayout.LEFT));

        JTextField memberIdField = new JTextField(8);
        JTextField isbnField = new JTextField(8);
        JButton reserveBtn = new JButton("Reserve");
        JTextField resIdField = new JTextField(8);
        JButton cancelBtn = new JButton("Cancel");
        JTextField queueIsbnField = new JTextField(8);
        JButton processQueueBtn = new JButton("Process Next in Queue");

        form.add(new JLabel("Member:")); form.add(memberIdField);
        form.add(new JLabel("ISBN:")); form.add(isbnField);
        form.add(reserveBtn);
        form.add(new JLabel(" | Res ID:")); form.add(resIdField);
        form.add(cancelBtn);
        form.add(new JLabel(" | Queue ISBN:")); form.add(queueIsbnField);
        form.add(processQueueBtn);

        JTable resTable = new JTable(reservationModel);

        reserveBtn.addActionListener(e -> {
            try {
                Reservation res = service.reserveBook(memberIdField.getText().trim(), isbnField.getText().trim());
                setStatus("Created Reservation " + res.getReservationId() + ".", false);
                refreshAllInBackground("");
            } catch (EntityNotFoundException ex) {
                showError("Not Found", ex.getMessage());
            } catch (IllegalStateException ex) {
                showError("Reservation Rule", ex.getMessage());
            } catch (DataAccessException ex) {
                showError("Database Failure", ex.getMessage());
            }
        });

        cancelBtn.addActionListener(e -> {
            String resId = resIdField.getText().trim();
            if (resId.isEmpty() && resTable.getSelectedRow() >= 0) {
                resId = reservationModel.getValueAt(resTable.getSelectedRow(), 0).toString();
            }
            if (resId.isEmpty()) {
                showError("Validation Error", "Enter or select a Reservation ID to cancel.");
                return;
            }
            if (JOptionPane.showConfirmDialog(this, "Cancel reservation " + resId + "?",
                    "Confirm Cancellation", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) return;

            try {
                service.cancelReservation(resId);
                resIdField.setText("");
                setStatus("Cancelled Reservation " + resId + ".", false);
                refreshAllInBackground("");
            } catch (EntityNotFoundException ex) {
                showError("Not Found", ex.getMessage());
            } catch (DataAccessException ex) {
                showError("Database Failure", ex.getMessage());
            }
        });

        processQueueBtn.addActionListener(e -> {
            String targetIsbn = queueIsbnField.getText().trim();
            if (targetIsbn.isEmpty()) {
                showError("Validation Error", "Enter a Book ISBN to process its FIFO reservation queue.");
                return;
            }
            try {
                Result<LoanRecord> outcome = service.processNextReservationForBook(targetIsbn, 7, LocalDate.now());
                if (outcome.isSuccess()) {
                    setStatus(outcome.getMessage(), false);
                    refreshAllInBackground("");
                } else {
                    showError("Queue Processing Notice", outcome.getMessage());
                }
            } catch (DataAccessException ex) {
                showError("Database Failure", ex.getMessage());
            }
        });

        panel.add(form, BorderLayout.NORTH);
        panel.add(new JScrollPane(resTable), BorderLayout.CENTER);
        return panel;
    }

    private JPanel buildReportsPanel() {
        JPanel panel = new JPanel(new BorderLayout(6, 6));
        reportArea.setEditable(false);
        reportArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));

        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
        top.add(runReportBtn);
        runReportBtn.addActionListener(e -> generateReportInBackground());

        panel.add(top, BorderLayout.NORTH);
        panel.add(new JScrollPane(reportArea), BorderLayout.CENTER);
        return panel;
    }

    private JPanel buildStatusBar() {
        JPanel bar = new JPanel(new BorderLayout(8, 0));
        bar.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        progressBar.setPreferredSize(new Dimension(160, 18));
        refreshAllBtn.addActionListener(e -> refreshAllInBackground(""));

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        right.add(progressBar);
        right.add(refreshAllBtn);

        bar.add(statusLabel, BorderLayout.CENTER);
        bar.add(right, BorderLayout.EAST);
        return bar;
    }

    private record DashboardSnapshot(List<LibraryBook> books, List<Member> members,
                                     List<LoanRecord> loans, List<Reservation> reservations) {}

    private void refreshAllInBackground(String searchKeyword) {
        String statusF = (String) loanStatusFilter.getSelectedItem();
        String typeF = (String) loanTypeFilter.getSelectedItem();
        setBusy(true, "Synchronizing tables with database in background...");
        new SwingWorker<DashboardSnapshot, Void>() {
            @Override
            protected DashboardSnapshot doInBackground() throws Exception {
                return new DashboardSnapshot(
                        service.searchBooks(searchKeyword),
                        service.getAllMembers(),
                        service.getFilteredAndSortedLoans(statusF, typeF),
                        service.getAllReservations());
            }

            @Override
            protected void done() {
                try {
                    DashboardSnapshot snap = get();
                    catalogModel.setRowCount(0);
                    for (LibraryBook b : snap.books()) {
                        catalogModel.addRow(new Object[]{b.getIsbn(), b.getTitle(), b.getTotalCopies(), b.getAvailableCopies()});
                    }
                    memberModel.setRowCount(0);
                    for (Member m : snap.members()) {
                        memberModel.addRow(new Object[]{m.getMemberId(), m.getName(), m.getMembershipType(),
                                m.getMaximumLoanDays(), String.format("PHP %.2f", m.getDailyFine())});
                    }
                    loanModel.setRowCount(0);
                    for (LoanRecord l : snap.loans()) {
                        loanModel.addRow(new Object[]{l.getLoanId(), l.getMember().getName(), l.getBook().getTitle(),
                                l.getRequestedDays(), l.getDueDate(), l.getLateDays(), l.getStatus(),
                                String.format("PHP %.2f", l.calculateEstimatedCharge())});
                    }
                    reservationModel.setRowCount(0);
                    for (Reservation r : snap.reservations()) {
                        reservationModel.addRow(new Object[]{r.getReservationId(), r.getMember().getName(),
                                r.getBook().getTitle(), r.getStatus()});
                    }
                    setBusy(false, "Tables synchronized (" + snap.books().size() + " books, " + snap.loans().size() + " loans).");
                } catch (InterruptedException | ExecutionException ex) {
                    setBusy(false, "Database refresh failed.");
                    showError("Database Failure", ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage());
                }
            }
        }.execute();
    }

    private void generateReportInBackground() {
        setBusy(true, "Aggregating circulation report in background...");
        new SwingWorker<CirculationSummary, Void>() {
            @Override
            protected CirculationSummary doInBackground() throws Exception {
                return service.generateSummary();
            }

            @Override
            protected void done() {
                try {
                    CirculationSummary s = get();
                    reportArea.setText(String.format("""
                            === BOOKBRIDGE INTEGRATED CIRCULATION REPORT ===
                            Catalog Titles        : %d
                            Available Book Copies : %d
                            Active Approved Loans : %d
                            Active Reservations   : %d
                            Active Loans by Type  : %s
                            Total Estimated Fines : PHP %.2f
                            ================================================
                            """,
                            s.totalCatalogTitles(), s.totalAvailableCopies(),
                            s.activeLoansCount(), s.activeReservationsCount(),
                            s.activeLoansByMemberType(), s.totalEstimatedFines()));
                    setBusy(false, "Circulation report generated.");
                } catch (InterruptedException | ExecutionException ex) {
                    setBusy(false, "Report generation failed.");
                    showError("Report Error", ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage());
                }
            }
        }.execute();
    }

    private void setBusy(boolean busy, String message) {
        progressBar.setIndeterminate(busy);
        refreshAllBtn.setEnabled(!busy);
        runReportBtn.setEnabled(!busy);
        statusLabel.setText(message);
    }

    private void setStatus(String message, boolean isError) {
        statusLabel.setForeground(isError ? Color.RED : new Color(0, 102, 51));
        statusLabel.setText(message);
    }

    private void showError(String title, String message) {
        setStatus(title + ": " + message, true);
        JOptionPane.showMessageDialog(this, message, title, JOptionPane.ERROR_MESSAGE);
    }
}
