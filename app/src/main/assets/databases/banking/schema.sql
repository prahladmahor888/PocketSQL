CREATE DATABASE IF NOT EXISTS banking;
USE banking;

CREATE TABLE branches (
    branch_id INT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    city VARCHAR(80) NOT NULL,
    manager VARCHAR(100) NOT NULL
);
CREATE INDEX idx_branches_city ON branches (city);
CREATE INDEX idx_branches_manager ON branches (manager);

CREATE TABLE customers (
    customer_id INT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    email VARCHAR(150) NOT NULL UNIQUE,
    phone VARCHAR(30),
    registered_at DATETIME NOT NULL
);
CREATE INDEX idx_customers_phone ON customers (phone);
CREATE INDEX idx_customers_registered ON customers (registered_at);

CREATE TABLE accounts (
    account_id INT AUTO_INCREMENT PRIMARY KEY,
    customer_id INT NOT NULL,
    branch_id INT NOT NULL,
    account_type VARCHAR(40) NOT NULL,
    balance DECIMAL(12,2) NOT NULL,
    opened_at DATETIME NOT NULL,
    FOREIGN KEY (customer_id) REFERENCES customers(customer_id),
    FOREIGN KEY (branch_id) REFERENCES branches(branch_id),
    CHECK (balance >= -1000000)
);
CREATE INDEX idx_accounts_customer ON accounts (customer_id);
CREATE INDEX idx_accounts_branch ON accounts (branch_id);
CREATE INDEX idx_accounts_type ON accounts (account_type);
CREATE INDEX idx_accounts_opened_at ON accounts (opened_at);

CREATE TABLE transactions (
    transaction_id INT AUTO_INCREMENT PRIMARY KEY,
    account_id INT NOT NULL,
    transaction_date DATETIME NOT NULL,
    amount DECIMAL(12,2) NOT NULL,
    type VARCHAR(20) NOT NULL,
    description VARCHAR(200),
    FOREIGN KEY (account_id) REFERENCES accounts(account_id)
);
CREATE INDEX idx_transactions_account ON transactions (account_id);
CREATE INDEX idx_transactions_date ON transactions (transaction_date);
CREATE INDEX idx_transactions_type ON transactions (type);

CREATE TABLE cards (
    card_id INT AUTO_INCREMENT PRIMARY KEY,
    customer_id INT NOT NULL,
    card_number VARCHAR(20) NOT NULL UNIQUE,
    card_type VARCHAR(40) NOT NULL,
    limit_amount DECIMAL(12,2) NOT NULL,
    issued_at DATE NOT NULL,
    FOREIGN KEY (customer_id) REFERENCES customers(customer_id),
    CHECK (limit_amount >= 0)
);
CREATE INDEX idx_cards_customer ON cards (customer_id);
CREATE INDEX idx_cards_type ON cards (card_type);

CREATE TABLE loans (
    loan_id INT AUTO_INCREMENT PRIMARY KEY,
    customer_id INT NOT NULL,
    branch_id INT NOT NULL,
    amount DECIMAL(12,2) NOT NULL,
    status VARCHAR(30) NOT NULL,
    issued_at DATE NOT NULL,
    due_date DATE NOT NULL,
    FOREIGN KEY (customer_id) REFERENCES customers(customer_id),
    FOREIGN KEY (branch_id) REFERENCES branches(branch_id),
    CHECK (amount > 0),
    CHECK (due_date >= issued_at)
);
CREATE INDEX idx_loans_customer ON loans (customer_id);
CREATE INDEX idx_loans_branch ON loans (branch_id);
CREATE INDEX idx_loans_status ON loans (status);

CREATE TABLE account_transfers (
    transfer_id INT AUTO_INCREMENT PRIMARY KEY,
    from_account_id INT NOT NULL,
    to_account_id INT NOT NULL,
    transfer_date DATETIME NOT NULL,
    amount DECIMAL(12,2) NOT NULL,
    status VARCHAR(30) NOT NULL,
    FOREIGN KEY (from_account_id) REFERENCES accounts(account_id),
    FOREIGN KEY (to_account_id) REFERENCES accounts(account_id),
    CHECK (amount > 0),
    CHECK (from_account_id <> to_account_id)
);
CREATE INDEX idx_account_transfers_from ON account_transfers (from_account_id);
CREATE INDEX idx_account_transfers_to ON account_transfers (to_account_id);
CREATE INDEX idx_account_transfers_date ON account_transfers (transfer_date);

CREATE TABLE currency_rates (
    currency_id INT AUTO_INCREMENT PRIMARY KEY,
    currency_code VARCHAR(10) NOT NULL,
    rate_to_inr DECIMAL(12,6) NOT NULL,
    updated_at DATETIME NOT NULL,
    CHECK (rate_to_inr > 0)
);
CREATE INDEX idx_currency_rates_code ON currency_rates (currency_code);
CREATE INDEX idx_currency_rates_updated_at ON currency_rates (updated_at);
