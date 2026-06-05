SET FOREIGN_KEY_CHECKS = 0;

DROP TABLE IF EXISTS journal_items;
DROP TABLE IF EXISTS journal_entry_lines;
DROP TABLE IF EXISTS journal_entries;
DROP TABLE IF EXISTS voucher_lines;
DROP TABLE IF EXISTS vouchers;
DROP TABLE IF EXISTS accounts;
DROP TABLE IF EXISTS accounting_closing_logs;
DROP TABLE IF EXISTS accounting_periods;
DROP TABLE IF EXISTS payroll_settlement_items;
DROP TABLE IF EXISTS payroll_deduction_items;
DROP TABLE IF EXISTS payroll_deductions;
DROP TABLE IF EXISTS payrolls;
DROP TABLE IF EXISTS payroll_contracts;
DROP TABLE IF EXISTS stock_movements;
DROP TABLE IF EXISTS purchase_order_items;
DROP TABLE IF EXISTS purchase_orders;
DROP TABLE IF EXISTS inventories;
DROP TABLE IF EXISTS warehouses;
DROP TABLE IF EXISTS items;
DROP TABLE IF EXISTS vendors;
DROP TABLE IF EXISTS attendances;
DROP TABLE IF EXISTS employees;
DROP TABLE IF EXISTS members;
DROP TABLE IF EXISTS task_history;
DROP TABLE IF EXISTS anomaly_logs;
DROP TABLE IF EXISTS notice;
DROP TABLE IF EXISTS ocr_documents;

SET FOREIGN_KEY_CHECKS = 1;

CREATE TABLE IF NOT EXISTS members (
    id BIGINT NOT NULL AUTO_INCREMENT,
    login_id VARCHAR(50) NOT NULL,
    password VARCHAR(255) NOT NULL,
    name VARCHAR(100) NOT NULL,
    role VARCHAR(30) NOT NULL,
    active TINYINT(1) NOT NULL DEFAULT 1,
    last_login_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_members_login_id (login_id),
    KEY idx_members_role (role),
    KEY idx_members_active (active)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS employees (
    id BIGINT NOT NULL AUTO_INCREMENT,
    member_id BIGINT NULL,
    employee_no VARCHAR(50) NOT NULL,
    name VARCHAR(100) NOT NULL,
    department VARCHAR(100) NOT NULL,
    position VARCHAR(100) NOT NULL,
    employment_status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    hire_date DATE NOT NULL,
    email VARCHAR(100) NOT NULL,
    phone VARCHAR(30) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_employees_employee_no (employee_no),
    UNIQUE KEY uk_employees_member_id (member_id),
    UNIQUE KEY uk_employees_email (email),
    CONSTRAINT fk_employees_member
        FOREIGN KEY (member_id) REFERENCES members (id)
        ON DELETE SET NULL
        ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS attendances (
    id BIGINT NOT NULL AUTO_INCREMENT,
    employee_id BIGINT NOT NULL,
    work_date DATE NOT NULL,
    check_in_at DATETIME NULL,
    check_out_at DATETIME NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'PRESENT',
    note VARCHAR(255) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_attendances_employee_work_date (employee_id, work_date),
    KEY idx_attendances_work_date (work_date),
    CONSTRAINT fk_attendances_employee
        FOREIGN KEY (employee_id) REFERENCES employees (id)
        ON DELETE CASCADE
        ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS vendors (
    id BIGINT NOT NULL AUTO_INCREMENT,
    vendor_code VARCHAR(50) NOT NULL,
    business_registration_no VARCHAR(20) NOT NULL,
    name VARCHAR(100) NOT NULL,
    representative_name VARCHAR(100) NOT NULL,
    business_type VARCHAR(100) NULL,
    business_item VARCHAR(100) NULL,
    contact_name VARCHAR(100) NULL,
    contact_phone VARCHAR(30) NULL,
    email VARCHAR(100) NULL,
    address VARCHAR(255) NULL,
    bank_name VARCHAR(50) NULL,
    bank_account_no VARCHAR(50) NULL,
    bank_account_holder VARCHAR(100) NULL,
    bankbook_copy_file_path VARCHAR(255) NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    memo VARCHAR(255) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_vendors_vendor_code (vendor_code),
    UNIQUE KEY uk_vendors_business_registration_no (business_registration_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS warehouses (
    id BIGINT NOT NULL AUTO_INCREMENT,
    warehouse_code VARCHAR(50) NOT NULL,
    warehouse_name VARCHAR(100) NOT NULL,
    location VARCHAR(255) NULL,
    manager_name VARCHAR(100) NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_warehouses_code (warehouse_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS items (
    id BIGINT NOT NULL AUTO_INCREMENT,
    item_code VARCHAR(50) NOT NULL,
    name VARCHAR(100) NOT NULL,
    item_type VARCHAR(30) NOT NULL DEFAULT 'FINISHED_GOOD',
    category VARCHAR(100) NOT NULL,
    spec VARCHAR(100) NOT NULL,
    barcode VARCHAR(100) NULL,
    unit VARCHAR(30) NOT NULL,
    default_vendor_id BIGINT NULL,
    standard_cost DECIMAL(15,2) NOT NULL DEFAULT 0.00,
    unit_price DECIMAL(15,2) NOT NULL DEFAULT 0.00,
    safety_stock INT NOT NULL DEFAULT 0,
    is_active TINYINT(1) NOT NULL DEFAULT 1,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_items_item_code (item_code),
    UNIQUE KEY uk_items_name (name),
    UNIQUE KEY uk_items_barcode (barcode),
    KEY idx_items_default_vendor_id (default_vendor_id),
    CONSTRAINT fk_items_default_vendor
        FOREIGN KEY (default_vendor_id) REFERENCES vendors (id)
        ON DELETE SET NULL
        ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS inventories (
    id BIGINT NOT NULL AUTO_INCREMENT,
    item_id BIGINT NOT NULL,
    warehouse_id BIGINT NOT NULL,
    location VARCHAR(100) NOT NULL,
    current_stock INT NOT NULL DEFAULT 0,
    allocated_stock INT NOT NULL DEFAULT 0,
    safety_stock INT NOT NULL DEFAULT 0,
    average_cost DECIMAL(19,4) NOT NULL DEFAULT 0.0000,
    inventory_value DECIMAL(19,4) NOT NULL DEFAULT 0.0000,
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_inventories_item_warehouse (item_id, warehouse_id),
    KEY idx_inventories_item_id (item_id),
    KEY idx_inventories_warehouse_id (warehouse_id),
    CONSTRAINT fk_inventories_item
        FOREIGN KEY (item_id) REFERENCES items (id)
        ON DELETE RESTRICT
        ON UPDATE CASCADE,
    CONSTRAINT fk_inventories_warehouse
        FOREIGN KEY (warehouse_id) REFERENCES warehouses (id)
        ON DELETE RESTRICT
        ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS purchase_orders (
    id BIGINT NOT NULL AUTO_INCREMENT,
    purchase_order_no VARCHAR(50) NOT NULL,
    vendor_id BIGINT NOT NULL,
    warehouse_id BIGINT NOT NULL,
    requested_by_member_id BIGINT NOT NULL,
    approved_by_member_id BIGINT NULL,
    order_date DATE NOT NULL,
    expected_date DATE NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'REQUESTED',
    total_amount DECIMAL(15,2) NOT NULL DEFAULT 0.00,
    note VARCHAR(255) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_purchase_orders_no (purchase_order_no),
    KEY idx_purchase_orders_vendor_id (vendor_id),
    KEY idx_purchase_orders_warehouse_id (warehouse_id),
    KEY idx_purchase_orders_requested_by (requested_by_member_id),
    KEY idx_purchase_orders_approved_by (approved_by_member_id),
    CONSTRAINT fk_purchase_orders_vendor
        FOREIGN KEY (vendor_id) REFERENCES vendors (id)
        ON DELETE RESTRICT
        ON UPDATE CASCADE,
    CONSTRAINT fk_purchase_orders_warehouse
        FOREIGN KEY (warehouse_id) REFERENCES warehouses (id)
        ON DELETE RESTRICT
        ON UPDATE CASCADE,
    CONSTRAINT fk_purchase_orders_requested_by
        FOREIGN KEY (requested_by_member_id) REFERENCES members (id)
        ON DELETE RESTRICT
        ON UPDATE CASCADE,
    CONSTRAINT fk_purchase_orders_approved_by
        FOREIGN KEY (approved_by_member_id) REFERENCES members (id)
        ON DELETE SET NULL
        ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE inventories
    ADD COLUMN IF NOT EXISTS location VARCHAR(100) NULL AFTER warehouse_id;

ALTER TABLE purchase_orders
    ADD COLUMN IF NOT EXISTS warehouse_id BIGINT NULL AFTER vendor_id;

CREATE TABLE IF NOT EXISTS purchase_order_items (
    id BIGINT NOT NULL AUTO_INCREMENT,
    purchase_order_id BIGINT NOT NULL,
    item_id BIGINT NOT NULL,
    quantity INT NOT NULL,
    unit VARCHAR(30) NOT NULL,
    unit_price DECIMAL(15,2) NOT NULL,
    supply_amount DECIMAL(15,2) NOT NULL DEFAULT 0.00,
    tax_amount DECIMAL(15,2) NOT NULL DEFAULT 0.00,
    line_amount DECIMAL(15,2) NOT NULL,
    expected_date DATE NULL,
    note VARCHAR(255) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_purchase_order_items_order_id (purchase_order_id),
    KEY idx_purchase_order_items_item_id (item_id),
    CONSTRAINT chk_purchase_order_items_quantity_positive CHECK (quantity > 0),
    CONSTRAINT chk_purchase_order_items_unit_price_non_negative CHECK (unit_price >= 0),
    CONSTRAINT chk_purchase_order_items_supply_amount_non_negative CHECK (supply_amount >= 0),
    CONSTRAINT chk_purchase_order_items_tax_amount_non_negative CHECK (tax_amount >= 0),
    CONSTRAINT chk_purchase_order_items_line_amount_matches CHECK (line_amount = supply_amount + tax_amount),
    CONSTRAINT fk_purchase_order_items_order
        FOREIGN KEY (purchase_order_id) REFERENCES purchase_orders (id)
        ON DELETE CASCADE
        ON UPDATE CASCADE,
    CONSTRAINT fk_purchase_order_items_item
        FOREIGN KEY (item_id) REFERENCES items (id)
        ON DELETE RESTRICT
        ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS audit_logs (
    id BIGINT NOT NULL AUTO_INCREMENT,
    member_id BIGINT NULL,
    action VARCHAR(100) NOT NULL,
    target_type VARCHAR(50) NOT NULL,
    target_id BIGINT NULL,
    details TEXT NULL,
    ip_address VARCHAR(64) NULL,
    user_agent VARCHAR(255) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_audit_logs_member_id (member_id),
    KEY idx_audit_logs_target (target_type, target_id),
    CONSTRAINT fk_audit_logs_member
        FOREIGN KEY (member_id) REFERENCES members (id)
        ON DELETE SET NULL
        ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS payrolls (
    id BIGINT NOT NULL AUTO_INCREMENT,
    employee_id BIGINT NOT NULL,
    pay_month CHAR(7) NOT NULL,
    base_salary DECIMAL(15,2) NOT NULL DEFAULT 0.00,
    allowance_amount DECIMAL(15,2) NOT NULL DEFAULT 0.00,
    deduction_amount DECIMAL(15,2) NOT NULL DEFAULT 0.00,
    net_salary DECIMAL(15,2) NOT NULL DEFAULT 0.00,
    status VARCHAR(30) NOT NULL DEFAULT 'DRAFT',
    paid_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_payrolls_employee_pay_month (employee_id, pay_month),
    CONSTRAINT fk_payrolls_employee
        FOREIGN KEY (employee_id) REFERENCES employees (id)
        ON DELETE CASCADE
        ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS expenses (
    id BIGINT NOT NULL AUTO_INCREMENT,
    employee_id BIGINT NULL,
    expense_date DATE NOT NULL,
    category VARCHAR(50) NOT NULL,
    amount DECIMAL(15,2) NOT NULL,
    description VARCHAR(255) NOT NULL,
    receipt_file_path VARCHAR(255) NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'SUBMITTED',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_expenses_employee_id (employee_id),
    KEY idx_expenses_expense_date (expense_date),
    CONSTRAINT fk_expenses_employee
        FOREIGN KEY (employee_id) REFERENCES employees (id)
        ON DELETE SET NULL
        ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS stock_movements (
    id BIGINT NOT NULL AUTO_INCREMENT,
    item_id BIGINT NOT NULL,
    warehouse_id BIGINT NOT NULL,
    movement_type VARCHAR(30) NOT NULL,
    movement_reason VARCHAR(50) NOT NULL,
    reference_no VARCHAR(50) NULL,
    quantity INT NOT NULL,
    unit_cost DECIMAL(19,4) NOT NULL DEFAULT 0.0000,
    total_amount DECIMAL(19,4) NOT NULL DEFAULT 0.0000,
    before_quantity INT NOT NULL,
    after_quantity INT NOT NULL,
    reference_type VARCHAR(50) NULL,
    reference_id VARCHAR(50) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_stock_movements_item_id (item_id),
    KEY idx_stock_movements_warehouse_id (warehouse_id),
    KEY idx_stock_movements_ref_no (reference_no),
    KEY idx_stock_movements_reference (reference_type, reference_id),
    CONSTRAINT fk_stock_movements_item
        FOREIGN KEY (item_id) REFERENCES items (id)
        ON DELETE RESTRICT
        ON UPDATE CASCADE,
    CONSTRAINT fk_stock_movements_warehouse
        FOREIGN KEY (warehouse_id) REFERENCES warehouses (id)
        ON DELETE RESTRICT
        ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Payroll & Accounting Extensions
CREATE TABLE IF NOT EXISTS payroll_contracts (
    id BIGINT NOT NULL AUTO_INCREMENT,
    employee_id BIGINT NOT NULL,
    contract_no VARCHAR(50) NOT NULL,
    base_salary DECIMAL(15,2) NOT NULL,
    hourly_rate DECIMAL(15,2) NULL,
    contract_date DATE NOT NULL,
    expiry_date DATE NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    bonus_rule TEXT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_payroll_contracts_no (contract_no),
    CONSTRAINT fk_payroll_contracts_employee FOREIGN KEY (employee_id) REFERENCES employees (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS accounting_periods (
    id BIGINT NOT NULL AUTO_INCREMENT,
    fiscal_year INT NOT NULL,
    fiscal_month INT NOT NULL,
    start_date DATE NULL,
    end_date DATE NULL,
    status VARCHAR(30) NOT NULL,
    closed_at DATETIME NULL,
    closed_by VARCHAR(80) NULL,
    reopened_at DATETIME NULL,
    reopened_by VARCHAR(80) NULL,
    reopen_count INT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_accounting_period_year_month (fiscal_year, fiscal_month)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS accounting_closing_logs (
    id BIGINT NOT NULL AUTO_INCREMENT,
    accounting_period_id BIGINT NOT NULL,
    action_type VARCHAR(40) NOT NULL,
    from_status VARCHAR(30) NULL,
    to_status VARCHAR(30) NULL,
    actor VARCHAR(80) NOT NULL,
    action_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ip_address VARCHAR(80) NULL,
    message VARCHAR(1000) NULL,
    PRIMARY KEY (id),
    KEY idx_closing_logs_period (accounting_period_id),
    KEY idx_closing_logs_action_at (action_at),
    CONSTRAINT fk_closing_logs_period
        FOREIGN KEY (accounting_period_id) REFERENCES accounting_periods (id)
        ON DELETE CASCADE
        ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS accounts (
    id BIGINT NOT NULL AUTO_INCREMENT,
    code VARCHAR(20) NOT NULL,
    name VARCHAR(100) NOT NULL,
    english_name VARCHAR(100) NULL,
    type VARCHAR(30) NOT NULL,
    normal_balance VARCHAR(10) NOT NULL DEFAULT 'DEBIT',
    level INT NOT NULL DEFAULT 1,
    sort_order INT NOT NULL DEFAULT 0,
    description VARCHAR(255) NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    allow_posting TINYINT(1) NOT NULL DEFAULT 1,
    system_account TINYINT(1) NOT NULL DEFAULT 0,
    deleted TINYINT(1) NOT NULL DEFAULT 0,
    parent_code VARCHAR(20) NULL,
    parent_id BIGINT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_accounts_code (code),
    KEY idx_accounts_parent_id (parent_id),
    CONSTRAINT fk_accounts_parent FOREIGN KEY (parent_id) REFERENCES accounts (id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE accounts ADD COLUMN IF NOT EXISTS status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE';
ALTER TABLE accounts ADD COLUMN IF NOT EXISTS sort_order INT NOT NULL DEFAULT 0;
ALTER TABLE accounts ADD COLUMN IF NOT EXISTS deleted TINYINT(1) NOT NULL DEFAULT 0;
ALTER TABLE accounts ADD COLUMN IF NOT EXISTS allow_posting TINYINT(1) NOT NULL DEFAULT 1;
ALTER TABLE accounts ADD COLUMN IF NOT EXISTS system_account TINYINT(1) NOT NULL DEFAULT 0;


CREATE TABLE IF NOT EXISTS journal_entries (
    id BIGINT NOT NULL AUTO_INCREMENT,
    journal_no VARCHAR(50) NOT NULL,
    transaction_date DATE NOT NULL,
    description VARCHAR(255) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'DRAFT',
    source_type VARCHAR(50) NULL,
    source_id BIGINT NULL,
    total_debit DECIMAL(15,2) NOT NULL DEFAULT 0.00,
    total_credit DECIMAL(15,2) NOT NULL DEFAULT 0.00,
    created_by VARCHAR(100) NULL,
    fiscal_year INT NULL,
    fiscal_month INT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_journal_entries_no (journal_no),
    KEY idx_journal_entries_source (source_type, source_id),
    KEY idx_journal_entries_fiscal (fiscal_year, fiscal_month),
    KEY idx_journal_entries_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS journal_items (
    id BIGINT NOT NULL AUTO_INCREMENT,
    journal_entry_id BIGINT NOT NULL,
    account_id BIGINT NOT NULL,
    debit_amount DECIMAL(15,2) NOT NULL DEFAULT 0.00,
    credit_amount DECIMAL(15,2) NOT NULL DEFAULT 0.00,
    description VARCHAR(255) NULL,
    reference_type VARCHAR(50) NULL,
    reference_id BIGINT NULL,
    PRIMARY KEY (id),
    KEY idx_journal_items_entry (journal_entry_id),
    KEY idx_journal_items_account (account_id),
    CONSTRAINT fk_journal_items_entry FOREIGN KEY (journal_entry_id) REFERENCES journal_entries (id) ON DELETE CASCADE,
    CONSTRAINT fk_journal_items_account FOREIGN KEY (account_id) REFERENCES accounts (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS vouchers (
    id BIGINT NOT NULL AUTO_INCREMENT,
    voucher_no VARCHAR(255) NOT NULL,
    voucher_date DATE NOT NULL,
    voucher_type VARCHAR(50) NOT NULL,
    vat_type VARCHAR(50) NULL,
    vendor_id BIGINT NULL,
    vendor_name_snapshot VARCHAR(255) NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'DRAFT',
    description VARCHAR(255) NULL,
    source_type VARCHAR(50) NULL COMMENT '자동생성 원천 도메인 (VoucherSourceType enum)',
    source_reference_id BIGINT NULL COMMENT '원천 엔티티 ID (stock_movement.id 등)',
    journal_entry_id BIGINT NULL,
    created_by VARCHAR(255) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_vouchers_no (voucher_no),
    UNIQUE KEY uk_vouchers_journal_entry (journal_entry_id),
    CONSTRAINT fk_vouchers_journal_entry FOREIGN KEY (journal_entry_id) REFERENCES journal_entries (id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS voucher_lines (
    id BIGINT NOT NULL AUTO_INCREMENT,
    voucher_id BIGINT NOT NULL,
    line_no INT NOT NULL,
    account_id BIGINT NOT NULL,
    account_code VARCHAR(255) NOT NULL,
    account_name VARCHAR(255) NOT NULL,
    debit_credit VARCHAR(10) NOT NULL,
    supply_amount DECIMAL(38,2) NOT NULL,
    vat_amount DECIMAL(38,2) NOT NULL,
    total_amount DECIMAL(38,2) NOT NULL,
    quantity INT NULL,
    unit_price DECIMAL(38,2) NULL,
    description VARCHAR(255) NULL,
    sort_order INT NOT NULL,
    stock_movement_id BIGINT NULL COMMENT 'FK: stock_movements.id (nullable - 수동전표는 NULL)',
    movement_type VARCHAR(50) NULL COMMENT '원장 거래 구분 (감사 추적용 스냅샷)',
    movement_reference_no VARCHAR(50) NULL COMMENT '원장 참조번호 스냅샷',
    PRIMARY KEY (id),
    KEY idx_voucher_lines_voucher (voucher_id),
    CONSTRAINT fk_voucher_lines_voucher FOREIGN KEY (voucher_id) REFERENCES vouchers (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


-- Legacy local DBs can still carry the old `amount` column without a default,
-- which breaks startup seed inserts because the current entity no longer writes it.
ALTER TABLE journal_items
    ADD COLUMN IF NOT EXISTS amount DECIMAL(15,2) NOT NULL DEFAULT 0.00 AFTER account_id;

ALTER TABLE journal_items
    MODIFY COLUMN amount DECIMAL(15,2) NOT NULL DEFAULT 0.00;

-- Default Chart of Accounts Seeds
INSERT INTO accounts (code, name, type, normal_balance, level, status, allow_posting, system_account, deleted, sort_order) VALUES
('1001', '현금', 'ASSET', 'DEBIT', 1, 'ACTIVE', 1, 1, 0, 10),
('1002', '보통예금', 'ASSET', 'DEBIT', 1, 'ACTIVE', 1, 1, 0, 20),
('1003', '재고자산', 'ASSET', 'DEBIT', 1, 'ACTIVE', 1, 1, 0, 30),
('2001', '외상매입금', 'LIABILITY', 'CREDIT', 1, 'ACTIVE', 1, 1, 0, 10),
('2002', '미지급금(급여)', 'LIABILITY', 'CREDIT', 1, 'ACTIVE', 1, 1, 0, 20),
('4001', '제품매출', 'REVENUE', 'CREDIT', 1, 'ACTIVE', 1, 1, 0, 10),
('5001', '매출원가', 'EXPENSE', 'DEBIT', 1, 'ACTIVE', 1, 1, 0, 10),
('5002', '급여비용', 'EXPENSE', 'DEBIT', 1, 'ACTIVE', 1, 1, 0, 20),
('5003', '재고손실', 'EXPENSE', 'DEBIT', 1, 'ACTIVE', 1, 1, 0, 30)
ON DUPLICATE KEY UPDATE
    name = VALUES(name),
    type = VALUES(type),
    normal_balance = VALUES(normal_balance),
    level = VALUES(level),
    status = VALUES(status),
    allow_posting = VALUES(allow_posting),
    system_account = VALUES(system_account),
    deleted = VALUES(deleted),
    sort_order = VALUES(sort_order);

-- 재고-회계 연동: Voucher 및 VoucherLine 컬럼이 생성 시 추가 완료되어 ALTER 구문 제거함

CREATE TABLE IF NOT EXISTS warehouse_transfers (
    id BIGINT NOT NULL AUTO_INCREMENT,
    transfer_no VARCHAR(50) NOT NULL,
    source_warehouse_id BIGINT NOT NULL,
    target_warehouse_id BIGINT NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    remarks VARCHAR(255) NULL,
    requested_by_id BIGINT NOT NULL,
    approved_by_id BIGINT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    approved_at DATETIME NULL,
    completed_at DATETIME NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_warehouse_transfers_no (transfer_no),
    KEY idx_warehouse_transfers_status (status),
    CONSTRAINT fk_transfers_source_wh FOREIGN KEY (source_warehouse_id) REFERENCES warehouses (id),
    CONSTRAINT fk_transfers_target_wh FOREIGN KEY (target_warehouse_id) REFERENCES warehouses (id),
    CONSTRAINT fk_transfers_requested_by FOREIGN KEY (requested_by_id) REFERENCES members (id),
    CONSTRAINT fk_transfers_approved_by FOREIGN KEY (approved_by_id) REFERENCES members (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS warehouse_transfer_items (
    id BIGINT NOT NULL AUTO_INCREMENT,
    transfer_id BIGINT NOT NULL,
    item_id BIGINT NOT NULL,
    quantity INT NOT NULL,
    PRIMARY KEY (id),
    KEY idx_transfer_items_transfer (transfer_id),
    CONSTRAINT chk_transfer_items_qty CHECK (quantity > 0),
    CONSTRAINT fk_transfer_items_transfer FOREIGN KEY (transfer_id) REFERENCES warehouse_transfers (id) ON DELETE CASCADE,
    CONSTRAINT fk_transfer_items_item FOREIGN KEY (item_id) REFERENCES items (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

