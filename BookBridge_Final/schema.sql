PRAGMA foreign_keys = ON;

CREATE TABLE IF NOT EXISTS members (
    member_id VARCHAR(20) PRIMARY KEY,
    name VARCHAR(100) NOT NULL CHECK (LENGTH(TRIM(name)) > 0),
    membership_type VARCHAR(20) NOT NULL CHECK (membership_type IN ('STUDENT', 'FACULTY', 'GUEST')),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS books (
    isbn VARCHAR(30) PRIMARY KEY,
    title VARCHAR(150) NOT NULL CHECK (LENGTH(TRIM(title)) > 0),
    total_copies INT NOT NULL DEFAULT 1 CHECK (total_copies >= 1),
    available_copies INT NOT NULL DEFAULT 1 CHECK (available_copies >= 0 AND available_copies <= total_copies),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS loans (
    loan_id VARCHAR(20) PRIMARY KEY,
    member_id VARCHAR(20) NOT NULL,
    isbn VARCHAR(30) NOT NULL,
    requested_days INT NOT NULL CHECK (requested_days BETWEEN 1 AND 30),
    issue_date DATE NOT NULL,
    due_date DATE NOT NULL,
    late_days INT NOT NULL DEFAULT 0 CHECK (late_days >= 0),
    status VARCHAR(20) NOT NULL CHECK (status IN ('APPROVED', 'RETURNED')),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (member_id) REFERENCES members(member_id) ON DELETE RESTRICT,
    FOREIGN KEY (isbn) REFERENCES books(isbn) ON DELETE RESTRICT
);

CREATE TABLE IF NOT EXISTS reservations (
    reservation_id VARCHAR(20) PRIMARY KEY,
    member_id VARCHAR(20) NOT NULL,
    isbn VARCHAR(30) NOT NULL,
    status VARCHAR(20) NOT NULL CHECK (status IN ('ACTIVE', 'FULFILLED', 'CANCELLED')),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (member_id) REFERENCES members(member_id) ON DELETE RESTRICT,
    FOREIGN KEY (isbn) REFERENCES books(isbn) ON DELETE RESTRICT
);
