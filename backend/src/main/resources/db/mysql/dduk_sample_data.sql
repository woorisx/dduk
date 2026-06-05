-- =================================================================
-- DDUK ERP Sample Seed: Amanti tea brand shared demo data
-- =================================================================
-- Purpose
-- - fill inventory / purchase / admin / HR / accounting dashboards with Amanti-based shared demo data
-- - provide enough demand + lead-time history for purchase recommendation
-- - provide anomaly rows for admin monitoring
-- - include stable AI/RPA and accounting demo history rows in this single file
--
-- Notes
-- - this file is startup-loaded sample data for fixed shared demos
-- - it assumes bootstrap schema + task_history_schema + anomaly_log_schema exist
-- - it is written to be re-runnable for the sample keys below
-- - it keeps all demo rows in one startup-managed file for easier maintenance

-- -----------------------------------------------------------------
-- cleanup for re-run
-- -----------------------------------------------------------------
-- ALTER TABLE items DROP COLUMN IF EXISTS active;
-- ALTER TABLE stock_movements DROP FOREIGN KEY fk_stock_movements_inventory;
-- ALTER TABLE stock_movements DROP COLUMN inventory_id;

SET FOREIGN_KEY_CHECKS = 0;

DELETE FROM purchase_order_items
WHERE purchase_order_id IN (
    SELECT id
    FROM purchase_orders
    WHERE purchase_order_no LIKE 'PO-AMANTE-AI-%' OR purchase_order_no LIKE 'PO-AMANTI-%'
);

DELETE FROM purchase_orders
WHERE purchase_order_no LIKE 'PO-AMANTE-AI-%' OR purchase_order_no LIKE 'PO-AMANTI-%';

DELETE FROM stock_movements
WHERE reference_type = 'SAMPLE_AMANTE' OR reference_type = 'SAMPLE_AMANTI';

DELETE FROM task_history
WHERE task_id LIKE 'sample-amante-%' OR task_id LIKE 'sample-amanti-%' OR task_id LIKE 'sample-demo-%';

DELETE FROM anomaly_logs
WHERE anomaly_key LIKE 'SAMPLE_AMANTE:%' OR anomaly_key LIKE 'SAMPLE_AMANTI:%';

DELETE FROM voucher_lines WHERE voucher_id IN (SELECT id FROM vouchers WHERE voucher_no LIKE 'DEMO-VCH-%');
DELETE FROM vouchers WHERE voucher_no LIKE 'DEMO-VCH-%';

DELETE FROM journal_items
WHERE journal_entry_id IN (
    SELECT id FROM journal_entries WHERE journal_no LIKE 'DEMO-JE-%'
);

DELETE FROM journal_entries
WHERE journal_no LIKE 'DEMO-JE-%';

-- DELETE FROM accounting_payroll_ledgers
-- WHERE created_by = 'demo-seed';

-- DELETE FROM accounting_closing_logs
-- WHERE actor = 'demo-seed';

-- DELETE FROM warehouse_transfer_items
-- WHERE transfer_id IN (
--     SELECT id FROM warehouse_transfers WHERE transfer_no LIKE 'TR-DEMO-%'
-- );

-- DELETE FROM warehouse_transfers
-- WHERE transfer_no LIKE 'TR-DEMO-%';

DELETE FROM notice
WHERE author_id = 'admin';

DELETE FROM expenses
WHERE employee_id IN (
    SELECT id FROM employees WHERE employee_no LIKE 'EMP-%'
);

DELETE FROM attendances
WHERE employee_id IN (
    SELECT id FROM employees WHERE employee_no LIKE 'EMP-%'
);

DELETE FROM payroll_contracts
WHERE employee_id IN (
    SELECT id FROM employees WHERE employee_no LIKE 'EMP-%'
);

DELETE FROM inventories
WHERE item_id IN (
    SELECT id FROM items WHERE item_code LIKE 'AMANTE-ITEM-%' OR item_code LIKE 'AMANTI-%'
);

DELETE FROM items
WHERE item_code LIKE 'AMANTE-ITEM-%' OR item_code LIKE 'AMANTI-%';

DELETE FROM employees
WHERE employee_no LIKE 'EMP-%';

DELETE FROM vendors
WHERE vendor_code LIKE 'V-AMANTE%' OR vendor_code LIKE 'V-AMANTI%' OR vendor_code IN ('V-TEA-PACK', 'V-GLASS-WARE', 'V-JEJU-FARM', 'V-HERB-IMPORT', 'V-CAFE-MALL', 'V-LOGIS');

SET FOREIGN_KEY_CHECKS = 1;

-- -----------------------------------------------------------------
-- vendors (거래처)
-- -----------------------------------------------------------------
INSERT INTO vendors (
    vendor_code,
    business_registration_no,
    name,
    representative_name,
    business_type,
    business_item,
    contact_name,
    contact_phone,
    email,
    address,
    status,
    memo,
    created_at,
    updated_at
)
VALUES
    ('V-AMANTI', '120-88-12345', '(주)아망티', '이서준', '제조/도소매', '홍차/허브차/식음료', '박철민 과장', '02-333-4455', 'contact@amantea.co.kr', '서울 마포구 창전로 5', 'ACTIVE', '차(Tea) 전문 수입 제조 및 카페 도소매 유통사', NOW(), NOW()),
    ('V-TEA-PACK', '214-85-98765', '대한다업 패키징', '김대현', '제조', '포장재/틴캔/박스', '최준호 대리', '031-777-8899', 'sales@daehanteapack.co.kr', '경기 안산시 단원구 산단로 45', 'ACTIVE', '티백용 필터 및 패키지 전문 제조업체', NOW(), NOW()),
    ('V-GLASS-WARE', '113-81-54321', '삼우글라스', '정삼우', '제조/도소매', '다기/내열유리용기', '이은영 팀장', '02-555-6677', 'info@samwooglass.co.kr', '서울 송파구 송파대로 120', 'ACTIVE', '티포트 및 텀블러/유리식기 수입유통사', NOW(), NOW()),
    ('V-JEJU-FARM', '609-92-11223', '제주 오가닉 다원', '강성민', '농업법인', '유기농녹차/말차원료', '강현우', '064-789-0123', 'farm@jejuorganic.co.kr', '제주 서귀포시 안덕면 녹차분재로 15', 'ACTIVE', '제주 다원 직송 유기농 차 원료 재배 농가', NOW(), NOW()),
    ('V-HERB-IMPORT', '105-84-00123', '글로벌허브 트레이딩', '마이클 스미스', '무역/도매', '허브원료/수입차', '김지연 대리', '02-222-3344', 'import@globalherb.co.kr', '서울 종로구 율곡로 33', 'ACTIVE', '유럽/아프리카 허브원료 직수입 무역회사', NOW(), NOW()),
    ('V-CAFE-MALL', '220-87-55667', '메가카페 식자재', '박성하', '도소매', '프랜차이즈 납품', '이동욱 주임', '02-999-8888', 'buyer@megacafe.co.kr', '서울 강남구 역삼로 88', 'ACTIVE', '대형 프랜차이즈 및 온오프라인 납품 유통망', NOW(), NOW()),
    ('V-LOGIS', '118-85-33445', '한진종합물류', '조현민', '서비스', '물류/택배/보관', '윤승재 과장', '02-1588-4600', 'logistics@hanjin.co.kr', '서울 중구 남대문로 63', 'ACTIVE', '전사 물류 배송 및 창고 3PL 계약 파트너', NOW(), NOW())
ON DUPLICATE KEY UPDATE
    name = VALUES(name),
    representative_name = VALUES(representative_name),
    business_type = VALUES(business_type),
    business_item = VALUES(business_item),
    contact_name = VALUES(contact_name),
    contact_phone = VALUES(contact_phone),
    email = VALUES(email),
    address = VALUES(address),
    status = VALUES(status),
    memo = VALUES(memo),
    updated_at = NOW();

-- -----------------------------------------------------------------
-- warehouses (창고)
-- -----------------------------------------------------------------
INSERT INTO warehouses (
    warehouse_code,
    warehouse_name,
    location,
    manager_name,
    status,
    created_at,
    updated_at
)
VALUES
    ('WH-RAW', '자재류창고', '김포 1센터', '이재훈', 'ACTIVE', NOW(), NOW()),
    ('WH-SEASON', '시즌상품창고', '고양 2센터', '박세진', 'ACTIVE', NOW(), NOW())
ON DUPLICATE KEY UPDATE
    warehouse_name = VALUES(warehouse_name),
    location = VALUES(location),
    manager_name = VALUES(manager_name),
    status = VALUES(status),
    updated_at = NOW();

-- -----------------------------------------------------------------
-- employees / contracts (임직원 및 계약)
-- -----------------------------------------------------------------
INSERT INTO employees (
    member_id,
    employee_no,
    name,
    department,
    position,
    employment_status,
    hire_date,
    email,
    phone,
    created_at,
    updated_at
)
VALUES
    (2, 'EMP-INV-001', '창고담당 김민수', 'inventory', 'Manager', 'ACTIVE', '2023-03-04', 'inventory.manager@dduk.local', '010-2000-3000', NOW(), NOW()),
    (3, 'EMP-HR-001', '인사담당 박지원', 'hr', 'Lead', 'ACTIVE', '2022-09-01', 'hr.lead@dduk.local', '010-2000-4000', NOW(), NOW()),
    (NULL, 'EMP-OPS-001', '운영담당 최유진', 'inventory', 'Staff', 'ACTIVE', '2024-01-08', 'ops.staff@dduk.local', '010-2000-5000', NOW(), NOW()),
    (NULL, 'EMP-ACC-001', '회계담당 이은지', 'hr', 'Manager', 'ACTIVE', '2023-07-15', 'accounting.manager@dduk.local', '010-2000-6000', NOW(), NOW()),
    (NULL, 'EMP-SAL-001', '영업담당 정우성', 'sales', 'Lead', 'ACTIVE', '2021-11-20', 'sales.lead@dduk.local', '010-2000-7000', NOW(), NOW()),
    (NULL, 'EMP-DEV-001', '개발담당 홍길동', 'development', 'Senior Engineer', 'ACTIVE', '2020-05-10', 'dev.senior@dduk.local', '010-2000-8000', NOW(), NOW()),
    (NULL, 'EMP-MFG-001', '생산담당 강철수', 'manufacturing', 'Staff', 'ACTIVE', '2024-02-15', 'mfg.staff@dduk.local', '010-2000-9000', NOW(), NOW())
ON DUPLICATE KEY UPDATE
    name = VALUES(name),
    department = VALUES(department),
    position = VALUES(position),
    employment_status = VALUES(employment_status),
    hire_date = VALUES(hire_date),
    phone = VALUES(phone),
    updated_at = NOW();

INSERT INTO payroll_contracts (
    employee_id,
    contract_no,
    base_salary,
    hourly_rate,
    contract_date,
    expiry_date,
    status,
    bonus_rule,
    created_at,
    updated_at
)
VALUES
    ((SELECT id FROM employees WHERE employee_no = 'EMP-INV-001'), 'CON-DEMO-EMP-INV-001', 4200000.00, NULL, '2025-01-01', NULL, 'ACTIVE', '{"bonusRate":0.08}', NOW(), NOW()),
    ((SELECT id FROM employees WHERE employee_no = 'EMP-HR-001'), 'CON-DEMO-EMP-HR-001', 4600000.00, NULL, '2025-01-01', NULL, 'ACTIVE', '{"bonusRate":0.10}', NOW(), NOW()),
    ((SELECT id FROM employees WHERE employee_no = 'EMP-OPS-001'), 'CON-DEMO-EMP-OPS-001', 3300000.00, NULL, '2025-01-01', NULL, 'ACTIVE', '{"bonusRate":0.05}', NOW(), NOW()),
    ((SELECT id FROM employees WHERE employee_no = 'EMP-ACC-001'), 'CON-DEMO-EMP-ACC-001', 4100000.00, NULL, '2025-01-01', NULL, 'ACTIVE', '{"bonusRate":0.07}', NOW(), NOW()),
    ((SELECT id FROM employees WHERE employee_no = 'EMP-SAL-001'), 'CON-DEMO-EMP-SAL-001', 4800000.00, NULL, '2025-01-01', NULL, 'ACTIVE', '{"bonusRate":0.12}', NOW(), NOW()),
    ((SELECT id FROM employees WHERE employee_no = 'EMP-DEV-001'), 'CON-DEMO-EMP-DEV-001', 5500000.00, NULL, '2025-01-01', NULL, 'ACTIVE', '{"bonusRate":0.15}', NOW(), NOW()),
    ((SELECT id FROM employees WHERE employee_no = 'EMP-MFG-001'), 'CON-DEMO-EMP-MFG-001', 3100000.00, NULL, '2025-01-01', NULL, 'ACTIVE', '{"bonusRate":0.04}', NOW(), NOW())
ON DUPLICATE KEY UPDATE
    employee_id = VALUES(employee_id),
    base_salary = VALUES(base_salary),
    hourly_rate = VALUES(hourly_rate),
    contract_date = VALUES(contract_date),
    expiry_date = VALUES(expiry_date),
    status = VALUES(status),
    bonus_rule = VALUES(bonus_rule),
    updated_at = NOW();

-- -----------------------------------------------------------------
-- items (아망티 차 상품 마스터)
-- -----------------------------------------------------------------
INSERT INTO items (
    item_code,
    name,
    item_type,
    category,
    spec,
    unit,
    default_vendor_id,
    standard_cost,
    unit_price,
    safety_stock,
    is_active,
    created_at,
    updated_at
)
VALUES
    ('AMANTI-ITEM-001', '아망티 스리랑카 실론 홍차 BOP (100g)', 'FINISHED_GOOD', '홍차', '잎차 / BOP / 100g 지퍼백', 'EA', (SELECT id FROM vendors WHERE vendor_code = 'V-AMANTI'), 6500.00, 12000.00, 20, 1, NOW(), NOW()),
    ('AMANTI-ITEM-002', '아망티 얼그레이 클래식 삼각티백 (20입)', 'FINISHED_GOOD', '홍차', '삼각티백 / 1.5g x 20입 지퍼백', 'EA', (SELECT id FROM vendors WHERE vendor_code = 'V-AMANTI'), 4500.00, 8500.00, 25, 1, NOW(), NOW()),
    ('AMANTI-ITEM-003', '아망티 인도 아쌈 CTC 잎차 (250g)', 'FINISHED_GOOD', '홍차', '잎차 / CTC / 250g 벌크', 'EA', (SELECT id FROM vendors WHERE vendor_code = 'V-AMANTI'), 9800.00, 18000.00, 15, 1, NOW(), NOW()),
    ('AMANTI-ITEM-004', '아망티 카모마일 리프레쉬 삼각티백 (20입)', 'FINISHED_GOOD', '허브차', '삼각티백 / 1.2g x 20입 지퍼백', 'EA', (SELECT id FROM vendors WHERE vendor_code = 'V-AMANTI'), 4800.00, 9000.00, 30, 1, NOW(), NOW()),
    ('AMANTI-ITEM-005', '아망티 페퍼민트 허브차 잎차 (80g)', 'FINISHED_GOOD', '허브차', '잎차 / 페퍼민트 100% / 80g', 'EA', (SELECT id FROM vendors WHERE vendor_code = 'V-AMANTI'), 5500.00, 11000.00, 20, 1, NOW(), NOW()),
    ('AMANTI-ITEM-006', '아망티 프리미엄 루이보스 클래식 잎차 (150g)', 'FINISHED_GOOD', '허브차', '잎차 / 루이보스 100% / 150g', 'EA', (SELECT id FROM vendors WHERE vendor_code = 'V-AMANTI'), 7000.00, 13000.00, 18, 1, NOW(), NOW()),
    ('AMANTI-ITEM-007', '아망티 스윗 피치 블랙티 삼각티백 (20입)', 'FINISHED_GOOD', '블렌딩티', '삼각티백 / 복숭아향 블렌딩 / 2g x 20입', 'EA', (SELECT id FROM vendors WHERE vendor_code = 'V-AMANTI'), 5800.00, 10500.00, 15, 1, NOW(), NOW()),
    ('AMANTI-ITEM-008', '아망티 잉글리쉬 브렉퍼스트 블렌드 잎차 (100g)', 'FINISHED_GOOD', '홍차', '잎차 / 아쌈+실론 블렌드 / 100g', 'EA', (SELECT id FROM vendors WHERE vendor_code = 'V-AMANTI'), 6800.00, 12500.00, 12, 1, NOW(), NOW()),
    ('AMANTI-ITEM-009', '아망티 크림 카라멜 루이보스 블렌드 (100g)', 'FINISHED_GOOD', '블렌딩티', '잎차 / 루이보스+카라멜향 / 100g', 'EA', (SELECT id FROM vendors WHERE vendor_code = 'V-AMANTI'), 8200.00, 15000.00, 10, 1, NOW(), NOW()),
    ('AMANTI-ITEM-010', '아망티 제주 유기농 말차 가루 (100g)', 'FINISHED_GOOD', '가루차', '가루차 / 말차분말 100% / 100g캔', 'EA', (SELECT id FROM vendors WHERE vendor_code = 'V-JEJU-FARM'), 11000.00, 20000.00, 15, 1, NOW(), NOW()),
    ('AMANTI-ITEM-011', '아망티 시트러스 블라썸 꽃차 삼각티백 (20입)', 'FINISHED_GOOD', '꽃차', '삼각티백 / 귤꽃+국화 블렌드 / 20입', 'EA', (SELECT id FROM vendors WHERE vendor_code = 'V-AMANTI'), 6000.00, 11500.00, 8, 1, NOW(), NOW()),
    ('AMANTI-ITEM-012', '아망티 내열유리 티포트 (600ml)', 'FINISHED_GOOD', '차도구', '다기 / 내열유리포트 / 600ml', 'EA', (SELECT id FROM vendors WHERE vendor_code = 'V-GLASS-WARE'), 8500.00, 15000.00, 10, 1, NOW(), NOW()),
    ('AMANTI-ITEM-013', '아망티 스테인리스 티 스트레이너 거름망', 'FINISHED_GOOD', '차도구', '다기 / 거름망 / 스테인리스 304', 'EA', (SELECT id FROM vendors WHERE vendor_code = 'V-GLASS-WARE'), 3500.00, 6500.00, 12, 1, NOW(), NOW()),
    ('AMANTI-RAW-001', '아망티 삼각 티백 PLA 필터 (롤)', 'RAW_MATERIAL', '원자재', '부자재 / 생분해 PLA / 롤', 'ROLL', (SELECT id FROM vendors WHERE vendor_code = 'V-TEA-PACK'), 45000.00, 0.00, 10, 1, NOW(), NOW()),
    ('AMANTI-RAW-002', '아망티 건조 카모마일 꽃잎 (원료/kg)', 'RAW_MATERIAL', '원자재', '원재료 / 이집트산 꽃잎 / kg', 'KG', (SELECT id FROM vendors WHERE vendor_code = 'V-HERB-IMPORT'), 15000.00, 0.00, 40, 1, NOW(), NOW()),
    ('AMANTI-RAW-003', '아망티 스리랑카 홍차 원엽 (원료/kg)', 'RAW_MATERIAL', '원자재', '원재료 / 실론 홍차엽 / kg', 'KG', (SELECT id FROM vendors WHERE vendor_code = 'V-AMANTI'), 18000.00, 0.00, 50, 1, NOW(), NOW()),
    ('AMANTI-RAW-004', '아망티 티백용 알루미늄 개별 포장지 (1000매)', 'RAW_MATERIAL', '원자재', '부자재 / 알루미늄 포장재 / 1000매', 'BOX', (SELECT id FROM vendors WHERE vendor_code = 'V-TEA-PACK'), 12000.00, 0.00, 8, 1, NOW(), NOW())
ON DUPLICATE KEY UPDATE
    name = VALUES(name),
    category = VALUES(category),
    spec = VALUES(spec),
    unit = VALUES(unit),
    default_vendor_id = VALUES(default_vendor_id),
    standard_cost = VALUES(standard_cost),
    unit_price = VALUES(unit_price),
    safety_stock = VALUES(safety_stock),
    is_active = VALUES(is_active),
    updated_at = NOW();

-- -----------------------------------------------------------------
-- inventories (재고 수준 설정)
-- -----------------------------------------------------------------
INSERT INTO inventories (
    item_id,
    warehouse_id,
    location,
    current_stock,
    allocated_stock,
    safety_stock,
    average_cost,
    inventory_value,
    version,
    created_at,
    updated_at
)
VALUES
    ((SELECT id FROM items WHERE item_code = 'AMANTI-ITEM-001'), (SELECT id FROM warehouses WHERE warehouse_code = 'WH-SEASON'), 'WH-SEASON', 9, 2, 20, 6500.0000, 58500.0000, 0, NOW(), NOW()),
    ((SELECT id FROM items WHERE item_code = 'AMANTI-ITEM-002'), (SELECT id FROM warehouses WHERE warehouse_code = 'WH-SEASON'), 'WH-SEASON', 6, 1, 25, 4500.0000, 27000.0000, 0, NOW(), NOW()),
    ((SELECT id FROM items WHERE item_code = 'AMANTI-ITEM-003'), (SELECT id FROM warehouses WHERE warehouse_code = 'WH-SEASON'), 'WH-SEASON', 18, 3, 15, 9800.0000, 176400.0000, 0, NOW(), NOW()),
    ((SELECT id FROM items WHERE item_code = 'AMANTI-ITEM-004'), (SELECT id FROM warehouses WHERE warehouse_code = 'WH-SEASON'), 'WH-SEASON', 0, 0, 30, 4800.0000, 0.0000, 0, NOW(), NOW()),
    ((SELECT id FROM items WHERE item_code = 'AMANTI-ITEM-005'), (SELECT id FROM warehouses WHERE warehouse_code = 'WH-SEASON'), 'WH-SEASON', 11, 18, 20, 5500.0000, 60500.0000, 0, NOW(), NOW()),
    ((SELECT id FROM items WHERE item_code = 'AMANTI-ITEM-006'), (SELECT id FROM warehouses WHERE warehouse_code = 'WH-SEASON'), 'WH-SEASON', 24, 2, 18, 7000.0000, 168000.0000, 0, NOW(), NOW()),
    ((SELECT id FROM items WHERE item_code = 'AMANTI-ITEM-007'), (SELECT id FROM warehouses WHERE warehouse_code = 'WH-SEASON'), 'WH-SEASON', 14, 2, 15, 5800.0000, 81200.0000, 0, NOW(), NOW()),
    ((SELECT id FROM items WHERE item_code = 'AMANTI-ITEM-008'), (SELECT id FROM warehouses WHERE warehouse_code = 'WH-SEASON'), 'WH-SEASON', 25, 0, 12, 6800.0000, 170000.0000, 0, NOW() - INTERVAL 110 DAY, NOW() - INTERVAL 110 DAY),
    ((SELECT id FROM items WHERE item_code = 'AMANTI-ITEM-009'), (SELECT id FROM warehouses WHERE warehouse_code = 'WH-SEASON'), 'WH-SEASON', 5, 1, 10, 8200.0000, 41000.0000, 0, NOW(), NOW()),
    ((SELECT id FROM items WHERE item_code = 'AMANTI-ITEM-010'), (SELECT id FROM warehouses WHERE warehouse_code = 'WH-SEASON'), 'WH-SEASON', 16, 1, 15, 11000.0000, 176000.0000, 0, NOW(), NOW()),
    ((SELECT id FROM items WHERE item_code = 'AMANTI-ITEM-011'), (SELECT id FROM warehouses WHERE warehouse_code = 'WH-SEASON'), 'WH-SEASON', 2, 0, 8, 6000.0000, 12000.0000, 0, NOW(), NOW()),
    ((SELECT id FROM items WHERE item_code = 'AMANTI-ITEM-012'), (SELECT id FROM warehouses WHERE warehouse_code = 'WH-SEASON'), 'WH-SEASON', 12, 2, 10, 8500.0000, 102000.0000, 0, NOW(), NOW()),
    ((SELECT id FROM items WHERE item_code = 'AMANTI-ITEM-013'), (SELECT id FROM warehouses WHERE warehouse_code = 'WH-SEASON'), 'WH-SEASON', 4, 1, 12, 3500.0000, 14000.0000, 0, NOW(), NOW()),
    ((SELECT id FROM items WHERE item_code = 'AMANTI-RAW-001'), (SELECT id FROM warehouses WHERE warehouse_code = 'WH-RAW'), 'WH-RAW', 22, 0, 10, 45000.0000, 990000.0000, 0, NOW(), NOW()),
    ((SELECT id FROM items WHERE item_code = 'AMANTI-RAW-002'), (SELECT id FROM warehouses WHERE warehouse_code = 'WH-RAW'), 'WH-RAW', 120, 0, 40, 15000.0000, 1800000.0000, 0, NOW(), NOW()),
    ((SELECT id FROM items WHERE item_code = 'AMANTI-RAW-003'), (SELECT id FROM warehouses WHERE warehouse_code = 'WH-RAW'), 'WH-RAW', 4, 0, 50, 18000.0000, 72000.0000, 0, NOW(), NOW()),
    ((SELECT id FROM items WHERE item_code = 'AMANTI-RAW-004'), (SELECT id FROM warehouses WHERE warehouse_code = 'WH-RAW'), 'WH-RAW', 15, 0, 8, 12000.0000, 180000.0000, 0, NOW(), NOW())
ON DUPLICATE KEY UPDATE
    current_stock = VALUES(current_stock),
    allocated_stock = VALUES(allocated_stock),
    safety_stock = VALUES(safety_stock),
    average_cost = VALUES(average_cost),
    inventory_value = VALUES(inventory_value),
    version = VALUES(version),
    updated_at = NOW();

-- -----------------------------------------------------------------
-- purchase orders (발주 내역 설정)
-- -----------------------------------------------------------------
INSERT INTO purchase_orders (
    purchase_order_no,
    vendor_id,
    warehouse_id,
    requested_by_member_id,
    approved_by_member_id,
    order_date,
    expected_date,
    status,
    total_amount,
    note,
    created_at,
    updated_at
)
VALUES
    ('PO-AMANTI-AI-001', (SELECT id FROM vendors WHERE vendor_code = 'V-AMANTI'), (SELECT id FROM warehouses WHERE warehouse_code = 'WH-SEASON'), 2, 1, CURRENT_DATE - INTERVAL 150 DAY, CURRENT_DATE - INTERVAL 143 DAY, 'COMPLETED', 170500.00, '실론홍차 및 얼그레이 티백 초도 물량', NOW(), NOW()),
    ('PO-AMANTI-AI-002', (SELECT id FROM vendors WHERE vendor_code = 'V-AMANTI'), (SELECT id FROM warehouses WHERE warehouse_code = 'WH-SEASON'), 2, 1, CURRENT_DATE - INTERVAL 95 DAY, CURRENT_DATE - INTERVAL 88 DAY, 'RECEIVED', 223300.00, '아쌈 원엽 및 루이보스 정기 입고', NOW(), NOW()),
    ('PO-AMANTI-AI-003', (SELECT id FROM vendors WHERE vendor_code = 'V-AMANTI'), (SELECT id FROM warehouses WHERE warehouse_code = 'WH-SEASON'), 2, 1, CURRENT_DATE - INTERVAL 40 DAY, CURRENT_DATE - INTERVAL 33 DAY, 'APPROVED', 146300.00, '실론 홍차 및 페퍼민트 추가 보충', NOW(), NOW()),
    ('PO-AMANTI-AI-004', (SELECT id FROM vendors WHERE vendor_code = 'V-AMANTI'), (SELECT id FROM warehouses WHERE warehouse_code = 'WH-SEASON'), 2, 1, CURRENT_DATE - INTERVAL 18 DAY, CURRENT_DATE - INTERVAL 10 DAY, 'REQUESTED', 105600.00, '카모마일 티백 긴급 발주 요청', NOW(), NOW()),
    ('PO-AMANTI-AI-005', (SELECT id FROM vendors WHERE vendor_code = 'V-JEJU-FARM'), (SELECT id FROM warehouses WHERE warehouse_code = 'WH-SEASON'), 2, 1, CURRENT_DATE - INTERVAL 120 DAY, CURRENT_DATE - INTERVAL 115 DAY, 'COMPLETED', 181500.00, '제주 유기농 말차 가루 신규 매입', NOW(), NOW()),
    ('PO-AMANTI-AI-006', (SELECT id FROM vendors WHERE vendor_code = 'V-TEA-PACK'), (SELECT id FROM warehouses WHERE warehouse_code = 'WH-RAW'), 2, 1, CURRENT_DATE - INTERVAL 80 DAY, CURRENT_DATE - INTERVAL 75 DAY, 'COMPLETED', 379500.00, '티백 필터 및 알루미늄 포장재 보충', NOW(), NOW()),
    ('PO-AMANTI-AI-007', (SELECT id FROM vendors WHERE vendor_code = 'V-GLASS-WARE'), (SELECT id FROM warehouses WHERE warehouse_code = 'WH-SEASON'), 2, 1, CURRENT_DATE - INTERVAL 60 DAY, CURRENT_DATE - INTERVAL 55 DAY, 'COMPLETED', 217250.00, '티포트 및 티 스트레이너 세트 다기류 수입', NOW(), NOW()),
    ('PO-AMANTI-AI-008', (SELECT id FROM vendors WHERE vendor_code = 'V-HERB-IMPORT'), (SELECT id FROM warehouses WHERE warehouse_code = 'WH-RAW'), 2, 1, CURRENT_DATE - INTERVAL 20 DAY, CURRENT_DATE - INTERVAL 13 DAY, 'APPROVED', 330000.00, '이집트산 카모마일 꽃잎 수입 승인건', NOW(), NOW()),
    ('PO-AMANTI-AI-009', (SELECT id FROM vendors WHERE vendor_code = 'V-JEJU-FARM'), (SELECT id FROM warehouses WHERE warehouse_code = 'WH-SEASON'), 2, 1, CURRENT_DATE - INTERVAL 5 DAY, CURRENT_DATE + INTERVAL 2 DAY, 'REQUESTED', 121000.00, '말차 가루 재고 부족 대비 보충 발주', NOW(), NOW()),
    ('PO-AMANTI-AI-010', (SELECT id FROM vendors WHERE vendor_code = 'V-TEA-PACK'), (SELECT id FROM warehouses WHERE warehouse_code = 'WH-RAW'), 2, NULL, CURRENT_DATE - INTERVAL 12 DAY, NULL, 'REJECTED', 198000.00, '규격 미달 및 예산 초과로 인한 반려건', NOW(), NOW())
ON DUPLICATE KEY UPDATE
    vendor_id = VALUES(vendor_id),
    warehouse_id = VALUES(warehouse_id),
    requested_by_member_id = VALUES(requested_by_member_id),
    approved_by_member_id = VALUES(approved_by_member_id),
    order_date = VALUES(order_date),
    expected_date = VALUES(expected_date),
    status = VALUES(status),
    total_amount = VALUES(total_amount),
    note = VALUES(note),
    updated_at = NOW();

INSERT INTO purchase_order_items (
    purchase_order_id,
    item_id,
    quantity,
    unit,
    unit_price,
    supply_amount,
    tax_amount,
    line_amount,
    expected_date,
    note,
    created_at,
    updated_at
)
VALUES
    ((SELECT id FROM purchase_orders WHERE purchase_order_no = 'PO-AMANTI-AI-001'), (SELECT id FROM items WHERE item_code = 'AMANTI-ITEM-001'), 10, 'EA', 6500.00, 65000.00, 6500.00, 71500.00, CURRENT_DATE - INTERVAL 143 DAY, '실론홍차 BOP', NOW(), NOW()),
    ((SELECT id FROM purchase_orders WHERE purchase_order_no = 'PO-AMANTI-AI-001'), (SELECT id FROM items WHERE item_code = 'AMANTI-ITEM-002'), 20, 'EA', 4500.00, 90000.00, 9000.00, 99000.00, CURRENT_DATE - INTERVAL 143 DAY, '얼그레이 삼각티백', NOW(), NOW()),
    ((SELECT id FROM purchase_orders WHERE purchase_order_no = 'PO-AMANTI-AI-002'), (SELECT id FROM items WHERE item_code = 'AMANTI-ITEM-003'), 10, 'EA', 9800.00, 98000.00, 9800.00, 107800.00, CURRENT_DATE - INTERVAL 88 DAY, '아쌈 CTC 잎차', NOW(), NOW()),
    ((SELECT id FROM purchase_orders WHERE purchase_order_no = 'PO-AMANTI-AI-002'), (SELECT id FROM items WHERE item_code = 'AMANTI-ITEM-006'), 15, 'EA', 7000.00, 105000.00, 10500.00, 115500.00, CURRENT_DATE - INTERVAL 88 DAY, '루이보스 클래식', NOW(), NOW()),
    ((SELECT id FROM purchase_orders WHERE purchase_order_no = 'PO-AMANTI-AI-003'), (SELECT id FROM items WHERE item_code = 'AMANTI-ITEM-001'), 12, 'EA', 6500.00, 78000.00, 7800.00, 85800.00, CURRENT_DATE - INTERVAL 33 DAY, '실론홍차 추가분', NOW(), NOW()),
    ((SELECT id FROM purchase_orders WHERE purchase_order_no = 'PO-AMANTI-AI-003'), (SELECT id FROM items WHERE item_code = 'AMANTI-ITEM-005'), 10, 'EA', 5500.00, 55000.00, 5500.00, 60500.00, CURRENT_DATE - INTERVAL 33 DAY, '페퍼민트 잎차', NOW(), NOW()),
    ((SELECT id FROM purchase_orders WHERE purchase_order_no = 'PO-AMANTI-AI-004'), (SELECT id FROM items WHERE item_code = 'AMANTI-ITEM-004'), 20, 'EA', 4800.00, 96000.00, 9600.00, 105600.00, CURRENT_DATE - INTERVAL 10 DAY, '카모마일 티백 긴급', NOW(), NOW()),
    ((SELECT id FROM purchase_orders WHERE purchase_order_no = 'PO-AMANTI-AI-005'), (SELECT id FROM items WHERE item_code = 'AMANTI-ITEM-010'), 15, 'EA', 11000.00, 165000.00, 16500.00, 181500.00, CURRENT_DATE - INTERVAL 115 DAY, '말차가루 초도물량', NOW(), NOW()),
    ((SELECT id FROM purchase_orders WHERE purchase_order_no = 'PO-AMANTI-AI-006'), (SELECT id FROM items WHERE item_code = 'AMANTI-RAW-001'), 5, 'ROLL', 45000.00, 225000.00, 22500.00, 247500.00, CURRENT_DATE - INTERVAL 75 DAY, '삼각 PLA 필터', NOW(), NOW()),
    ((SELECT id FROM purchase_orders WHERE purchase_order_no = 'PO-AMANTI-AI-006'), (SELECT id FROM items WHERE item_code = 'AMANTI-RAW-004'), 10, 'BOX', 12000.00, 120000.00, 12000.00, 132000.00, CURRENT_DATE - INTERVAL 75 DAY, '알루미늄 개별포장지', NOW(), NOW()),
    ((SELECT id FROM purchase_orders WHERE purchase_order_no = 'PO-AMANTI-AI-007'), (SELECT id FROM items WHERE item_code = 'AMANTI-ITEM-012'), 15, 'EA', 8500.00, 127500.00, 12750.00, 140250.00, CURRENT_DATE - INTERVAL 55 DAY, '내열유리 티포트', NOW(), NOW()),
    ((SELECT id FROM purchase_orders WHERE purchase_order_no = 'PO-AMANTI-AI-007'), (SELECT id FROM items WHERE item_code = 'AMANTI-ITEM-013'), 20, 'EA', 3500.00, 70000.00, 7000.00, 77000.00, CURRENT_DATE - INTERVAL 55 DAY, '티 스트레이너 거름망', NOW(), NOW()),
    ((SELECT id FROM purchase_orders WHERE purchase_order_no = 'PO-AMANTI-AI-008'), (SELECT id FROM items WHERE item_code = 'AMANTI-RAW-002'), 20, 'KG', 15000.00, 300000.00, 30000.00, 330000.00, CURRENT_DATE - INTERVAL 13 DAY, '이집트산 카모마일 꽃잎', NOW(), NOW()),
    ((SELECT id FROM purchase_orders WHERE purchase_order_no = 'PO-AMANTI-AI-009'), (SELECT id FROM items WHERE item_code = 'AMANTI-ITEM-010'), 10, 'EA', 11000.00, 110000.00, 11000.00, 121000.00, CURRENT_DATE + INTERVAL 2 DAY, '추가 말차 분말', NOW(), NOW()),
    ((SELECT id FROM purchase_orders WHERE purchase_order_no = 'PO-AMANTI-AI-010'), (SELECT id FROM items WHERE item_code = 'AMANTI-RAW-001'), 4, 'ROLL', 45000.00, 180000.00, 18000.00, 198000.00, NULL, '반려된 예비 필터', NOW(), NOW());

-- -----------------------------------------------------------------
-- stock movements (수불부 기록 설정)
-- -----------------------------------------------------------------
INSERT INTO stock_movements (
    item_id,
    warehouse_id,
    movement_type,
    movement_reason,
    reference_no,
    quantity,
    unit_cost,
    total_amount,
    before_quantity,
    after_quantity,
    reference_type,
    reference_id,
    created_at
)
VALUES
    ((SELECT id FROM items WHERE item_code = 'AMANTI-ITEM-001'), (SELECT id FROM warehouses WHERE warehouse_code = 'WH-SEASON'), 'INBOUND', 'PURCHASE_RECEIVED', 'SM-AMANTI-001', 10, 6500.0000, 65000.0000, 0, 10, 'SAMPLE_AMANTI', 'AMANTI-ITEM-001-1', NOW() - INTERVAL 140 DAY),
    ((SELECT id FROM items WHERE item_code = 'AMANTI-ITEM-001'), (SELECT id FROM warehouses WHERE warehouse_code = 'WH-SEASON'), 'OUTBOUND', 'SALES_SHIPPED', 'SM-AMANTI-002', 3, 6500.0000, 19500.0000, 10, 7, 'SAMPLE_AMANTI', 'AMANTI-ITEM-001-2', NOW() - INTERVAL 28 DAY),
    ((SELECT id FROM items WHERE item_code = 'AMANTI-ITEM-001'), (SELECT id FROM warehouses WHERE warehouse_code = 'WH-SEASON'), 'OUTBOUND', 'SALES_SHIPPED', 'SM-AMANTI-003', 2, 6500.0000, 13000.0000, 7, 5, 'SAMPLE_AMANTI', 'AMANTI-ITEM-001-3', NOW() - INTERVAL 20 DAY),
    ((SELECT id FROM items WHERE item_code = 'AMANTI-ITEM-001'), (SELECT id FROM warehouses WHERE warehouse_code = 'WH-SEASON'), 'INBOUND', 'PURCHASE_RECEIVED', 'SM-AMANTI-004', 8, 6500.0000, 52000.0000, 5, 13, 'SAMPLE_AMANTI', 'AMANTI-ITEM-001-4', NOW() - INTERVAL 15 DAY),
    ((SELECT id FROM items WHERE item_code = 'AMANTI-ITEM-001'), (SELECT id FROM warehouses WHERE warehouse_code = 'WH-SEASON'), 'OUTBOUND', 'SALES_SHIPPED', 'SM-AMANTI-005', 4, 6500.0000, 26000.0000, 13, 9, 'SAMPLE_AMANTI', 'AMANTI-ITEM-001-5', NOW() - INTERVAL 7 DAY),
    ((SELECT id FROM items WHERE item_code = 'AMANTI-ITEM-002'), (SELECT id FROM warehouses WHERE warehouse_code = 'WH-SEASON'), 'INBOUND', 'PURCHASE_RECEIVED', 'SM-AMANTI-006', 20, 4500.0000, 90000.0000, 0, 20, 'SAMPLE_AMANTI', 'AMANTI-ITEM-002-1', NOW() - INTERVAL 140 DAY),
    ((SELECT id FROM items WHERE item_code = 'AMANTI-ITEM-002'), (SELECT id FROM warehouses WHERE warehouse_code = 'WH-SEASON'), 'OUTBOUND', 'SALES_SHIPPED', 'SM-AMANTI-007', 8, 4500.0000, 36000.0000, 20, 12, 'SAMPLE_AMANTI', 'AMANTI-ITEM-002-2', NOW() - INTERVAL 25 DAY),
    ((SELECT id FROM items WHERE item_code = 'AMANTI-ITEM-002'), (SELECT id FROM warehouses WHERE warehouse_code = 'WH-SEASON'), 'OUTBOUND', 'SALES_SHIPPED', 'SM-AMANTI-008', 6, 4500.0000, 27000.0000, 12, 6, 'SAMPLE_AMANTI', 'AMANTI-ITEM-002-3', NOW() - INTERVAL 5 DAY),
    ((SELECT id FROM items WHERE item_code = 'AMANTI-ITEM-005'), (SELECT id FROM warehouses WHERE warehouse_code = 'WH-SEASON'), 'INBOUND', 'PURCHASE_RECEIVED', 'SM-AMANTI-009', 20, 5500.0000, 110000.0000, 0, 20, 'SAMPLE_AMANTI', 'AMANTI-ITEM-005-1', NOW() - INTERVAL 33 DAY),
    ((SELECT id FROM items WHERE item_code = 'AMANTI-ITEM-005'), (SELECT id FROM warehouses WHERE warehouse_code = 'WH-SEASON'), 'OUTBOUND', 'SALES_SHIPPED', 'SM-AMANTI-010', 9, 5500.0000, 49500.0000, 20, 11, 'SAMPLE_AMANTI', 'AMANTI-ITEM-005-2', NOW() - INTERVAL 6 DAY),
    ((SELECT id FROM items WHERE item_code = 'AMANTI-RAW-001'), (SELECT id FROM warehouses WHERE warehouse_code = 'WH-RAW'), 'INBOUND', 'PURCHASE_RECEIVED', 'SM-AMANTI-011', 5, 45000.0000, 225000.0000, 18, 23, 'SAMPLE_AMANTI', 'AMANTI-RAW-001-1', NOW() - INTERVAL 75 DAY),
    ((SELECT id FROM items WHERE item_code = 'AMANTI-RAW-001'), (SELECT id FROM warehouses WHERE warehouse_code = 'WH-RAW'), 'OUTBOUND', 'PRODUCTION_CONSUMED', 'SM-AMANTI-012', 1, 45000.0000, 45000.0000, 23, 22, 'SAMPLE_AMANTI', 'AMANTI-RAW-001-2', NOW() - INTERVAL 10 DAY),
    ((SELECT id FROM items WHERE item_code = 'AMANTI-ITEM-010'), (SELECT id FROM warehouses WHERE warehouse_code = 'WH-SEASON'), 'INBOUND', 'PURCHASE_RECEIVED', 'SM-AMANTI-013', 15, 11000.0000, 165000.0000, 5, 20, 'SAMPLE_AMANTI', 'AMANTI-ITEM-010-1', NOW() - INTERVAL 115 DAY),
    ((SELECT id FROM items WHERE item_code = 'AMANTI-ITEM-010'), (SELECT id FROM warehouses WHERE warehouse_code = 'WH-SEASON'), 'OUTBOUND', 'SALES_SHIPPED', 'SM-AMANTI-014', 4, 11000.0000, 44000.0000, 20, 16, 'SAMPLE_AMANTI', 'AMANTI-ITEM-010-2', NOW() - INTERVAL 15 DAY),
    ((SELECT id FROM items WHERE item_code = 'AMANTI-ITEM-003'), (SELECT id FROM warehouses WHERE warehouse_code = 'WH-SEASON'), 'INBOUND', 'PURCHASE_RECEIVED', 'SM-AMANTI-015', 25, 9800.0000, 245000.0000, 0, 25, 'SAMPLE_AMANTI', 'AMANTI-ITEM-003-1', NOW() - INTERVAL 100 DAY),
    ((SELECT id FROM items WHERE item_code = 'AMANTI-ITEM-003'), (SELECT id FROM warehouses WHERE warehouse_code = 'WH-SEASON'), 'OUTBOUND', 'SALES_SHIPPED', 'SM-AMANTI-016', 7, 9800.0000, 68600.0000, 25, 18, 'SAMPLE_AMANTI', 'AMANTI-ITEM-003-2', NOW() - INTERVAL 95 DAY);

-- -----------------------------------------------------------------
-- warehouse transfers (창고 간 재고 이동)
-- -----------------------------------------------------------------
INSERT INTO warehouse_transfers (
    transfer_no,
    source_warehouse_id,
    target_warehouse_id,
    status,
    remarks,
    requested_by_id,
    approved_by_id,
    created_at,
    updated_at,
    approved_at,
    completed_at
)
VALUES
    ('TR-DEMO-001', (SELECT id FROM warehouses WHERE warehouse_code = 'WH-RAW'), (SELECT id FROM warehouses WHERE warehouse_code = 'WH-SEASON'), 'PENDING', '티백 원단 시즌 대비 긴급 이동 요청', 2, NULL, NOW() - INTERVAL 1 DAY, NOW() - INTERVAL 1 DAY, NULL, NULL),
    ('TR-DEMO-002', (SELECT id FROM warehouses WHERE warehouse_code = 'WH-RAW'), (SELECT id FROM warehouses WHERE warehouse_code = 'WH-SEASON'), 'APPROVED', '패키징 전 대기 박스 부자재 이동', 2, 1, NOW() - INTERVAL 2 DAY, NOW() - INTERVAL 2 DAY, NOW() - INTERVAL 2 DAY + INTERVAL 2 HOUR, NULL),
    ('TR-DEMO-003', (SELECT id FROM warehouses WHERE warehouse_code = 'WH-RAW'), (SELECT id FROM warehouses WHERE warehouse_code = 'WH-SEASON'), 'COMPLETED', '홍차엽 및 허브 원료 시즌창고 정기 이동', 2, 1, NOW() - INTERVAL 5 DAY, NOW() - INTERVAL 4 DAY, NOW() - INTERVAL 5 DAY + INTERVAL 1 HOUR, NOW() - INTERVAL 4 DAY)
ON DUPLICATE KEY UPDATE
    source_warehouse_id = VALUES(source_warehouse_id),
    target_warehouse_id = VALUES(target_warehouse_id),
    status = VALUES(status),
    remarks = VALUES(remarks),
    requested_by_id = VALUES(requested_by_id),
    approved_by_id = VALUES(approved_by_id),
    updated_at = NOW();

INSERT INTO warehouse_transfer_items (
    transfer_id,
    item_id,
    quantity
)
VALUES
    ((SELECT id FROM warehouse_transfers WHERE transfer_no = 'TR-DEMO-001'), (SELECT id FROM items WHERE item_code = 'AMANTI-RAW-001'), 2),
    ((SELECT id FROM warehouse_transfers WHERE transfer_no = 'TR-DEMO-002'), (SELECT id FROM items WHERE item_code = 'AMANTI-RAW-004'), 3),
    ((SELECT id FROM warehouse_transfers WHERE transfer_no = 'TR-DEMO-003'), (SELECT id FROM items WHERE item_code = 'AMANTI-RAW-003'), 5);

-- -----------------------------------------------------------------
-- notices (사내 공지사항 설정)
-- -----------------------------------------------------------------
INSERT INTO notice (
    type,
    title,
    content,
    start_date,
    end_date,
    view_count,
    author_id,
    created_at,
    updated_at
)
VALUES
    ('MAINTENANCE', 'DDUK ERP 2.0 시스템 정기 업데이트 점검 안내', '안녕하세요. DDUK ERP 운영팀입니다. 시스템 성능 개선 및 보안 업데이트를 위해 2026년 6월 15일(일) 01:00부터 05:00까지 정기 점검이 진행됩니다. 점검 시간 동안은 서비스 접속이 일시 제한되오니 업무에 참고하시기 바랍니다.', CURRENT_DATE - INTERVAL 3 DAY, CURRENT_DATE + INTERVAL 10 DAY, 45, 'admin', NOW(), NOW()),
    ('NORMAL', '[인사] 2026년 하반기 전사 타운홀 미팅 개최 및 참석 요청', '임직원 여러분 안녕하십니까. 경영지원팀입니다. 당해 하반기 목표 달성 전략 공유 및 소통을 위해 전사 타운홀 미팅을 아래와 같이 개최하오니 전 임직원분들은 필히 참석해주시기 바랍니다. 일시: 2026년 6월 10일(수) 15:00, 장소: 대회의실 및 화상회의 줌 스트리밍.', CURRENT_DATE - INTERVAL 2 DAY, CURRENT_DATE + INTERVAL 7 DAY, 120, 'admin', NOW(), NOW()),
    ('NORMAL', '임직원 복지몰 아망티 브랜드 특가 제휴 이벤트 안내 (최대 40% 할인)', '복리후생 지원 프로그램의 일환으로 차(Tea) 전문 수입 제조사 아망티와 임직원 전용 특가 제휴를 체결하였습니다. 아망티 공식 쇼핑몰에서 DDUK ERP 사원 인증 번호 입력 시 홍차, 허브차, 선물세트를 최대 40% 할인가에 구매하실 수 있습니다. 상세 가이드는 첨부파일을 참조하세요.', CURRENT_DATE - INTERVAL 5 DAY, CURRENT_DATE + INTERVAL 15 DAY, 310, 'admin', NOW(), NOW())
ON DUPLICATE KEY UPDATE
    title = VALUES(title),
    content = VALUES(content),
    view_count = VALUES(view_count),
    updated_at = NOW();

-- -----------------------------------------------------------------
-- anomaly logs (이상 징후 로그 설정)
-- -----------------------------------------------------------------
INSERT INTO anomaly_logs (
    anomaly_key,
    rule_code,
    severity,
    title,
    summary,
    source_type,
    source_id,
    source_label,
    payload_json,
    active,
    status,
    first_detected_at,
    last_detected_at,
    reviewed_at,
    reviewed_by,
    review_note
)
SELECT
    'SAMPLE_AMANTI:NEGATIVE_AVAILABLE_STOCK',
    'NEGATIVE_AVAILABLE_STOCK',
    'CRITICAL',
    '가용재고가 음수로 내려간 아망티 SKU',
    '예약 수량이 현재고를 초과해 출고 차질 가능성이 높습니다. 즉시 재고 정합성 확인이 필요합니다.',
    'INVENTORY',
    CAST(inv.id AS CHAR),
    CONCAT(i.name, ' / ', w.warehouse_name),
    JSON_OBJECT('currentStock', inv.current_stock, 'allocatedStock', inv.allocated_stock, 'availableStock', inv.current_stock - inv.allocated_stock, 'itemCode', i.item_code),
    b'1',
    'OPEN',
    NOW() - INTERVAL 2 DAY,
    NOW() - INTERVAL 20 MINUTE,
    NULL,
    NULL,
    NULL
FROM inventories inv
JOIN items i ON i.id = inv.item_id
JOIN warehouses w ON w.id = inv.warehouse_id
WHERE i.item_code = 'AMANTI-ITEM-005'
ON DUPLICATE KEY UPDATE
    severity = VALUES(severity),
    title = VALUES(title),
    summary = VALUES(summary),
    source_id = VALUES(source_id),
    source_label = VALUES(source_label),
    payload_json = VALUES(payload_json),
    active = VALUES(active),
    status = VALUES(status),
    last_detected_at = VALUES(last_detected_at),
    updated_at = NOW();

INSERT INTO anomaly_logs (
    anomaly_key,
    rule_code,
    severity,
    title,
    summary,
    source_type,
    source_id,
    source_label,
    payload_json,
    active,
    status,
    first_detected_at,
    last_detected_at,
    reviewed_at,
    reviewed_by,
    review_note
)
SELECT
    'SAMPLE_AMANTI:OUT_OF_STOCK_WITH_SAFETY',
    'OUT_OF_STOCK_WITH_SAFETY',
    'HIGH',
    '안전재고가 남아 있는 품절 위험 SKU',
    '현재 재고가 0인데 안전재고 기준보다 부족합니다. 긴급 보충 또는 판매 중지 판단이 필요합니다.',
    'INVENTORY',
    CAST(inv.id AS CHAR),
    CONCAT(i.name, ' / ', w.warehouse_name),
    JSON_OBJECT('currentStock', inv.current_stock, 'safetyStock', inv.safety_stock, 'availableStock', inv.current_stock - inv.allocated_stock, 'itemCode', i.item_code),
    b'1',
    'OPEN',
    NOW() - INTERVAL 2 DAY,
    NOW() - INTERVAL 40 MINUTE,
    NULL,
    NULL,
    NULL
FROM inventories inv
JOIN items i ON i.id = inv.item_id
JOIN warehouses w ON w.id = inv.warehouse_id
WHERE i.item_code = 'AMANTI-ITEM-004'
ON DUPLICATE KEY UPDATE
    severity = VALUES(severity),
    title = VALUES(title),
    summary = VALUES(summary),
    source_id = VALUES(source_id),
    source_label = VALUES(source_label),
    payload_json = VALUES(payload_json),
    active = VALUES(active),
    status = VALUES(status),
    last_detected_at = VALUES(last_detected_at),
    updated_at = NOW();

INSERT INTO anomaly_logs (
    anomaly_key,
    rule_code,
    severity,
    title,
    summary,
    source_type,
    source_id,
    source_label,
    payload_json,
    active,
    status,
    first_detected_at,
    last_detected_at,
    reviewed_at,
    reviewed_by,
    review_note
)
SELECT
    'SAMPLE_AMANTI:STOCKOUT_BEFORE_LEAD_TIME',
    'STOCKOUT_BEFORE_LEAD_TIME',
    'MEDIUM',
    '리드타임보다 먼저 재고가 소진될 위험이 있는 SKU',
    '최근 출고 속도 기준으로 아망티 실론 홍차는 리드타임 도착 전 품절 가능성이 있어 긴급 발주 검토가 필요합니다.',
    'INVENTORY',
    CAST(inv.id AS CHAR),
    CONCAT(i.name, ' / ', w.warehouse_name),
    JSON_OBJECT('availableStock', inv.current_stock - inv.allocated_stock, 'safetyStock', inv.safety_stock, 'leadTimeDays', 7, 'predictedDaysUntilStockout', 5, 'itemCode', i.item_code),
    b'1',
    'CONFIRMED',
    NOW() - INTERVAL 3 DAY,
    NOW() - INTERVAL 1 HOUR,
    NOW() - INTERVAL 40 MINUTE,
    'System Admin',
    '아망티 실론 홍차 주간 대비 발주 우선순위 상향 검토'
FROM inventories inv
JOIN items i ON i.id = inv.item_id
JOIN warehouses w ON w.id = inv.warehouse_id
WHERE i.item_code = 'AMANTI-ITEM-001'
ON DUPLICATE KEY UPDATE
    severity = VALUES(severity),
    title = VALUES(title),
    summary = VALUES(summary),
    source_id = VALUES(source_id),
    source_label = VALUES(source_label),
    payload_json = VALUES(payload_json),
    active = VALUES(active),
    status = VALUES(status),
    reviewed_at = VALUES(reviewed_at),
    reviewed_by = VALUES(reviewed_by),
    review_note = VALUES(review_note),
    last_detected_at = VALUES(last_detected_at),
    updated_at = NOW();

-- -----------------------------------------------------------------
-- task history (AI/RPA 실행 기록) - 데모 데이터 비활성화
-- -----------------------------------------------------------------


-- -----------------------------------------------------------------
-- attendances / expenses (인사 근태 및 경비 청구)
-- -----------------------------------------------------------------
INSERT INTO attendances (
    employee_id,
    work_date,
    check_in_at,
    check_out_at,
    status,
    note,
    created_at,
    updated_at
)
VALUES
    ((SELECT id FROM employees WHERE employee_no = 'EMP-INV-001'), CURRENT_DATE - INTERVAL 4 DAY, '2026-05-29 08:52:10', '2026-05-29 18:15:30', 'PRESENT', '정상 출근', NOW(), NOW()),
    ((SELECT id FROM employees WHERE employee_no = 'EMP-INV-001'), CURRENT_DATE - INTERVAL 3 DAY, '2026-05-30 08:50:00', '2026-05-30 18:05:00', 'PRESENT', '정상 출근', NOW(), NOW()),
    ((SELECT id FROM employees WHERE employee_no = 'EMP-INV-001'), CURRENT_DATE - INTERVAL 2 DAY, '2026-05-31 08:57:40', '2026-05-31 18:10:20', 'PRESENT', '정상 출근', NOW(), NOW()),
    ((SELECT id FROM employees WHERE employee_no = 'EMP-INV-001'), CURRENT_DATE - INTERVAL 1 DAY, '2026-06-01 08:48:15', '2026-06-01 18:22:00', 'PRESENT', '정상 출근', NOW(), NOW()),
    ((SELECT id FROM employees WHERE employee_no = 'EMP-INV-001'), CURRENT_DATE, '2026-06-02 08:53:00', NULL, 'PRESENT', '진행 중', NOW(), NOW()),
    ((SELECT id FROM employees WHERE employee_no = 'EMP-HR-001'), CURRENT_DATE - INTERVAL 4 DAY, '2026-05-29 08:45:00', '2026-05-29 18:01:00', 'PRESENT', '정상 출근', NOW(), NOW()),
    ((SELECT id FROM employees WHERE employee_no = 'EMP-HR-001'), CURRENT_DATE - INTERVAL 3 DAY, '2026-05-30 08:40:00', '2026-05-30 18:02:00', 'PRESENT', '정상 출근', NOW(), NOW()),
    ((SELECT id FROM employees WHERE employee_no = 'EMP-HR-001'), CURRENT_DATE - INTERVAL 2 DAY, '2026-05-31 08:55:00', '2026-05-31 18:05:00', 'PRESENT', '정상 출근', NOW(), NOW()),
    ((SELECT id FROM employees WHERE employee_no = 'EMP-HR-001'), CURRENT_DATE - INTERVAL 1 DAY, '2026-06-01 08:44:00', '2026-06-01 18:00:00', 'PRESENT', '정상 출근', NOW(), NOW()),
    ((SELECT id FROM employees WHERE employee_no = 'EMP-HR-001'), CURRENT_DATE, '2026-06-02 08:42:00', NULL, 'PRESENT', '진행 중', NOW(), NOW()),
    ((SELECT id FROM employees WHERE employee_no = 'EMP-ACC-001'), CURRENT_DATE - INTERVAL 4 DAY, '2026-05-29 09:15:00', '2026-05-29 18:10:00', 'LATE', '늦은 교통사정', NOW(), NOW()),
    ((SELECT id FROM employees WHERE employee_no = 'EMP-ACC-001'), CURRENT_DATE - INTERVAL 3 DAY, '2026-05-30 08:52:00', '2026-05-30 18:08:00', 'PRESENT', '정상 출근', NOW(), NOW()),
    ((SELECT id FROM employees WHERE employee_no = 'EMP-ACC-001'), CURRENT_DATE - INTERVAL 2 DAY, '2026-05-31 08:50:00', '2026-05-31 18:04:00', 'PRESENT', '정상 출근', NOW(), NOW()),
    ((SELECT id FROM employees WHERE employee_no = 'EMP-ACC-001'), CURRENT_DATE - INTERVAL 1 DAY, '2026-06-01 08:55:00', '2026-06-01 18:05:00', 'PRESENT', '정상 출근', NOW(), NOW()),
    ((SELECT id FROM employees WHERE employee_no = 'EMP-ACC-001'), CURRENT_DATE, '2026-06-02 08:51:00', NULL, 'PRESENT', '진행 중', NOW(), NOW())
ON DUPLICATE KEY UPDATE
    check_in_at = VALUES(check_in_at),
    check_out_at = VALUES(check_out_at),
    status = VALUES(status),
    note = VALUES(note),
    updated_at = NOW();

INSERT INTO expenses (
    employee_id,
    expense_date,
    category,
    amount,
    description,
    receipt_file_path,
    status,
    created_at,
    updated_at
)
VALUES
    ((SELECT id FROM employees WHERE employee_no = 'EMP-INV-001'), '2026-05-28', 'MEAL', 15000.00, '물류창고 야근 식대', '/uploads/receipts/inv-001-20260528.jpg', 'APPROVED', NOW(), NOW()),
    ((SELECT id FROM employees WHERE employee_no = 'EMP-HR-001'), '2026-05-29', 'OFFICE_SUPPLIES', 48000.00, '인사팀 사무용 필기구 구매', '/uploads/receipts/hr-001-20260529.jpg', 'APPROVED', NOW(), NOW()),
    ((SELECT id FROM employees WHERE employee_no = 'EMP-ACC-001'), '2026-06-01', 'TRAVEL', 12500.00, '회계팀 은행 업무 출장 택시비', '/uploads/receipts/acc-001-20260601.jpg', 'SUBMITTED', NOW(), NOW())
ON DUPLICATE KEY UPDATE
    employee_id = VALUES(employee_id),
    expense_date = VALUES(expense_date),
    category = VALUES(category),
    amount = VALUES(amount),
    description = VALUES(description),
    status = VALUES(status),
    updated_at = NOW();

-- -----------------------------------------------------------------
-- accounts (계정과목 확장 및 동기화)
-- -----------------------------------------------------------------
INSERT INTO accounts (code, name, type, normal_balance, level, status, allow_posting, system_account, deleted, sort_order) VALUES
('1001', '현금', 'ASSET', 'DEBIT', 1, 'ACTIVE', 1, 1, 0, 10),
('1002', '보통예금', 'ASSET', 'DEBIT', 1, 'ACTIVE', 1, 1, 0, 20),
('1003', '재고자산', 'ASSET', 'DEBIT', 1, 'ACTIVE', 1, 1, 0, 30),
('1004', '매출채권', 'ASSET', 'DEBIT', 1, 'ACTIVE', 1, 1, 0, 40),
('1005', '비품', 'ASSET', 'DEBIT', 1, 'ACTIVE', 1, 1, 0, 50),
('1006', '감가상각누계액(비품)', 'ASSET', 'CREDIT', 1, 'ACTIVE', 1, 1, 0, 60),
('2001', '외상매입금', 'LIABILITY', 'CREDIT', 1, 'ACTIVE', 1, 1, 0, 10),
('2002', '미지급금(급여)', 'LIABILITY', 'CREDIT', 1, 'ACTIVE', 1, 1, 0, 20),
('3001', '자본금', 'EQUITY', 'CREDIT', 1, 'ACTIVE', 1, 1, 0, 10),
('3002', '이익잉여금', 'EQUITY', 'CREDIT', 1, 'ACTIVE', 1, 1, 0, 20),
('4001', '제품매출', 'REVENUE', 'CREDIT', 1, 'ACTIVE', 1, 1, 0, 10),
('4002', '상품매출', 'REVENUE', 'CREDIT', 1, 'ACTIVE', 1, 1, 0, 20),
('5001', '매출원가', 'EXPENSE', 'DEBIT', 1, 'ACTIVE', 1, 1, 0, 10),
('5002', '급여비용', 'EXPENSE', 'DEBIT', 1, 'ACTIVE', 1, 1, 0, 20),
('5003', '재고손실', 'EXPENSE', 'DEBIT', 1, 'ACTIVE', 1, 1, 0, 30),
('5004', '임차료', 'EXPENSE', 'DEBIT', 1, 'ACTIVE', 1, 1, 0, 40),
('5005', '광고선전비', 'EXPENSE', 'DEBIT', 1, 'ACTIVE', 1, 1, 0, 50),
('5006', '감가상각비', 'EXPENSE', 'DEBIT', 1, 'ACTIVE', 1, 1, 0, 60),
('5007', '복리후생비', 'EXPENSE', 'DEBIT', 1, 'ACTIVE', 1, 1, 0, 70),
('5008', '통신비', 'EXPENSE', 'DEBIT', 1, 'ACTIVE', 1, 1, 0, 80),
('5009', '여비교통비', 'EXPENSE', 'DEBIT', 1, 'ACTIVE', 1, 1, 0, 90),
('5010', '소모품비', 'EXPENSE', 'DEBIT', 1, 'ACTIVE', 1, 1, 0, 100)
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

-- -----------------------------------------------------------------
-- journal entries (회계 전표 및 분개 설정 - 2026년 대차대조 완벽 시나리오)
-- -----------------------------------------------------------------
INSERT INTO journal_entries (
    journal_no, transaction_date, description, status, source_type, source_id,
    total_debit, total_credit, created_by, fiscal_year, fiscal_month, created_at, updated_at
)
VALUES
    -- [1] 설립자본금 납입 (1월 1일)
    ('DEMO-JE-202601-CAPITAL', '2026-01-01', '설립 주주 납입 자본금 입금 전표', 'POSTED', 'MANUAL', NULL, 500000000.00, 500000000.00, 'demo-seed', 2026, 1, NOW() - INTERVAL 150 DAY, NOW() - INTERVAL 150 DAY),
    -- [2] 비품 및 초기 소모품 구입 (1월 5일)
    ('DEMO-JE-202601-FIX', '2026-01-05', '본사 사무실 집기 비품 및 소모품 구입 전표', 'POSTED', 'MANUAL', NULL, 32000000.00, 32000000.00, 'demo-seed', 2026, 1, NOW() - INTERVAL 149 DAY, NOW() - INTERVAL 149 DAY),
    
    -- [3] 1월 ~ 5월 매월 정기 전표들
    -- 1월
    ('DEMO-JE-202601-SALES', '2026-01-15', '아망티 제품/상품 판매 매출 전표 (1월)', 'POSTED', 'STOCK_OUTBOUND', NULL, 33000000.00, 33000000.00, 'demo-seed', 2026, 1, NOW() - INTERVAL 138 DAY, NOW() - INTERVAL 138 DAY),
    ('DEMO-JE-202601-PURCHASE', '2026-01-10', '아망티 원재료 및 원엽 매입 전표 (1월)', 'POSTED', 'PURCHASE_ORDER', NULL, 12000000.00, 12000000.00, 'demo-seed', 2026, 1, NOW() - INTERVAL 143 DAY, NOW() - INTERVAL 143 DAY),
    ('DEMO-JE-202601-EXPENSE', '2026-01-25', '임직원 급여 및 본사 임차 정기 경비 전표 (1월)', 'POSTED', 'MANUAL', NULL, 18500000.00, 18500000.00, 'demo-seed', 2026, 1, NOW() - INTERVAL 128 DAY, NOW() - INTERVAL 128 DAY),
    ('DEMO-JE-202601-COGS', '2026-01-30', '월간 판매 실적 매출원가 계상 전표 (1월)', 'POSTED', 'STOCK_ADJUSTMENT_OUT', NULL, 9000000.00, 9000000.00, 'demo-seed', 2026, 1, NOW() - INTERVAL 123 DAY, NOW() - INTERVAL 123 DAY),

    -- 2월
    ('DEMO-JE-202602-SALES', '2026-02-15', '아망티 제품/상품 판매 매출 전표 (2월)', 'POSTED', 'STOCK_OUTBOUND', NULL, 33000000.00, 33000000.00, 'demo-seed', 2026, 2, NOW() - INTERVAL 107 DAY, NOW() - INTERVAL 107 DAY),
    ('DEMO-JE-202602-COLL', '2026-02-15', '매출처 외상매출금 보통예금 회수 전표 (2월)', 'POSTED', 'MANUAL', NULL, 13000000.00, 13000000.00, 'demo-seed', 2026, 2, NOW() - INTERVAL 107 DAY, NOW() - INTERVAL 107 DAY),
    ('DEMO-JE-202602-PURCHASE', '2026-02-10', '아망티 원재료 및 원엽 매입 전표 (2월)', 'POSTED', 'PURCHASE_ORDER', NULL, 12000000.00, 12000000.00, 'demo-seed', 2026, 2, NOW() - INTERVAL 112 DAY, NOW() - INTERVAL 112 DAY),
    ('DEMO-JE-202602-PAYAP', '2026-02-10', '매입 거래처 외상매입금 이체 전표 (2월)', 'POSTED', 'MANUAL', NULL, 5000000.00, 5000000.00, 'demo-seed', 2026, 2, NOW() - INTERVAL 112 DAY, NOW() - INTERVAL 112 DAY),
    ('DEMO-JE-202602-EXPENSE', '2026-02-25', '임직원 급여 및 본사 임차 정기 경비 전표 (2월)', 'POSTED', 'MANUAL', NULL, 18500000.00, 18500000.00, 'demo-seed', 2026, 2, NOW() - INTERVAL 97 DAY, NOW() - INTERVAL 97 DAY),
    ('DEMO-JE-202602-COGS', '2026-02-28', '월간 판매 실적 매출원가 계상 전표 (2월)', 'POSTED', 'STOCK_ADJUSTMENT_OUT', NULL, 9000000.00, 9000000.00, 'demo-seed', 2026, 2, NOW() - INTERVAL 94 DAY, NOW() - INTERVAL 94 DAY),

    -- 3월
    ('DEMO-JE-202603-SALES', '2026-03-15', '아망티 제품/상품 판매 매출 전표 (3월)', 'POSTED', 'STOCK_OUTBOUND', NULL, 33000000.00, 33000000.00, 'demo-seed', 2026, 3, NOW() - INTERVAL 79 DAY, NOW() - INTERVAL 79 DAY),
    ('DEMO-JE-202603-COLL', '2026-03-15', '매출처 외상매출금 보통예금 회수 전표 (3월)', 'POSTED', 'MANUAL', NULL, 13000000.00, 13000000.00, 'demo-seed', 2026, 3, NOW() - INTERVAL 79 DAY, NOW() - INTERVAL 79 DAY),
    ('DEMO-JE-202603-PURCHASE', '2026-03-10', '아망티 원재료 및 원엽 매입 전표 (3월)', 'POSTED', 'PURCHASE_ORDER', NULL, 12000000.00, 12000000.00, 'demo-seed', 2026, 3, NOW() - INTERVAL 84 DAY, NOW() - INTERVAL 84 DAY),
    ('DEMO-JE-202603-PAYAP', '2026-03-10', '매입 거래처 외상매입금 이체 전표 (3월)', 'POSTED', 'MANUAL', NULL, 5000000.00, 5000000.00, 'demo-seed', 2026, 3, NOW() - INTERVAL 84 DAY, NOW() - INTERVAL 84 DAY),
    ('DEMO-JE-202603-EXPENSE', '2026-03-25', '임직원 급여 및 본사 임차 정기 경비 전표 (3월)', 'POSTED', 'MANUAL', NULL, 18500000.00, 18500000.00, 'demo-seed', 2026, 3, NOW() - INTERVAL 69 DAY, NOW() - INTERVAL 69 DAY),
    ('DEMO-JE-202603-COGS', '2026-03-30', '월간 판매 실적 매출원가 계상 전표 (3월)', 'POSTED', 'STOCK_ADJUSTMENT_OUT', NULL, 9000000.00, 9000000.00, 'demo-seed', 2026, 3, NOW() - INTERVAL 64 DAY, NOW() - INTERVAL 64 DAY),

    -- 4월
    ('DEMO-JE-202604-SALES', '2026-04-15', '아망티 제품/상품 판매 매출 전표 (4월)', 'POSTED', 'STOCK_OUTBOUND', NULL, 33000000.00, 33000000.00, 'demo-seed', 2026, 4, NOW() - INTERVAL 48 DAY, NOW() - INTERVAL 48 DAY),
    ('DEMO-JE-202604-COLL', '2026-04-15', '매출처 외상매출금 보통예금 회수 전표 (4월)', 'POSTED', 'MANUAL', NULL, 13000000.00, 13000000.00, 'demo-seed', 2026, 4, NOW() - INTERVAL 48 DAY, NOW() - INTERVAL 48 DAY),
    ('DEMO-JE-202604-PURCHASE', '2026-04-10', '아망티 원재료 및 원엽 매입 전표 (4월)', 'POSTED', 'PURCHASE_ORDER', NULL, 12000000.00, 12000000.00, 'demo-seed', 2026, 4, NOW() - INTERVAL 53 DAY, NOW() - INTERVAL 53 DAY),
    ('DEMO-JE-202604-PAYAP', '2026-04-10', '매입 거래처 외상매입금 이체 전표 (4월)', 'POSTED', 'MANUAL', NULL, 5000000.00, 5000000.00, 'demo-seed', 2026, 4, NOW() - INTERVAL 53 DAY, NOW() - INTERVAL 53 DAY),
    ('DEMO-JE-202604-EXPENSE', '2026-04-25', '임직원 급여 및 본사 임차 정기 경비 전표 (4월)', 'POSTED', 'MANUAL', NULL, 18500000.00, 18500000.00, 'demo-seed', 2026, 4, NOW() - INTERVAL 38 DAY, NOW() - INTERVAL 38 DAY),
    ('DEMO-JE-202604-COGS', '2026-04-30', '월간 판매 실적 매출원가 계상 전표 (4월)', 'POSTED', 'STOCK_ADJUSTMENT_OUT', NULL, 9000000.00, 9000000.00, 'demo-seed', 2026, 4, NOW() - INTERVAL 33 DAY, NOW() - INTERVAL 33 DAY),

    -- 5월
    ('DEMO-JE-202605-SALES', '2026-05-15', '아망티 제품/상품 판매 매출 전표 (5월)', 'POSTED', 'STOCK_OUTBOUND', NULL, 33000000.00, 33000000.00, 'demo-seed', 2026, 5, NOW() - INTERVAL 18 DAY, NOW() - INTERVAL 18 DAY),
    ('DEMO-JE-202605-COLL', '2026-05-15', '매출처 외상매출금 보통예금 회수 전표 (5월)', 'POSTED', 'MANUAL', NULL, 13000000.00, 13000000.00, 'demo-seed', 2026, 5, NOW() - INTERVAL 18 DAY, NOW() - INTERVAL 18 DAY),
    ('DEMO-JE-202605-PURCHASE', '2026-05-10', '아망티 원재료 및 원엽 매입 전표 (5월)', 'POSTED', 'PURCHASE_ORDER', NULL, 12000000.00, 12000000.00, 'demo-seed', 2026, 5, NOW() - INTERVAL 23 DAY, NOW() - INTERVAL 23 DAY),
    ('DEMO-JE-202605-PAYAP', '2026-05-10', '매입 거래처 외상매입금 이체 전표 (5월)', 'POSTED', 'MANUAL', NULL, 5000000.00, 5000000.00, 'demo-seed', 2026, 5, NOW() - INTERVAL 23 DAY, NOW() - INTERVAL 23 DAY),
    ('DEMO-JE-202605-EXPENSE', '2026-05-25', '임직원 급여 및 본사 임차 정기 경비 전표 (5월)', 'POSTED', 'MANUAL', NULL, 18500000.00, 18500000.00, 'demo-seed', 2026, 5, NOW() - INTERVAL 8 DAY, NOW() - INTERVAL 8 DAY),
    ('DEMO-JE-202605-COGS', '2026-05-30', '월간 판매 실적 매출원가 계상 전표 (5월)', 'POSTED', 'STOCK_ADJUSTMENT_OUT', NULL, 9000000.00, 9000000.00, 'demo-seed', 2026, 5, NOW() - INTERVAL 3 DAY, NOW() - INTERVAL 3 DAY),
    
    -- [4] 결산 조정 분개 (5월 31일)
    -- 비품 감가상각 누계액 계상
    ('DEMO-JE-202605-DEP', '2026-05-31', '2026년 상반기 비품 정기 감가상각 분개 전표', 'POSTED', 'MANUAL', NULL, 3000000.00, 3000000.00, 'demo-seed', 2026, 5, NOW() - INTERVAL 2 DAY, NOW() - INTERVAL 2 DAY),
    -- 손익 계정 마감 및 이익잉여금 대체 결산 분개
    ('DEMO-JE-202605-CLOSE', '2026-05-31', '2026년 5월 말 결산 손익계정 마감 및 이익잉여금 대체 전표', 'POSTED', 'MANUAL', NULL, 165000000.00, 165000000.00, 'demo-seed', 2026, 5, NOW() - INTERVAL 2 DAY, NOW() - INTERVAL 2 DAY),

    -- [5] 6월 임시/대기 중인 전표 분개 (Drafts for search/statistics/pages)
    -- DRAFT (5건)
    ('DEMO-JE-202606-D1', '2026-06-01', '홍보용 리플렛 디자인 및 인쇄비 청구 (기안)', 'DRAFT', 'VOUCHER', NULL, 1200000.00, 1200000.00, 'demo-seed', 2026, 6, NOW(), NOW()),
    ('DEMO-JE-202606-D2', '2026-06-01', '개발본부 야근 야식 식대 실비 경비 청구 (기안)', 'DRAFT', 'VOUCHER', NULL, 120000.00, 120000.00, 'demo-seed', 2026, 6, NOW(), NOW()),
    ('DEMO-JE-202606-D3', '2026-06-01', '전사 기가 인터넷 및 유선 통신망 정기 청구 (기안)', 'DRAFT', 'VOUCHER', NULL, 180000.00, 180000.00, 'demo-seed', 2026, 6, NOW(), NOW()),
    ('DEMO-JE-202606-D4', '2026-06-01', '사무용품 탕비실 믹스커피 대량 구입 건 (기안)', 'DRAFT', 'VOUCHER', NULL, 500000.00, 500000.00, 'demo-seed', 2026, 6, NOW(), NOW()),
    ('DEMO-JE-202606-D5', '2026-06-01', '인프라 구축 자문 기술 미팅 시내 교통비 정산 (기안)', 'DRAFT', 'VOUCHER', NULL, 250000.00, 250000.00, 'demo-seed', 2026, 6, NOW(), NOW()),
    -- REQUESTED (5건)
    ('DEMO-JE-202606-R1', '2026-06-01', '선물세트 패키지 박스 부자재 수입 결제 요청 (결재대기)', 'DRAFT', 'VOUCHER', NULL, 15000000.00, 15000000.00, 'demo-seed', 2026, 6, NOW(), NOW()),
    ('DEMO-JE-202606-R2', '2026-06-01', '사무실 관리비 정기 지급 청구 건 (결재대기)', 'DRAFT', 'VOUCHER', NULL, 2500000.00, 2500000.00, 'demo-seed', 2026, 6, NOW(), NOW()),
    ('DEMO-JE-202606-R3', '2026-06-01', '여름 시즌 대비 구글 포털 매체 검색광고 선급 (결재대기)', 'DRAFT', 'VOUCHER', NULL, 4500000.00, 4500000.00, 'demo-seed', 2026, 6, NOW(), NOW()),
    ('DEMO-JE-202606-R4', '2026-06-01', '전사 임직원 건강검진 지원금 결제 청구 (결재대기)', 'DRAFT', 'VOUCHER', NULL, 3800000.00, 3800000.00, 'demo-seed', 2026, 6, NOW(), NOW()),
    ('DEMO-JE-202606-R5', '2026-06-01', '사무실 복사 용지 및 소모품 벌크 구입 청구 (결재대기)', 'DRAFT', 'VOUCHER', NULL, 850000.00, 850000.00, 'demo-seed', 2026, 6, NOW(), NOW()),
    -- APPROVED (10건)
    ('DEMO-JE-202606-A1', '2026-06-02', '메가카페 원자재 대량 여름 시즌 선매출 건 (승인완료)', 'DRAFT', 'VOUCHER', NULL, 25000000.00, 25000000.00, 'demo-seed', 2026, 6, NOW(), NOW()),
    ('DEMO-JE-202606-A2', '2026-06-02', '대한다업 기획 차 선물세트 공급 선매출 건 (승인완료)', 'DRAFT', 'VOUCHER', NULL, 12000000.00, 12000000.00, 'demo-seed', 2026, 6, NOW(), NOW()),
    ('DEMO-JE-202606-A3', '2026-06-02', '잎차 신선도 보관용 철제 틴캔 부자재 대량 매입 (승인완료)', 'DRAFT', 'VOUCHER', NULL, 8500000.00, 8500000.00, 'demo-seed', 2026, 6, NOW(), NOW()),
    ('DEMO-JE-202606-A4', '2026-06-02', '영업본부 영남지역 대리점 미팅 출장 교통비 (승인완료)', 'DRAFT', 'VOUCHER', NULL, 1200000.00, 1200000.00, 'demo-seed', 2026, 6, NOW(), NOW()),
    ('DEMO-JE-202606-A5', '2026-06-02', 'AWS 클라우드 및 사내 그룹웨어 인프라 사용료 (승인완료)', 'DRAFT', 'VOUCHER', NULL, 3200000.00, 3200000.00, 'demo-seed', 2026, 6, NOW(), NOW()),
    ('DEMO-JE-202606-A6', '2026-06-02', '해외 수출 카탈로그 영문/중문 번역 외주비 (승인완료)', 'DRAFT', 'VOUCHER', NULL, 2000000.00, 2000000.00, 'demo-seed', 2026, 6, NOW(), NOW()),
    ('DEMO-JE-202606-A7', '2026-06-02', '고객지원실 냉난방기 무상 보증외 긴급 수리비 (승인완료)', 'DRAFT', 'VOUCHER', NULL, 450000.00, 450000.00, 'demo-seed', 2026, 6, NOW(), NOW()),
    ('DEMO-JE-202606-A8', '2026-06-02', '개발자 직무 향상 사내 스터디용 도서 구매 지원 (승인완료)', 'DRAFT', 'VOUCHER', NULL, 950000.00, 950000.00, 'demo-seed', 2026, 6, NOW(), NOW()),
    ('DEMO-JE-202606-A9', '2026-06-02', '대회의실 프리젠테이션용 85인치 모니터 추가 도입 (승인완료)', 'DRAFT', 'VOUCHER', NULL, 180000.00, 180000.00, 'demo-seed', 2026, 6, NOW(), NOW()),
    ('DEMO-JE-202606-A10', '2026-06-02', '온라인 원격 직무 교육 수강권 전사 라이선스 구입 (승인완료)', 'DRAFT', 'VOUCHER', NULL, 3000000.00, 3000000.00, 'demo-seed', 2026, 6, NOW(), NOW()),
    -- REJECTED (3건)
    ('DEMO-JE-202606-REJ1', '2026-06-02', '주말 개인 용도 마트 구매 비용 소모품비 청구 (반려됨)', 'DRAFT', 'VOUCHER', NULL, 500000.00, 500000.00, 'demo-seed', 2026, 6, NOW(), NOW()),
    ('DEMO-JE-202606-REJ2', '2026-06-02', '본사 결재선 누락 수입 원예 상품 보충 매입 건 (반려됨)', 'DRAFT', 'VOUCHER', NULL, 1500000.00, 1500000.00, 'demo-seed', 2026, 6, NOW(), NOW()),
    ('DEMO-JE-202606-REJ3', '2026-06-02', '대외 제휴 회의비 교통비 이중 청구 실비 정산 (반려됨)', 'DRAFT', 'VOUCHER', NULL, 120000.00, 120000.00, 'demo-seed', 2026, 6, NOW(), NOW())
ON DUPLICATE KEY UPDATE
    transaction_date = VALUES(transaction_date),
    description = VALUES(description),
    status = VALUES(status),
    total_debit = VALUES(total_debit),
    total_credit = VALUES(total_credit),
    updated_at = NOW();

-- -----------------------------------------------------------------
-- journal items (회계 라인/분개 상세 항목 설정)
-- -----------------------------------------------------------------
INSERT INTO journal_items (
    journal_entry_id, account_id, amount, debit_amount, credit_amount, description, reference_type, reference_id
)
VALUES
    -- [1] 설립자본금 납입
    ((SELECT id FROM journal_entries WHERE journal_no = 'DEMO-JE-202601-CAPITAL'), (SELECT id FROM accounts WHERE code = '1002'), 500000000.00, 500000000.00, 0.00, '설립자본금 예금 입금액', 'DEMO_SEED', NULL),
    ((SELECT id FROM journal_entries WHERE journal_no = 'DEMO-JE-202601-CAPITAL'), (SELECT id FROM accounts WHERE code = '3001'), 500000000.00, 0.00, 500000000.00, '설립 자본금 주식 발행액', 'DEMO_SEED', NULL),

    -- [2] 비품 및 소모품 구입
    ((SELECT id FROM journal_entries WHERE journal_no = 'DEMO-JE-202601-FIX'), (SELECT id FROM accounts WHERE code = '1005'), 30000000.00, 30000000.00, 0.00, '사무실 집기 비품 구입액', 'DEMO_SEED', NULL),
    ((SELECT id FROM journal_entries WHERE journal_no = 'DEMO-JE-202601-FIX'), (SELECT id FROM accounts WHERE code = '5010'), 2000000.00, 2000000.00, 0.00, '사무실 소형 소모품비', 'DEMO_SEED', NULL),
    ((SELECT id FROM journal_entries WHERE journal_no = 'DEMO-JE-202601-FIX'), (SELECT id FROM accounts WHERE code = '1002'), 32000000.00, 0.00, 32000000.00, '비품 및 소모품 대금 송금액', 'DEMO_SEED', NULL),

    -- [3] 1월 매출, 매입, 경비, 매출원가
    ((SELECT id FROM journal_entries WHERE journal_no = 'DEMO-JE-202601-SALES'), (SELECT id FROM accounts WHERE code = '1002'), 20000000.00, 20000000.00, 0.00, '판매 매출 현금 입금', 'DEMO_SEED', NULL),
    ((SELECT id FROM journal_entries WHERE journal_no = 'DEMO-JE-202601-SALES'), (SELECT id FROM accounts WHERE code = '1004'), 13000000.00, 13000000.00, 0.00, '도매 거래처 외상매출금', 'DEMO_SEED', NULL),
    ((SELECT id FROM journal_entries WHERE journal_no = 'DEMO-JE-202601-SALES'), (SELECT id FROM accounts WHERE code = '4001'), 25000000.00, 0.00, 25000000.00, '홍차 BOP 제품 매출', 'DEMO_SEED', NULL),
    ((SELECT id FROM journal_entries WHERE journal_no = 'DEMO-JE-202601-SALES'), (SELECT id FROM accounts WHERE code = '4002'), 8000000.00, 0.00, 8000000.00, '티포트/스트레이너 상품 매출', 'DEMO_SEED', NULL),

    ((SELECT id FROM journal_entries WHERE journal_no = 'DEMO-JE-202601-PURCHASE'), (SELECT id FROM accounts WHERE code = '1003'), 12000000.00, 12000000.00, 0.00, '아망티 원엽/포장필터 정기 매입', 'DEMO_SEED', NULL),
    ((SELECT id FROM journal_entries WHERE journal_no = 'DEMO-JE-202601-PURCHASE'), (SELECT id FROM accounts WHERE code = '1002'), 7000000.00, 0.00, 7000000.00, '원자재 매입 일부 선결제', 'DEMO_SEED', NULL),
    ((SELECT id FROM journal_entries WHERE journal_no = 'DEMO-JE-202601-PURCHASE'), (SELECT id FROM accounts WHERE code = '2001'), 5000000.00, 0.00, 5000000.00, '원자재 매입 외상매입금 잔액', 'DEMO_SEED', NULL),

    ((SELECT id FROM journal_entries WHERE journal_no = 'DEMO-JE-202601-EXPENSE'), (SELECT id FROM accounts WHERE code = '5004'), 3000000.00, 3000000.00, 0.00, '본사 사무실 건물 정기 월세', 'DEMO_SEED', NULL),
    ((SELECT id FROM journal_entries WHERE journal_no = 'DEMO-JE-202601-EXPENSE'), (SELECT id FROM accounts WHERE code = '5002'), 12000000.00, 12000000.00, 0.00, '임직원 1월 급여 총비용', 'DEMO_SEED', NULL),
    ((SELECT id FROM journal_entries WHERE journal_no = 'DEMO-JE-202601-EXPENSE'), (SELECT id FROM accounts WHERE code = '5005'), 1500000.00, 1500000.00, 0.00, '네이버 포털 검색 광고비 대행', 'DEMO_SEED', NULL),
    ((SELECT id FROM journal_entries WHERE journal_no = 'DEMO-JE-202601-EXPENSE'), (SELECT id FROM accounts WHERE code = '5007'), 1200000.00, 1200000.00, 0.00, '정기 탕비실 다과 및 야간 식대 지원', 'DEMO_SEED', NULL),
    ((SELECT id FROM journal_entries WHERE journal_no = 'DEMO-JE-202601-EXPENSE'), (SELECT id FROM accounts WHERE code = '5008'), 300000.00, 300000.00, 0.00, '사내 전산 보안 전용선 요금', 'DEMO_SEED', NULL),
    ((SELECT id FROM journal_entries WHERE journal_no = 'DEMO-JE-202601-EXPENSE'), (SELECT id FROM accounts WHERE code = '5009'), 500000.00, 500000.00, 0.00, '영업본부 외근용 하이패스 및 대중교통비', 'DEMO_SEED', NULL),
    ((SELECT id FROM journal_entries WHERE journal_no = 'DEMO-JE-202601-EXPENSE'), (SELECT id FROM accounts WHERE code = '1002'), 18500000.00, 0.00, 18500000.00, '정기 경비 및 급여 예금 일괄 이체액', 'DEMO_SEED', NULL),

    ((SELECT id FROM journal_entries WHERE journal_no = 'DEMO-JE-202601-COGS'), (SELECT id FROM accounts WHERE code = '5001'), 9000000.00, 9000000.00, 0.00, '1월 출고 원자재 분량 매출원가', 'DEMO_SEED', NULL),
    ((SELECT id FROM journal_entries WHERE journal_no = 'DEMO-JE-202601-COGS'), (SELECT id FROM accounts WHERE code = '1003'), 9000000.00, 0.00, 9000000.00, '재고자산 수량 차감 분개', 'DEMO_SEED', NULL),

    -- 2월
    ((SELECT id FROM journal_entries WHERE journal_no = 'DEMO-JE-202602-SALES'), (SELECT id FROM accounts WHERE code = '1002'), 20000000.00, 20000000.00, 0.00, '판매 매출 현금 입금', 'DEMO_SEED', NULL),
    ((SELECT id FROM journal_entries WHERE journal_no = 'DEMO-JE-202602-SALES'), (SELECT id FROM accounts WHERE code = '1004'), 13000000.00, 13000000.00, 0.00, '도매 거래처 외상매출금', 'DEMO_SEED', NULL),
    ((SELECT id FROM journal_entries WHERE journal_no = 'DEMO-JE-202602-SALES'), (SELECT id FROM accounts WHERE code = '4001'), 25000000.00, 0.00, 25000000.00, '홍차 BOP 제품 매출', 'DEMO_SEED', NULL),
    ((SELECT id FROM journal_entries WHERE journal_no = 'DEMO-JE-202602-SALES'), (SELECT id FROM accounts WHERE code = '4002'), 8000000.00, 0.00, 8000000.00, '티포트/스트레이너 상품 매출', 'DEMO_SEED', NULL),

    ((SELECT id FROM journal_entries WHERE journal_no = 'DEMO-JE-202602-COLL'), (SELECT id FROM accounts WHERE code = '1002'), 13000000.00, 13000000.00, 0.00, '1월 매출채권 보통예금 회수액', 'DEMO_SEED', NULL),
    ((SELECT id FROM journal_entries WHERE journal_no = 'DEMO-JE-202602-COLL'), (SELECT id FROM accounts WHERE code = '1004'), 13000000.00, 0.00, 13000000.00, '매출채권 잔액 반제 차감', 'DEMO_SEED', NULL),

    ((SELECT id FROM journal_entries WHERE journal_no = 'DEMO-JE-202602-PURCHASE'), (SELECT id FROM accounts WHERE code = '1003'), 12000000.00, 12000000.00, 0.00, '아망티 원엽/포장필터 정기 매입', 'DEMO_SEED', NULL),
    ((SELECT id FROM journal_entries WHERE journal_no = 'DEMO-JE-202602-PURCHASE'), (SELECT id FROM accounts WHERE code = '1002'), 7000000.00, 0.00, 7000000.00, '원자재 매입 일부 선결제', 'DEMO_SEED', NULL),
    ((SELECT id FROM journal_entries WHERE journal_no = 'DEMO-JE-202602-PURCHASE'), (SELECT id FROM accounts WHERE code = '2001'), 5000000.00, 0.00, 5000000.00, '원자재 매입 외상매입금 잔액', 'DEMO_SEED', NULL),

    ((SELECT id FROM journal_entries WHERE journal_no = 'DEMO-JE-202602-PAYAP'), (SELECT id FROM accounts WHERE code = '2001'), 5000000.00, 5000000.00, 0.00, '1월 매입 외상거래 대금 지급액', 'DEMO_SEED', NULL),
    ((SELECT id FROM journal_entries WHERE journal_no = 'DEMO-JE-202602-PAYAP'), (SELECT id FROM accounts WHERE code = '1002'), 5000000.00, 0.00, 5000000.00, '외상매입금 반제 송금', 'DEMO_SEED', NULL),

    ((SELECT id FROM journal_entries WHERE journal_no = 'DEMO-JE-202602-EXPENSE'), (SELECT id FROM accounts WHERE code = '5004'), 3000000.00, 3000000.00, 0.00, '본사 사무실 건물 정기 월세', 'DEMO_SEED', NULL),
    ((SELECT id FROM journal_entries WHERE journal_no = 'DEMO-JE-202602-EXPENSE'), (SELECT id FROM accounts WHERE code = '5002'), 12000000.00, 12000000.00, 0.00, '임직원 2월 급여 총비용', 'DEMO_SEED', NULL),
    ((SELECT id FROM journal_entries WHERE journal_no = 'DEMO-JE-202602-EXPENSE'), (SELECT id FROM accounts WHERE code = '5005'), 1500000.00, 1500000.00, 0.00, '네이버 포털 검색 광고비 대행', 'DEMO_SEED', NULL),
    ((SELECT id FROM journal_entries WHERE journal_no = 'DEMO-JE-202602-EXPENSE'), (SELECT id FROM accounts WHERE code = '5007'), 1200000.00, 1200000.00, 0.00, '정기 탕비실 다과 및 야간 식대 지원', 'DEMO_SEED', NULL),
    ((SELECT id FROM journal_entries WHERE journal_no = 'DEMO-JE-202602-EXPENSE'), (SELECT id FROM accounts WHERE code = '5008'), 300000.00, 300000.00, 0.00, '사내 전산 보안 전용선 요금', 'DEMO_SEED', NULL),
    ((SELECT id FROM journal_entries WHERE journal_no = 'DEMO-JE-202602-EXPENSE'), (SELECT id FROM accounts WHERE code = '5009'), 500000.00, 500000.00, 0.00, '영업본부 외근용 하이패스 및 대중교통비', 'DEMO_SEED', NULL),
    ((SELECT id FROM journal_entries WHERE journal_no = 'DEMO-JE-202602-EXPENSE'), (SELECT id FROM accounts WHERE code = '1002'), 18500000.00, 0.00, 18500000.00, '정기 경비 및 급여 예금 일괄 이체액', 'DEMO_SEED', NULL),

    ((SELECT id FROM journal_entries WHERE journal_no = 'DEMO-JE-202602-COGS'), (SELECT id FROM accounts WHERE code = '5001'), 9000000.00, 9000000.00, 0.00, '2월 출고 원자재 분량 매출원가', 'DEMO_SEED', NULL),
    ((SELECT id FROM journal_entries WHERE journal_no = 'DEMO-JE-202602-COGS'), (SELECT id FROM accounts WHERE code = '1003'), 9000000.00, 0.00, 9000000.00, '재고자산 수량 차감 분개', 'DEMO_SEED', NULL),

    -- 3월
    ((SELECT id FROM journal_entries WHERE journal_no = 'DEMO-JE-202603-SALES'), (SELECT id FROM accounts WHERE code = '1002'), 20000000.00, 20000000.00, 0.00, '판매 매출 현금 입금', 'DEMO_SEED', NULL),
    ((SELECT id FROM journal_entries WHERE journal_no = 'DEMO-JE-202603-SALES'), (SELECT id FROM accounts WHERE code = '1004'), 13000000.00, 13000000.00, 0.00, '도매 거래처 외상매출금', 'DEMO_SEED', NULL),
    ((SELECT id FROM journal_entries WHERE journal_no = 'DEMO-JE-202603-SALES'), (SELECT id FROM accounts WHERE code = '4001'), 25000000.00, 0.00, 25000000.00, '홍차 BOP 제품 매출', 'DEMO_SEED', NULL),
    ((SELECT id FROM journal_entries WHERE journal_no = 'DEMO-JE-202603-SALES'), (SELECT id FROM accounts WHERE code = '4002'), 8000000.00, 0.00, 8000000.00, '티포트/스트레이너 상품 매출', 'DEMO_SEED', NULL),

    ((SELECT id FROM journal_entries WHERE journal_no = 'DEMO-JE-202603-COLL'), (SELECT id FROM accounts WHERE code = '1002'), 13000000.00, 13000000.00, 0.00, '2월 매출채권 보통예금 회수액', 'DEMO_SEED', NULL),
    ((SELECT id FROM journal_entries WHERE journal_no = 'DEMO-JE-202603-COLL'), (SELECT id FROM accounts WHERE code = '1004'), 13000000.00, 0.00, 13000000.00, '매출채권 잔액 반제 차감', 'DEMO_SEED', NULL),

    ((SELECT id FROM journal_entries WHERE journal_no = 'DEMO-JE-202603-PURCHASE'), (SELECT id FROM accounts WHERE code = '1003'), 12000000.00, 12000000.00, 0.00, '아망티 원엽/포장필터 정기 매입', 'DEMO_SEED', NULL),
    ((SELECT id FROM journal_entries WHERE journal_no = 'DEMO-JE-202603-PURCHASE'), (SELECT id FROM accounts WHERE code = '1002'), 7000000.00, 0.00, 7000000.00, '원자재 매입 일부 선결제', 'DEMO_SEED', NULL),
    ((SELECT id FROM journal_entries WHERE journal_no = 'DEMO-JE-202603-PURCHASE'), (SELECT id FROM accounts WHERE code = '2001'), 5000000.00, 0.00, 5000000.00, '원자재 매입 외상매입금 잔액', 'DEMO_SEED', NULL),

    ((SELECT id FROM journal_entries WHERE journal_no = 'DEMO-JE-202603-PAYAP'), (SELECT id FROM accounts WHERE code = '2001'), 5000000.00, 5000000.00, 0.00, '2월 매입 외상거래 대금 지급액', 'DEMO_SEED', NULL),
    ((SELECT id FROM journal_entries WHERE journal_no = 'DEMO-JE-202603-PAYAP'), (SELECT id FROM accounts WHERE code = '1002'), 5000000.00, 0.00, 5000000.00, '외상매입금 반제 송금', 'DEMO_SEED', NULL),

    ((SELECT id FROM journal_entries WHERE journal_no = 'DEMO-JE-202603-EXPENSE'), (SELECT id FROM accounts WHERE code = '5004'), 3000000.00, 3000000.00, 0.00, '본사 사무실 건물 정기 월세', 'DEMO_SEED', NULL),
    ((SELECT id FROM journal_entries WHERE journal_no = 'DEMO-JE-202603-EXPENSE'), (SELECT id FROM accounts WHERE code = '5002'), 12000000.00, 12000000.00, 0.00, '임직원 3월 급여 총비용', 'DEMO_SEED', NULL),
    ((SELECT id FROM journal_entries WHERE journal_no = 'DEMO-JE-202603-EXPENSE'), (SELECT id FROM accounts WHERE code = '5005'), 1500000.00, 1500000.00, 0.00, '네이버 포털 검색 광고비 대행', 'DEMO_SEED', NULL),
    ((SELECT id FROM journal_entries WHERE journal_no = 'DEMO-JE-202603-EXPENSE'), (SELECT id FROM accounts WHERE code = '5007'), 1200000.00, 1200000.00, 0.00, '정기 탕비실 다과 및 야간 식대 지원', 'DEMO_SEED', NULL),
    ((SELECT id FROM journal_entries WHERE journal_no = 'DEMO-JE-202603-EXPENSE'), (SELECT id FROM accounts WHERE code = '5008'), 300000.00, 300000.00, 0.00, '사내 전산 보안 전용선 요금', 'DEMO_SEED', NULL),
    ((SELECT id FROM journal_entries WHERE journal_no = 'DEMO-JE-202603-EXPENSE'), (SELECT id FROM accounts WHERE code = '5009'), 500000.00, 500000.00, 0.00, '영업본부 외근용 하이패스 및 대중교통비', 'DEMO_SEED', NULL),
    ((SELECT id FROM journal_entries WHERE journal_no = 'DEMO-JE-202603-EXPENSE'), (SELECT id FROM accounts WHERE code = '1002'), 18500000.00, 0.00, 18500000.00, '정기 경비 및 급여 예금 일괄 이체액', 'DEMO_SEED', NULL),

    ((SELECT id FROM journal_entries WHERE journal_no = 'DEMO-JE-202603-COGS'), (SELECT id FROM accounts WHERE code = '5001'), 9000000.00, 9000000.00, 0.00, '3월 출고 원자재 분량 매출원가', 'DEMO_SEED', NULL),
    ((SELECT id FROM journal_entries WHERE journal_no = 'DEMO-JE-202603-COGS'), (SELECT id FROM accounts WHERE code = '1003'), 9000000.00, 0.00, 9000000.00, '재고자산 수량 차감 분개', 'DEMO_SEED', NULL),

    -- 4월
    ((SELECT id FROM journal_entries WHERE journal_no = 'DEMO-JE-202604-SALES'), (SELECT id FROM accounts WHERE code = '1002'), 20000000.00, 20000000.00, 0.00, '판매 매출 현금 입금', 'DEMO_SEED', NULL),
    ((SELECT id FROM journal_entries WHERE journal_no = 'DEMO-JE-202604-SALES'), (SELECT id FROM accounts WHERE code = '1004'), 13000000.00, 13000000.00, 0.00, '도매 거래처 외상매출금', 'DEMO_SEED', NULL),
    ((SELECT id FROM journal_entries WHERE journal_no = 'DEMO-JE-202604-SALES'), (SELECT id FROM accounts WHERE code = '4001'), 25000000.00, 0.00, 25000000.00, '홍차 BOP 제품 매출', 'DEMO_SEED', NULL),
    ((SELECT id FROM journal_entries WHERE journal_no = 'DEMO-JE-202604-SALES'), (SELECT id FROM accounts WHERE code = '4002'), 8000000.00, 0.00, 8000000.00, '티포트/스트레이너 상품 매출', 'DEMO_SEED', NULL),

    ((SELECT id FROM journal_entries WHERE journal_no = 'DEMO-JE-202604-COLL'), (SELECT id FROM accounts WHERE code = '1002'), 13000000.00, 13000000.00, 0.00, '3월 매출채권 보통예금 회수액', 'DEMO_SEED', NULL),
    ((SELECT id FROM journal_entries WHERE journal_no = 'DEMO-JE-202604-COLL'), (SELECT id FROM accounts WHERE code = '1004'), 13000000.00, 0.00, 13000000.00, '매출채권 잔액 반제 차감', 'DEMO_SEED', NULL),

    ((SELECT id FROM journal_entries WHERE journal_no = 'DEMO-JE-202604-PURCHASE'), (SELECT id FROM accounts WHERE code = '1003'), 12000000.00, 12000000.00, 0.00, '아망티 원엽/포장필터 정기 매입', 'DEMO_SEED', NULL),
    ((SELECT id FROM journal_entries WHERE journal_no = 'DEMO-JE-202604-PURCHASE'), (SELECT id FROM accounts WHERE code = '1002'), 7000000.00, 0.00, 7000000.00, '원자재 매입 일부 선결제', 'DEMO_SEED', NULL),
    ((SELECT id FROM journal_entries WHERE journal_no = 'DEMO-JE-202604-PURCHASE'), (SELECT id FROM accounts WHERE code = '2001'), 5000000.00, 0.00, 5000000.00, '원자재 매입 외상매입금 잔액', 'DEMO_SEED', NULL),

    ((SELECT id FROM journal_entries WHERE journal_no = 'DEMO-JE-202604-PAYAP'), (SELECT id FROM accounts WHERE code = '2001'), 5000000.00, 5000000.00, 0.00, '3월 매입 외상거래 대금 지급액', 'DEMO_SEED', NULL),
    ((SELECT id FROM journal_entries WHERE journal_no = 'DEMO-JE-202604-PAYAP'), (SELECT id FROM accounts WHERE code = '1002'), 5000000.00, 0.00, 5000000.00, '외상매입금 반제 송금', 'DEMO_SEED', NULL),

    ((SELECT id FROM journal_entries WHERE journal_no = 'DEMO-JE-202604-EXPENSE'), (SELECT id FROM accounts WHERE code = '5004'), 3000000.00, 3000000.00, 0.00, '본사 사무실 건물 정기 월세', 'DEMO_SEED', NULL),
    ((SELECT id FROM journal_entries WHERE journal_no = 'DEMO-JE-202604-EXPENSE'), (SELECT id FROM accounts WHERE code = '5002'), 12000000.00, 12000000.00, 0.00, '임직원 4월 급여 총비용', 'DEMO_SEED', NULL),
    ((SELECT id FROM journal_entries WHERE journal_no = 'DEMO-JE-202604-EXPENSE'), (SELECT id FROM accounts WHERE code = '5005'), 1500000.00, 1500000.00, 0.00, '네이버 포털 검색 광고비 대행', 'DEMO_SEED', NULL),
    ((SELECT id FROM journal_entries WHERE journal_no = 'DEMO-JE-202604-EXPENSE'), (SELECT id FROM accounts WHERE code = '5007'), 1200000.00, 1200000.00, 0.00, '정기 탕비실 다과 및 야간 식대 지원', 'DEMO_SEED', NULL),
    ((SELECT id FROM journal_entries WHERE journal_no = 'DEMO-JE-202604-EXPENSE'), (SELECT id FROM accounts WHERE code = '5008'), 300000.00, 300000.00, 0.00, '사내 전산 보안 전용선 요금', 'DEMO_SEED', NULL),
    ((SELECT id FROM journal_entries WHERE journal_no = 'DEMO-JE-202604-EXPENSE'), (SELECT id FROM accounts WHERE code = '5009'), 500000.00, 500000.00, 0.00, '영업본부 외근용 하이패스 및 대중교통비', 'DEMO_SEED', NULL),
    ((SELECT id FROM journal_entries WHERE journal_no = 'DEMO-JE-202604-EXPENSE'), (SELECT id FROM accounts WHERE code = '1002'), 18500000.00, 0.00, 18500000.00, '정기 경비 및 급여 예금 일괄 이체액', 'DEMO_SEED', NULL),

    ((SELECT id FROM journal_entries WHERE journal_no = 'DEMO-JE-202604-COGS'), (SELECT id FROM accounts WHERE code = '5001'), 9000000.00, 9000000.00, 0.00, '4월 출고 원자재 분량 매출원가', 'DEMO_SEED', NULL),
    ((SELECT id FROM journal_entries WHERE journal_no = 'DEMO-JE-202604-COGS'), (SELECT id FROM accounts WHERE code = '1003'), 9000000.00, 0.00, 9000000.00, '재고자산 수량 차감 분개', 'DEMO_SEED', NULL),

    -- 5월
    ((SELECT id FROM journal_entries WHERE journal_no = 'DEMO-JE-202605-SALES'), (SELECT id FROM accounts WHERE code = '1002'), 20000000.00, 20000000.00, 0.00, '판매 매출 현금 입금', 'DEMO_SEED', NULL),
    ((SELECT id FROM journal_entries WHERE journal_no = 'DEMO-JE-202605-SALES'), (SELECT id FROM accounts WHERE code = '1004'), 13000000.00, 13000000.00, 0.00, '도매 거래처 외상매출금', 'DEMO_SEED', NULL),
    ((SELECT id FROM journal_entries WHERE journal_no = 'DEMO-JE-202605-SALES'), (SELECT id FROM accounts WHERE code = '4001'), 25000000.00, 0.00, 25000000.00, '홍차 BOP 제품 매출', 'DEMO_SEED', NULL),
    ((SELECT id FROM journal_entries WHERE journal_no = 'DEMO-JE-202605-SALES'), (SELECT id FROM accounts WHERE code = '4002'), 8000000.00, 0.00, 8000000.00, '티포트/스트레이너 상품 매출', 'DEMO_SEED', NULL),

    ((SELECT id FROM journal_entries WHERE journal_no = 'DEMO-JE-202605-COLL'), (SELECT id FROM accounts WHERE code = '1002'), 13000000.00, 13000000.00, 0.00, '4월 매출채권 보통예금 회수액', 'DEMO_SEED', NULL),
    ((SELECT id FROM journal_entries WHERE journal_no = 'DEMO-JE-202605-COLL'), (SELECT id FROM accounts WHERE code = '1004'), 13000000.00, 0.00, 13000000.00, '매출채권 잔액 반제 차감', 'DEMO_SEED', NULL),

    ((SELECT id FROM journal_entries WHERE journal_no = 'DEMO-JE-202605-PURCHASE'), (SELECT id FROM accounts WHERE code = '1003'), 12000000.00, 12000000.00, 0.00, '아망티 원엽/포장필터 정기 매입', 'DEMO_SEED', NULL),
    ((SELECT id FROM journal_entries WHERE journal_no = 'DEMO-JE-202605-PURCHASE'), (SELECT id FROM accounts WHERE code = '1002'), 7000000.00, 0.00, 7000000.00, '원자재 매입 일부 선결제', 'DEMO_SEED', NULL),
    ((SELECT id FROM journal_entries WHERE journal_no = 'DEMO-JE-202605-PURCHASE'), (SELECT id FROM accounts WHERE code = '2001'), 5000000.00, 0.00, 5000000.00, '원자재 매입 외상매입금 잔액', 'DEMO_SEED', NULL),

    ((SELECT id FROM journal_entries WHERE journal_no = 'DEMO-JE-202605-PAYAP'), (SELECT id FROM accounts WHERE code = '2001'), 5000000.00, 5000000.00, 0.00, '4월 매입 외상거래 대금 지급액', 'DEMO_SEED', NULL),
    ((SELECT id FROM journal_entries WHERE journal_no = 'DEMO-JE-202605-PAYAP'), (SELECT id FROM accounts WHERE code = '1002'), 5000000.00, 0.00, 5000000.00, '외상매입금 반제 송금', 'DEMO_SEED', NULL),

    ((SELECT id FROM journal_entries WHERE journal_no = 'DEMO-JE-202605-EXPENSE'), (SELECT id FROM accounts WHERE code = '5004'), 3000000.00, 3000000.00, 0.00, '본사 사무실 건물 정기 월세', 'DEMO_SEED', NULL),
    ((SELECT id FROM journal_entries WHERE journal_no = 'DEMO-JE-202605-EXPENSE'), (SELECT id FROM accounts WHERE code = '5002'), 12000000.00, 12000000.00, 0.00, '임직원 5월 급여 총비용', 'DEMO_SEED', NULL),
    ((SELECT id FROM journal_entries WHERE journal_no = 'DEMO-JE-202605-EXPENSE'), (SELECT id FROM accounts WHERE code = '5005'), 1500000.00, 1500000.00, 0.00, '네이버 포털 검색 광고비 대행', 'DEMO_SEED', NULL),
    ((SELECT id FROM journal_entries WHERE journal_no = 'DEMO-JE-202605-EXPENSE'), (SELECT id FROM accounts WHERE code = '5007'), 1200000.00, 1200000.00, 0.00, '정기 탕비실 다과 및 야간 식대 지원', 'DEMO_SEED', NULL),
    ((SELECT id FROM journal_entries WHERE journal_no = 'DEMO-JE-202605-EXPENSE'), (SELECT id FROM accounts WHERE code = '5008'), 300000.00, 300000.00, 0.00, '사내 전산 보안 전용선 요금', 'DEMO_SEED', NULL),
    ((SELECT id FROM journal_entries WHERE journal_no = 'DEMO-JE-202605-EXPENSE'), (SELECT id FROM accounts WHERE code = '5009'), 500000.00, 500000.00, 0.00, '영업본부 외근용 하이패스 및 대중교통비', 'DEMO_SEED', NULL),
    ((SELECT id FROM journal_entries WHERE journal_no = 'DEMO-JE-202605-EXPENSE'), (SELECT id FROM accounts WHERE code = '1002'), 18500000.00, 0.00, 18500000.00, '정기 경비 및 급여 예금 일괄 이체액', 'DEMO_SEED', NULL),

    ((SELECT id FROM journal_entries WHERE journal_no = 'DEMO-JE-202605-COGS'), (SELECT id FROM accounts WHERE code = '5001'), 9000000.00, 9000000.00, 0.00, '5월 출고 원자재 분량 매출원가', 'DEMO_SEED', NULL),
    ((SELECT id FROM journal_entries WHERE journal_no = 'DEMO-JE-202605-COGS'), (SELECT id FROM accounts WHERE code = '1003'), 9000000.00, 0.00, 9000000.00, '재고자산 수량 차감 분개', 'DEMO_SEED', NULL),

    -- [4] 결산 조정 분개 (5월 31일)
    ((SELECT id FROM journal_entries WHERE journal_no = 'DEMO-JE-202605-DEP'), (SELECT id FROM accounts WHERE code = '5006'), 3000000.00, 3000000.00, 0.00, '2026년 상반기 비품 정기 감가상각비', 'DEMO_SEED', NULL),
    ((SELECT id FROM journal_entries WHERE journal_no = 'DEMO-JE-202605-DEP'), (SELECT id FROM accounts WHERE code = '1006'), 3000000.00, 0.00, 3000000.00, '비품 감가상각누계액 차감', 'DEMO_SEED', NULL),

    -- 결산 마감 및 이익잉여금 대체 (Revenues closed, Expenses closed, Profit transferred)
    ((SELECT id FROM journal_entries WHERE journal_no = 'DEMO-JE-202605-CLOSE'), (SELECT id FROM accounts WHERE code = '4001'), 125000000.00, 125000000.00, 0.00, '제품매출 결산 손익계정 마감', 'DEMO_SEED', NULL),
    ((SELECT id FROM journal_entries WHERE journal_no = 'DEMO-JE-202605-CLOSE'), (SELECT id FROM accounts WHERE code = '4002'), 40000000.00, 40000000.00, 0.00, '상품매출 결산 손익계정 마감', 'DEMO_SEED', NULL),
    ((SELECT id FROM journal_entries WHERE journal_no = 'DEMO-JE-202605-CLOSE'), (SELECT id FROM accounts WHERE code = '5001'), 45000000.00, 0.00, 45000000.00, '매출원가 결산 대체', 'DEMO_SEED', NULL),
    ((SELECT id FROM journal_entries WHERE journal_no = 'DEMO-JE-202605-CLOSE'), (SELECT id FROM accounts WHERE code = '5002'), 60000000.00, 0.00, 60000000.00, '급여비용 결산 대체', 'DEMO_SEED', NULL),
    ((SELECT id FROM journal_entries WHERE journal_no = 'DEMO-JE-202605-CLOSE'), (SELECT id FROM accounts WHERE code = '5004'), 15000000.00, 0.00, 15000000.00, '임차료 결산 대체', 'DEMO_SEED', NULL),
    ((SELECT id FROM journal_entries WHERE journal_no = 'DEMO-JE-202605-CLOSE'), (SELECT id FROM accounts WHERE code = '5005'), 7500000.00, 0.00, 7500000.00, '광고선전비 결산 대체', 'DEMO_SEED', NULL),
    ((SELECT id FROM journal_entries WHERE journal_no = 'DEMO-JE-202605-CLOSE'), (SELECT id FROM accounts WHERE code = '5007'), 6000000.00, 0.00, 6000000.00, '복리후생비 결산 대체', 'DEMO_SEED', NULL),
    ((SELECT id FROM journal_entries WHERE journal_no = 'DEMO-JE-202605-CLOSE'), (SELECT id FROM accounts WHERE code = '5008'), 1500000.00, 0.00, 1500000.00, '통신비 결산 대체', 'DEMO_SEED', NULL),
    ((SELECT id FROM journal_entries WHERE journal_no = 'DEMO-JE-202605-CLOSE'), (SELECT id FROM accounts WHERE code = '5009'), 2500000.00, 0.00, 2500000.00, '여비교통비 결산 대체', 'DEMO_SEED', NULL),
    ((SELECT id FROM journal_entries WHERE journal_no = 'DEMO-JE-202605-CLOSE'), (SELECT id FROM accounts WHERE code = '5010'), 2000000.00, 0.00, 2000000.00, '소모품비 결산 대체', 'DEMO_SEED', NULL),
    ((SELECT id FROM journal_entries WHERE journal_no = 'DEMO-JE-202605-CLOSE'), (SELECT id FROM accounts WHERE code = '5006'), 3000000.00, 0.00, 3000000.00, '감가상각비 결산 대체', 'DEMO_SEED', NULL),
    ((SELECT id FROM journal_entries WHERE journal_no = 'DEMO-JE-202605-CLOSE'), (SELECT id FROM accounts WHERE code = '3002'), 22500000.00, 0.00, 22500000.00, '당기순이익 이익잉여금 대체분개', 'DEMO_SEED', NULL),

    -- [5] 6월 기안/대기 전표 분개 (DRAFT)
    -- DRAFT (5건)
    ((SELECT id FROM journal_entries WHERE journal_no = 'DEMO-JE-202606-D1'), (SELECT id FROM accounts WHERE code = '5005'), 1200000.00, 1200000.00, 0.00, '홍보 브로셔 인쇄비 청구', 'VOUCHER_LINE', NULL),
    ((SELECT id FROM journal_entries WHERE journal_no = 'DEMO-JE-202606-D1'), (SELECT id FROM accounts WHERE code = '1002'), 1200000.00, 0.00, 1200000.00, '홍보 브로셔 인쇄 대금 보통예금', 'VOUCHER_LINE', NULL),
    
    ((SELECT id FROM journal_entries WHERE journal_no = 'DEMO-JE-202606-D2'), (SELECT id FROM accounts WHERE code = '5007'), 120000.00, 120000.00, 0.00, '개발팀 야근 식대 결제건', 'VOUCHER_LINE', NULL),
    ((SELECT id FROM journal_entries WHERE journal_no = 'DEMO-JE-202606-D2'), (SELECT id FROM accounts WHERE code = '1002'), 120000.00, 0.00, 120000.00, '개발팀 야근 식대 보통예금 이체', 'VOUCHER_LINE', NULL),

    ((SELECT id FROM journal_entries WHERE journal_no = 'DEMO-JE-202606-D3'), (SELECT id FROM accounts WHERE code = '5008'), 180000.00, 180000.00, 0.00, '사내 전산 보안 전용망 정기 청구', 'VOUCHER_LINE', NULL),
    ((SELECT id FROM journal_entries WHERE journal_no = 'DEMO-JE-202606-D3'), (SELECT id FROM accounts WHERE code = '1002'), 180000.00, 0.00, 180000.00, '전용망 사용료 보통예금 이체', 'VOUCHER_LINE', NULL),

    ((SELECT id FROM journal_entries WHERE journal_no = 'DEMO-JE-202606-D4'), (SELECT id FROM accounts WHERE code = '5010'), 500000.00, 500000.00, 0.00, '탕비실용 커피 믹스 및 종이컵 구입', 'VOUCHER_LINE', NULL),
    ((SELECT id FROM journal_entries WHERE journal_no = 'DEMO-JE-202606-D4'), (SELECT id FROM accounts WHERE code = '1002'), 500000.00, 0.00, 500000.00, '소모품 구입 대금 보통예금 이체', 'VOUCHER_LINE', NULL),

    ((SELECT id FROM journal_entries WHERE journal_no = 'DEMO-JE-202606-D5'), (SELECT id FROM accounts WHERE code = '5009'), 250000.00, 250000.00, 0.00, '여수 공장 기술 미팅 출장 교통비 정산', 'VOUCHER_LINE', NULL),
    ((SELECT id FROM journal_entries WHERE journal_no = 'DEMO-JE-202606-D5'), (SELECT id FROM accounts WHERE code = '1002'), 250000.00, 0.00, 250000.00, '출장 실비 보통예금 정산 송금', 'VOUCHER_LINE', NULL),

    -- REQUESTED (5건)
    ((SELECT id FROM journal_entries WHERE journal_no = 'DEMO-JE-202606-R1'), (SELECT id FROM accounts WHERE code = '1003'), 15000000.00, 15000000.00, 0.00, '선물세트 패키지용 크라프트 박스 수입 매입', 'VOUCHER_LINE', NULL),
    ((SELECT id FROM journal_entries WHERE journal_no = 'DEMO-JE-202606-R1'), (SELECT id FROM accounts WHERE code = '2001'), 15000000.00, 0.00, 15000000.00, '자재 매입 외상매입금 결제 승인 요청', 'VOUCHER_LINE', NULL),

    ((SELECT id FROM journal_entries WHERE journal_no = 'DEMO-JE-202606-R2'), (SELECT id FROM accounts WHERE code = '5004'), 2500000.00, 2500000.00, 0.00, '본사 정기 관리비 청구', 'VOUCHER_LINE', NULL),
    ((SELECT id FROM journal_entries WHERE journal_no = 'DEMO-JE-202606-R2'), (SELECT id FROM accounts WHERE code = '1002'), 2500000.00, 0.00, 2500000.00, '본사 관리비 보통예금 인출 결제 요청', 'VOUCHER_LINE', NULL),

    ((SELECT id FROM journal_entries WHERE journal_no = 'DEMO-JE-202606-R3'), (SELECT id FROM accounts WHERE code = '5005'), 4500000.00, 4500000.00, 0.00, '구글 검색광고 매체 충전금 집행 요청', 'VOUCHER_LINE', NULL),
    ((SELECT id FROM journal_entries WHERE journal_no = 'DEMO-JE-202606-R3'), (SELECT id FROM accounts WHERE code = '1002'), 4500000.00, 0.00, 4500000.00, '구글 광고비 예금 이체 결제 요청', 'VOUCHER_LINE', NULL),

    ((SELECT id FROM journal_entries WHERE journal_no = 'DEMO-JE-202606-R4'), (SELECT id FROM accounts WHERE code = '5007'), 3800000.00, 3800000.00, 0.00, '종합병원 임직원 건강검진 대행 정산', 'VOUCHER_LINE', NULL),
    ((SELECT id FROM journal_entries WHERE journal_no = 'DEMO-JE-202606-R4'), (SELECT id FROM accounts WHERE code = '1002'), 3800000.00, 0.00, 3800000.00, '건강검진 지원금 보통예금 결제 요청', 'VOUCHER_LINE', NULL),

    ((SELECT id FROM journal_entries WHERE journal_no = 'DEMO-JE-202606-R5'), (SELECT id FROM accounts WHERE code = '5010'), 850000.00, 850000.00, 0.00, '사무용 프린터 드럼 및 복사 용지 벌크 구입', 'VOUCHER_LINE', NULL),
    ((SELECT id FROM journal_entries WHERE journal_no = 'DEMO-JE-202606-R5'), (SELECT id FROM accounts WHERE code = '1002'), 850000.00, 0.00, 850000.00, '복사용지 대금 보통예금 결제 요청', 'VOUCHER_LINE', NULL),

    -- APPROVED (10건)
    ((SELECT id FROM journal_entries WHERE journal_no = 'DEMO-JE-202606-A1'), (SELECT id FROM accounts WHERE code = '1002'), 25000000.00, 25000000.00, 0.00, '메가카페 여름 원료 대량 납품 계약금 입금', 'VOUCHER_LINE', NULL),
    ((SELECT id FROM journal_entries WHERE journal_no = 'DEMO-JE-202606-A1'), (SELECT id FROM accounts WHERE code = '4001'), 25000000.00, 0.00, 25000000.00, '메가카페 대량 제품 선급 매출액', 'VOUCHER_LINE', NULL),

    ((SELECT id FROM journal_entries WHERE journal_no = 'DEMO-JE-202606-A2'), (SELECT id FROM accounts WHERE code = '1002'), 12000000.00, 12000000.00, 0.00, '기획 티포트 차 패키지 1차 중도금 보통예금', 'VOUCHER_LINE', NULL),
    ((SELECT id FROM journal_entries WHERE journal_no = 'DEMO-JE-202606-A2'), (SELECT id FROM accounts WHERE code = '4002'), 12000000.00, 0.00, 12000000.00, '대한다업 다기 상품 선급 매출액', 'VOUCHER_LINE', NULL),

    ((SELECT id FROM journal_entries WHERE journal_no = 'DEMO-JE-202606-A3'), (SELECT id FROM accounts WHERE code = '1003'), 8500000.00, 8500000.00, 0.00, '원자재 신선도 보관용 철제 틴캔 포장 매입', 'VOUCHER_LINE', NULL),
    ((SELECT id FROM journal_entries WHERE journal_no = 'DEMO-JE-202606-A3'), (SELECT id FROM accounts WHERE code = '2001'), 8500000.00, 0.00, 8500000.00, '보관 용기 매입 외상매입금 발생', 'VOUCHER_LINE', NULL),

    ((SELECT id FROM journal_entries WHERE journal_no = 'DEMO-JE-202606-A4'), (SELECT id FROM accounts WHERE code = '5009'), 1200000.00, 1200000.00, 0.00, '영업팀 영남지역 연계 대리점 출장 교통 정산', 'VOUCHER_LINE', NULL),
    ((SELECT id FROM journal_entries WHERE journal_no = 'DEMO-JE-202606-A4'), (SELECT id FROM accounts WHERE code = '1002'), 1200000.00, 0.00, 1200000.00, '출장 항공/KTX 보통예금 정산', 'VOUCHER_LINE', NULL),

    ((SELECT id FROM journal_entries WHERE journal_no = 'DEMO-JE-202606-A5'), (SELECT id FROM accounts WHERE code = '5008'), 3200000.00, 3200000.00, 0.00, '사내 ERP 연계 클라우드 가상 서버 사용료', 'VOUCHER_LINE', NULL),
    ((SELECT id FROM journal_entries WHERE journal_no = 'DEMO-JE-202606-A5'), (SELECT id FROM accounts WHERE code = '1002'), 3200000.00, 0.00, 3200000.00, 'AWS 서버 월간 이용 보통예금 자동 이체', 'VOUCHER_LINE', NULL),

    ((SELECT id FROM journal_entries WHERE journal_no = 'DEMO-JE-202606-A6'), (SELECT id FROM accounts WHERE code = '5005'), 2000000.00, 2000000.00, 0.00, '유기농 말차 영문 리플렛 외주 번역료', 'VOUCHER_LINE', NULL),
    ((SELECT id FROM journal_entries WHERE journal_no = 'DEMO-JE-202606-A6'), (SELECT id FROM accounts WHERE code = '1002'), 2000000.00, 0.00, 2000000.00, '외주 번역료 보통예금 이체 완료', 'VOUCHER_LINE', NULL),

    ((SELECT id FROM journal_entries WHERE journal_no = 'DEMO-JE-202606-A7'), (SELECT id FROM accounts WHERE code = '5007'), 450000.00, 450000.00, 0.00, '고객 상담 지원실 노후 냉난방기 긴급 수리', 'VOUCHER_LINE', NULL),
    ((SELECT id FROM journal_entries WHERE journal_no = 'DEMO-JE-202606-A7'), (SELECT id FROM accounts WHERE code = '1002'), 450000.00, 0.00, 450000.00, '상담실 냉난방기 긴급 부품 교체 보통예금', 'VOUCHER_LINE', NULL),

    ((SELECT id FROM journal_entries WHERE journal_no = 'DEMO-JE-202606-A8'), (SELECT id FROM accounts WHERE code = '5007'), 950000.00, 950000.00, 0.00, '사내 프론트엔드 역량 강화 강좌 라이선스', 'VOUCHER_LINE', NULL),
    ((SELECT id FROM journal_entries WHERE journal_no = 'DEMO-JE-202606-A8'), (SELECT id FROM accounts WHERE code = '1002'), 950000.00, 0.00, 950000.00, '직무 강좌 구입비 보통예금 송금', 'VOUCHER_LINE', NULL),

    ((SELECT id FROM journal_entries WHERE journal_no = 'DEMO-JE-202606-A9'), (SELECT id FROM accounts WHERE code = '1005'), 180000.00, 180000.00, 0.00, '사내 화상 회의실 연계 스마트 빔 프로젝터 거치대', 'VOUCHER_LINE', NULL),
    ((SELECT id FROM journal_entries WHERE journal_no = 'DEMO-JE-202606-A9'), (SELECT id FROM accounts WHERE code = '1002'), 180000.00, 0.00, 180000.00, '회의실 비품 보통예금 결제 송금', 'VOUCHER_LINE', NULL),

    ((SELECT id FROM journal_entries WHERE journal_no = 'DEMO-JE-202606-A10'), (SELECT id FROM accounts WHERE code = '5005'), 3000000.00, 3000000.00, 0.00, '인사팀 신규 ERP 교육 온라인 수강 연간 플랫폼 구독', 'VOUCHER_LINE', NULL),
    ((SELECT id FROM journal_entries WHERE journal_no = 'DEMO-JE-202606-A10'), (SELECT id FROM accounts WHERE code = '1002'), 3000000.00, 0.00, 3000000.00, '채용/교육 연간 플랫폼 구독 예금 이체', 'VOUCHER_LINE', NULL),

    -- REJECTED (3건)
    ((SELECT id FROM journal_entries WHERE journal_no = 'DEMO-JE-202606-REJ1'), (SELECT id FROM accounts WHERE code = '5010'), 500000.00, 500000.00, 0.00, '개인 용품 구입비 소모품비 이중 청구', 'VOUCHER_LINE', NULL),
    ((SELECT id FROM journal_entries WHERE journal_no = 'DEMO-JE-202606-REJ1'), (SELECT id FROM accounts WHERE code = '1002'), 500000.00, 0.00, 500000.00, '이중 청구에 따른 예금 반제 (반려)', 'VOUCHER_LINE', NULL),

    ((SELECT id FROM journal_entries WHERE journal_no = 'DEMO-JE-202606-REJ2'), (SELECT id FROM accounts WHERE code = '1003'), 1500000.00, 1500000.00, 0.00, '사전 기안 미비 부자재 수입 매입 건', 'VOUCHER_LINE', NULL),
    ((SELECT id FROM journal_entries WHERE journal_no = 'DEMO-JE-202606-REJ2'), (SELECT id FROM accounts WHERE code = '2001'), 1500000.00, 0.00, 1500000.00, '외상거래 취소 및 반려 분개', 'VOUCHER_LINE', NULL),

    ((SELECT id FROM journal_entries WHERE journal_no = 'DEMO-JE-202606-REJ3'), (SELECT id FROM accounts WHERE code = '5009'), 120000.00, 120000.00, 0.00, '외근 대중교통비 영수증 증빙 중복 청구 건', 'VOUCHER_LINE', NULL),
    ((SELECT id FROM journal_entries WHERE journal_no = 'DEMO-JE-202606-REJ3'), (SELECT id FROM accounts WHERE code = '1002'), 120000.00, 0.00, 120000.00, '대중교통비 이중청구 예금 반제 (반려)', 'VOUCHER_LINE', NULL);


-- -----------------------------------------------------------------
-- vouchers (전표 목록 원장 설정 - 결재 상태별 다각화)
-- -----------------------------------------------------------------
INSERT INTO vouchers (
    voucher_no, voucher_date, voucher_type, vat_type, vendor_id, vendor_name_snapshot,
    status, source_type, source_reference_id, description, journal_entry_id, created_by, created_at, updated_at
)
VALUES
    -- [1] 설립자본금 납입 및 비품
    ('DEMO-VCH-202601-001', '2026-01-01', 'GENERAL', 'ZERO_TAX', NULL, '설립 발기인 주주 총회', 'POSTED', NULL, NULL, '설립 주주 납입 자본금 입금 전표', (SELECT id FROM journal_entries WHERE journal_no = 'DEMO-JE-202601-CAPITAL'), 'demo-seed', NOW() - INTERVAL 150 DAY, NOW() - INTERVAL 150 DAY),
    ('DEMO-VCH-202601-002', '2026-01-05', 'GENERAL', 'ZERO_TAX', NULL, '삼우글라스 가구 가전마트', 'POSTED', NULL, NULL, '본사 사무실 집기 비품 및 소모품 구입 전표', (SELECT id FROM journal_entries WHERE journal_no = 'DEMO-JE-202601-FIX'), 'demo-seed', NOW() - INTERVAL 149 DAY, NOW() - INTERVAL 149 DAY),

    -- [2] 1월 ~ 5월 정기 전표
    -- 1월
    ('DEMO-VCH-202601-SAL', '2026-01-15', 'SALES', 'TAX_INVOICE', NULL, '메가카페 식자재', 'POSTED', 'STOCK_OUTBOUND', (SELECT id FROM stock_movements WHERE reference_no = 'SM-AMANTI-002'), '아망티 제품/상품 판매 매출 전표 (1월)', (SELECT id FROM journal_entries WHERE journal_no = 'DEMO-JE-202601-SALES'), 'demo-seed', NOW() - INTERVAL 138 DAY, NOW() - INTERVAL 138 DAY),
    ('DEMO-VCH-202601-PUR', '2026-01-10', 'PURCHASE', 'TAX_INVOICE', NULL, '(주)아망티', 'POSTED', 'PURCHASE_ORDER', (SELECT id FROM purchase_orders WHERE purchase_order_no = 'PO-AMANTI-AI-001'), '아망티 원재료 및 원엽 매입 전표 (1월)', (SELECT id FROM journal_entries WHERE journal_no = 'DEMO-JE-202601-PURCHASE'), 'demo-seed', NOW() - INTERVAL 143 DAY, NOW() - INTERVAL 143 DAY),
    ('DEMO-VCH-202601-EXP', '2026-01-25', 'GENERAL', 'ZERO_TAX', NULL, '경영지원본부 정기 경비', 'POSTED', NULL, NULL, '임직원 급여 및 본사 임차 정기 경비 전표 (1월)', (SELECT id FROM journal_entries WHERE journal_no = 'DEMO-JE-202601-EXPENSE'), 'demo-seed', NOW() - INTERVAL 128 DAY, NOW() - INTERVAL 128 DAY),
    ('DEMO-VCH-202601-COG', '2026-01-30', 'GENERAL', 'ZERO_TAX', NULL, '사내 전산 결산 마감', 'POSTED', 'STOCK_ADJUSTMENT_OUT', NULL, '월간 판매 실적 매출원가 계상 전표 (1월)', (SELECT id FROM journal_entries WHERE journal_no = 'DEMO-JE-202601-COGS'), 'demo-seed', NOW() - INTERVAL 123 DAY, NOW() - INTERVAL 123 DAY),

    -- 2월
    ('DEMO-VCH-202602-SAL', '2026-02-15', 'SALES', 'TAX_INVOICE', NULL, '메가카페 식자재', 'POSTED', 'STOCK_OUTBOUND', NULL, '아망티 제품/상품 판매 매출 전표 (2월)', (SELECT id FROM journal_entries WHERE journal_no = 'DEMO-JE-202602-SALES'), 'demo-seed', NOW() - INTERVAL 107 DAY, NOW() - INTERVAL 107 DAY),
    ('DEMO-VCH-202602-COL', '2026-02-15', 'GENERAL', 'ZERO_TAX', NULL, '메가카페 식자재', 'POSTED', NULL, NULL, '매출처 외상매출금 보통예금 회수 전표 (2월)', (SELECT id FROM journal_entries WHERE journal_no = 'DEMO-JE-202602-COLL'), 'demo-seed', NOW() - INTERVAL 107 DAY, NOW() - INTERVAL 107 DAY),
    ('DEMO-VCH-202602-PUR', '2026-02-10', 'PURCHASE', 'TAX_INVOICE', NULL, '(주)아망티', 'POSTED', 'PURCHASE_ORDER', (SELECT id FROM purchase_orders WHERE purchase_order_no = 'PO-AMANTI-AI-002'), '아망티 원재료 및 원엽 매입 전표 (2월)', (SELECT id FROM journal_entries WHERE journal_no = 'DEMO-JE-202602-PURCHASE'), 'demo-seed', NOW() - INTERVAL 112 DAY, NOW() - INTERVAL 112 DAY),
    ('DEMO-VCH-202602-PAP', '2026-02-10', 'GENERAL', 'ZERO_TAX', NULL, '(주)아망티', 'POSTED', NULL, NULL, '매입 거래처 외상매입금 이체 전표 (2월)', (SELECT id FROM journal_entries WHERE journal_no = 'DEMO-JE-202602-PAYAP'), 'demo-seed', NOW() - INTERVAL 112 DAY, NOW() - INTERVAL 112 DAY),
    ('DEMO-VCH-202602-EXP', '2026-02-25', 'GENERAL', 'ZERO_TAX', NULL, '경영지원본부 정기 경비', 'POSTED', NULL, NULL, '임직원 급여 및 본사 임차 정기 경비 전표 (2월)', (SELECT id FROM journal_entries WHERE journal_no = 'DEMO-JE-202602-EXPENSE'), 'demo-seed', NOW() - INTERVAL 97 DAY, NOW() - INTERVAL 97 DAY),
    ('DEMO-VCH-202602-COG', '2026-02-28', 'GENERAL', 'ZERO_TAX', NULL, '사내 전산 결산 마감', 'POSTED', 'STOCK_ADJUSTMENT_OUT', NULL, '월간 판매 실적 매출원가 계상 전표 (2월)', (SELECT id FROM journal_entries WHERE journal_no = 'DEMO-JE-202602-COGS'), 'demo-seed', NOW() - INTERVAL 94 DAY, NOW() - INTERVAL 94 DAY),

    -- 3월
    ('DEMO-VCH-202603-SAL', '2026-03-15', 'SALES', 'TAX_INVOICE', NULL, '메가카페 식자재', 'POSTED', 'STOCK_OUTBOUND', NULL, '아망티 제품/상품 판매 매출 전표 (3월)', (SELECT id FROM journal_entries WHERE journal_no = 'DEMO-JE-202603-SALES'), 'demo-seed', NOW() - INTERVAL 79 DAY, NOW() - INTERVAL 79 DAY),
    ('DEMO-VCH-202603-COL', '2026-03-15', 'GENERAL', 'ZERO_TAX', NULL, '메가카페 식자재', 'POSTED', NULL, NULL, '매출처 외상매출금 보통예금 회수 전표 (3월)', (SELECT id FROM journal_entries WHERE journal_no = 'DEMO-JE-202603-COLL'), 'demo-seed', NOW() - INTERVAL 79 DAY, NOW() - INTERVAL 79 DAY),
    ('DEMO-VCH-202603-PUR', '2026-03-10', 'PURCHASE', 'TAX_INVOICE', NULL, '(주)아망티', 'POSTED', 'PURCHASE_ORDER', NULL, '아망티 원재료 및 원엽 매입 전표 (3월)', (SELECT id FROM journal_entries WHERE journal_no = 'DEMO-JE-202603-PURCHASE'), 'demo-seed', NOW() - INTERVAL 84 DAY, NOW() - INTERVAL 84 DAY),
    ('DEMO-VCH-202603-PAP', '2026-03-10', 'GENERAL', 'ZERO_TAX', NULL, '(주)아망티', 'POSTED', NULL, NULL, '매입 거래처 외상매입금 이체 전표 (3월)', (SELECT id FROM journal_entries WHERE journal_no = 'DEMO-JE-202603-PAYAP'), 'demo-seed', NOW() - INTERVAL 84 DAY, NOW() - INTERVAL 84 DAY),
    ('DEMO-VCH-202603-EXP', '2026-03-25', 'GENERAL', 'ZERO_TAX', NULL, '경영지원본부 정기 경비', 'POSTED', NULL, NULL, '임직원 급여 및 본사 임차 정기 경비 전표 (3월)', (SELECT id FROM journal_entries WHERE journal_no = 'DEMO-JE-202603-EXPENSE'), 'demo-seed', NOW() - INTERVAL 69 DAY, NOW() - INTERVAL 69 DAY),
    ('DEMO-VCH-202603-COG', '2026-03-30', 'GENERAL', 'ZERO_TAX', NULL, '사내 전산 결산 마감', 'POSTED', 'STOCK_ADJUSTMENT_OUT', NULL, '월간 판매 실적 매출원가 계상 전표 (3월)', (SELECT id FROM journal_entries WHERE journal_no = 'DEMO-JE-202603-COGS'), 'demo-seed', NOW() - INTERVAL 64 DAY, NOW() - INTERVAL 64 DAY),

    -- 4월
    ('DEMO-VCH-202604-SAL', '2026-04-15', 'SALES', 'TAX_INVOICE', NULL, '메가카페 식자재', 'POSTED', 'STOCK_OUTBOUND', NULL, '아망티 제품/상품 판매 매출 전표 (4월)', (SELECT id FROM journal_entries WHERE journal_no = 'DEMO-JE-202604-SALES'), 'demo-seed', NOW() - INTERVAL 48 DAY, NOW() - INTERVAL 48 DAY),
    ('DEMO-VCH-202604-COL', '2026-04-15', 'GENERAL', 'ZERO_TAX', NULL, '메가카페 식자재', 'POSTED', NULL, NULL, '매출처 외상매출금 보통예금 회수 전표 (4월)', (SELECT id FROM journal_entries WHERE journal_no = 'DEMO-JE-202604-COLL'), 'demo-seed', NOW() - INTERVAL 48 DAY, NOW() - INTERVAL 48 DAY),
    ('DEMO-VCH-202604-PUR', '2026-04-10', 'PURCHASE', 'TAX_INVOICE', NULL, '(주)아망티', 'POSTED', 'PURCHASE_ORDER', NULL, '아망티 원재료 및 원엽 매입 전표 (4월)', (SELECT id FROM journal_entries WHERE journal_no = 'DEMO-JE-202604-PURCHASE'), 'demo-seed', NOW() - INTERVAL 53 DAY, NOW() - INTERVAL 53 DAY),
    ('DEMO-VCH-202604-PAP', '2026-04-10', 'GENERAL', 'ZERO_TAX', NULL, '(주)아망티', 'POSTED', NULL, NULL, '매입 거래처 외상매입금 이체 전표 (4월)', (SELECT id FROM journal_entries WHERE journal_no = 'DEMO-JE-202604-PAYAP'), 'demo-seed', NOW() - INTERVAL 53 DAY, NOW() - INTERVAL 53 DAY),
    ('DEMO-VCH-202604-EXP', '2026-04-25', 'GENERAL', 'ZERO_TAX', NULL, '경영지원본부 정기 경비', 'POSTED', NULL, NULL, '임직원 급여 및 본사 임차 정기 경비 전표 (4월)', (SELECT id FROM journal_entries WHERE journal_no = 'DEMO-JE-202604-EXPENSE'), 'demo-seed', NOW() - INTERVAL 38 DAY, NOW() - INTERVAL 38 DAY),
    ('DEMO-VCH-202604-COG', '2026-04-30', 'GENERAL', 'ZERO_TAX', NULL, '사내 전산 결산 마감', 'POSTED', 'STOCK_ADJUSTMENT_OUT', NULL, '월간 판매 실적 매출원가 계상 전표 (4월)', (SELECT id FROM journal_entries WHERE journal_no = 'DEMO-JE-202604-COGS'), 'demo-seed', NOW() - INTERVAL 33 DAY, NOW() - INTERVAL 33 DAY),

    -- 5월
    ('DEMO-VCH-202605-SAL', '2026-05-15', 'SALES', 'TAX_INVOICE', NULL, '메가카페 식자재', 'POSTED', 'STOCK_OUTBOUND', NULL, '아망티 제품/상품 판매 매출 전표 (5월)', (SELECT id FROM journal_entries WHERE journal_no = 'DEMO-JE-202605-SALES'), 'demo-seed', NOW() - INTERVAL 18 DAY, NOW() - INTERVAL 18 DAY),
    ('DEMO-VCH-202605-COL', '2026-05-15', 'GENERAL', 'ZERO_TAX', NULL, '메가카페 식자재', 'POSTED', NULL, NULL, '매출처 외상매출금 보통예금 회수 전표 (5월)', (SELECT id FROM journal_entries WHERE journal_no = 'DEMO-JE-202605-COLL'), 'demo-seed', NOW() - INTERVAL 18 DAY, NOW() - INTERVAL 18 DAY),
    ('DEMO-VCH-202605-PUR', '2026-05-10', 'PURCHASE', 'TAX_INVOICE', NULL, '(주)아망티', 'POSTED', 'PURCHASE_ORDER', NULL, '아망티 원재료 및 원엽 매입 전표 (5월)', (SELECT id FROM journal_entries WHERE journal_no = 'DEMO-JE-202605-PURCHASE'), 'demo-seed', NOW() - INTERVAL 23 DAY, NOW() - INTERVAL 23 DAY),
    ('DEMO-VCH-202605-PAP', '2026-05-10', 'GENERAL', 'ZERO_TAX', NULL, '(주)아망티', 'POSTED', NULL, NULL, '매입 거래처 외상매입금 이체 전표 (5월)', (SELECT id FROM journal_entries WHERE journal_no = 'DEMO-JE-202605-PAYAP'), 'demo-seed', NOW() - INTERVAL 23 DAY, NOW() - INTERVAL 23 DAY),
    ('DEMO-VCH-202605-EXP', '2026-05-25', 'GENERAL', 'ZERO_TAX', NULL, '경영지원본부 정기 경비', 'POSTED', NULL, NULL, '임직원 급여 및 본사 임차 정기 경비 전표 (5월)', (SELECT id FROM journal_entries WHERE journal_no = 'DEMO-JE-202605-EXPENSE'), 'demo-seed', NOW() - INTERVAL 8 DAY, NOW() - INTERVAL 8 DAY),
    ('DEMO-VCH-202605-COG', '2026-05-30', 'GENERAL', 'ZERO_TAX', NULL, '사내 전산 결산 마감', 'POSTED', 'STOCK_ADJUSTMENT_OUT', NULL, '월간 판매 실적 매출원가 계상 전표 (5월)', (SELECT id FROM journal_entries WHERE journal_no = 'DEMO-JE-202605-COGS'), 'demo-seed', NOW() - INTERVAL 3 DAY, NOW() - INTERVAL 3 DAY),

    -- [3] 결산 조정 분개 (5월 31일)
    ('DEMO-VCH-202605-DEP', '2026-05-31', 'GENERAL', 'ZERO_TAX', NULL, '사내 전산 결산 마감', 'POSTED', NULL, NULL, '2026년 상반기 비품 정기 감가상각 분개 전표', (SELECT id FROM journal_entries WHERE journal_no = 'DEMO-JE-202605-DEP'), 'demo-seed', NOW() - INTERVAL 2 DAY, NOW() - INTERVAL 2 DAY),
    ('DEMO-VCH-202605-CLS', '2026-05-31', 'GENERAL', 'ZERO_TAX', NULL, '사내 전산 결산 마감', 'POSTED', NULL, NULL, '2026년 5월 말 결산 손익계정 마감 및 이익잉여금 대체 전표', (SELECT id FROM journal_entries WHERE journal_no = 'DEMO-JE-202605-CLOSE'), 'demo-seed', NOW() - INTERVAL 2 DAY, NOW() - INTERVAL 2 DAY),

    -- [4] 6월 기안/결재/승인 상태별 전표 (총 23건)
    -- DRAFT (5건)
    ('DEMO-VCH-202606-D1', '2026-06-01', 'GENERAL', 'ZERO_TAX', NULL, '삼우글라스 인쇄나라', 'DRAFT', NULL, NULL, '홍보용 리플렛 디자인 및 인쇄비 청구 (기안)', (SELECT id FROM journal_entries WHERE journal_no = 'DEMO-JE-202606-D1'), 'demo-seed', NOW(), NOW()),
    ('DEMO-VCH-202606-D2', '2026-06-01', 'GENERAL', 'ZERO_TAX', NULL, '배달의민족 야식마켓', 'DRAFT', NULL, NULL, '개발본부 야근 야식 식대 실비 경비 청구 (기안)', (SELECT id FROM journal_entries WHERE journal_no = 'DEMO-JE-202606-D2'), 'demo-seed', NOW(), NOW()),
    ('DEMO-VCH-202606-D3', '2026-06-01', 'GENERAL', 'ZERO_TAX', NULL, 'KT 비즈넷 통신', 'DRAFT', NULL, NULL, '전사 기가 인터넷 및 유선 통신망 정기 청구 (기안)', (SELECT id FROM journal_entries WHERE journal_no = 'DEMO-JE-202606-D3'), 'demo-seed', NOW(), NOW()),
    ('DEMO-VCH-202606-D4', '2026-06-01', 'PURCHASE', 'TAX_INVOICE', NULL, '다이소 비즈몰', 'DRAFT', NULL, NULL, '사무용품 탕비실 믹스커피 대량 구입 건 (기안)', (SELECT id FROM journal_entries WHERE journal_no = 'DEMO-JE-202606-D4'), 'demo-seed', NOW(), NOW()),
    ('DEMO-VCH-202606-D5', '2026-06-01', 'GENERAL', 'ZERO_TAX', NULL, '코레일 철도공사', 'DRAFT', NULL, NULL, '인프라 구축 자문 기술 미팅 시내 교통비 정산 (기안)', (SELECT id FROM journal_entries WHERE journal_no = 'DEMO-JE-202606-D5'), 'demo-seed', NOW(), NOW()),

    -- REQUESTED (5건)
    ('DEMO-VCH-202606-R1', '2026-06-01', 'PURCHASE', 'TAX_INVOICE', NULL, '대한다업 패키징', 'REQUESTED', NULL, NULL, '선물세트 패키지 박스 부자재 수입 결제 요청 (결재대기)', (SELECT id FROM journal_entries WHERE journal_no = 'DEMO-JE-202606-R1'), 'demo-seed', NOW(), NOW()),
    ('DEMO-VCH-202606-R2', '2026-06-01', 'GENERAL', 'ZERO_TAX', NULL, '김포 1센터 관리 사무소', 'REQUESTED', NULL, NULL, '사무실 관리비 정기 지급 청구 건 (결재대기)', (SELECT id FROM journal_entries WHERE journal_no = 'DEMO-JE-202606-R2'), 'demo-seed', NOW(), NOW()),
    ('DEMO-VCH-202606-R3', '2026-06-01', 'GENERAL', 'ZERO_TAX', NULL, '구글 코리아 마케팅', 'REQUESTED', NULL, NULL, '여름 시즌 대비 구글 포털 매체 검색광고 선급 (결재대기)', (SELECT id FROM journal_entries WHERE journal_no = 'DEMO-JE-202606-R3'), 'demo-seed', NOW(), NOW()),
    ('DEMO-VCH-202606-R4', '2026-06-01', 'GENERAL', 'ZERO_TAX', NULL, '한국의학연구소 KMI', 'REQUESTED', NULL, NULL, '전사 임직원 건강검진 지원금 결제 청구 (결재대기)', (SELECT id FROM journal_entries WHERE journal_no = 'DEMO-JE-202606-R4'), 'demo-seed', NOW(), NOW()),
    ('DEMO-VCH-202606-R5', '2026-06-01', 'PURCHASE', 'TAX_INVOICE', NULL, '오피스디포 문구몰', 'REQUESTED', NULL, NULL, '사무실 복사 용지 및 소모품 벌크 구입 청구 (결재대기)', (SELECT id FROM journal_entries WHERE journal_no = 'DEMO-JE-202606-R5'), 'demo-seed', NOW(), NOW()),

    -- APPROVED (10건)
    ('DEMO-VCH-202606-A1', '2026-06-02', 'SALES', 'TAX_INVOICE', NULL, '메가카페 식자재', 'APPROVED', NULL, NULL, '메가카페 원자재 대량 여름 시즌 선매출 건 (승인완료)', (SELECT id FROM journal_entries WHERE journal_no = 'DEMO-JE-202606-A1'), 'demo-seed', NOW(), NOW()),
    ('DEMO-VCH-202606-A2', '2026-06-02', 'SALES', 'TAX_INVOICE', NULL, '대한다업 패키징', 'APPROVED', NULL, NULL, '대한다업 기획 차 선물세트 공급 선매출 건 (승인완료)', (SELECT id FROM journal_entries WHERE journal_no = 'DEMO-JE-202606-A2'), 'demo-seed', NOW(), NOW()),
    ('DEMO-VCH-202606-A3', '2026-06-02', 'PURCHASE', 'TAX_INVOICE', NULL, '삼우글라스 다기 몰', 'APPROVED', NULL, NULL, '잎차 신선도 보관용 철제 틴캔 부자재 대량 매입 (승인완료)', (SELECT id FROM journal_entries WHERE journal_no = 'DEMO-JE-202606-A3'), 'demo-seed', NOW(), NOW()),
    ('DEMO-VCH-202606-A4', '2026-06-02', 'GENERAL', 'ZERO_TAX', NULL, '대한항공 주식회사', 'APPROVED', NULL, NULL, '영업본부 영남지역 대리점 미팅 출장 교통비 (승인완료)', (SELECT id FROM journal_entries WHERE journal_no = 'DEMO-JE-202606-A4'), 'demo-seed', NOW(), NOW()),
    ('DEMO-VCH-202606-A5', '2026-06-02', 'GENERAL', 'ZERO_TAX', NULL, '아마존 웹 서비스 AWS', 'APPROVED', NULL, NULL, 'AWS 클라우드 및 사내 그룹웨어 인프라 사용료 (승인완료)', (SELECT id FROM journal_entries WHERE journal_no = 'DEMO-JE-202606-A5'), 'demo-seed', NOW(), NOW()),
    ('DEMO-VCH-202606-A6', '2026-06-02', 'GENERAL', 'ZERO_TAX', NULL, '한가람 번역 연구소', 'APPROVED', NULL, NULL, '해외 수출 카탈로그 영문/중문 번역 외주비 (승인완료)', (SELECT id FROM journal_entries WHERE journal_no = 'DEMO-JE-202606-A6'), 'demo-seed', NOW(), NOW()),
    ('DEMO-VCH-202606-A7', '2026-06-02', 'GENERAL', 'ZERO_TAX', NULL, '삼성 에어컨 서초케어', 'APPROVED', NULL, NULL, '고객지원실 냉난방기 무상 보증외 긴급 수리비 (승인완료)', (SELECT id FROM journal_entries WHERE journal_no = 'DEMO-JE-202606-A7'), 'demo-seed', NOW(), NOW()),
    ('DEMO-VCH-202606-A8', '2026-06-02', 'GENERAL', 'ZERO_TAX', NULL, '영풍문고 강남점', 'APPROVED', NULL, NULL, '개발자 직무 향상 사내 스터디용 도서 구매 지원 (승인완료)', (SELECT id FROM journal_entries WHERE journal_no = 'DEMO-JE-202606-A8'), 'demo-seed', NOW(), NOW()),
    ('DEMO-VCH-202606-A9', '2026-06-02', 'PURCHASE', 'TAX_INVOICE', NULL, 'LG전자 베스트샵', 'APPROVED', NULL, NULL, '대회의실 프리젠테이션용 85인치 모니터 추가 도입 (승인완료)', (SELECT id FROM journal_entries WHERE journal_no = 'DEMO-JE-202606-A9'), 'demo-seed', NOW(), NOW()),
    ('DEMO-VCH-202606-A10', '2026-06-02', 'GENERAL', 'ZERO_TAX', NULL, '패스트캠퍼스 기업교육', 'APPROVED', NULL, NULL, '온라인 원격 직무 교육 수강권 전사 라이선스 구입 (승인완료)', (SELECT id FROM journal_entries WHERE journal_no = 'DEMO-JE-202606-A10'), 'demo-seed', NOW(), NOW()),

    -- REJECTED (3건)
    ('DEMO-VCH-202606-REJ1', '2026-06-02', 'GENERAL', 'ZERO_TAX', NULL, '이마트 가양점 마켓', 'REJECTED', NULL, NULL, '주말 개인 용도 마트 구매 비용 소모품비 청구 (반려됨)', (SELECT id FROM journal_entries WHERE journal_no = 'DEMO-JE-202606-REJ1'), 'demo-seed', NOW(), NOW()),
    ('DEMO-VCH-202606-REJ2', '2026-06-02', 'PURCHASE', 'TAX_INVOICE', NULL, '제주 오가닉 다원', 'REJECTED', NULL, NULL, '본사 결재선 누락 수입 원예 상품 보충 매입 건 (반려됨)', (SELECT id FROM journal_entries WHERE journal_no = 'DEMO-JE-202606-REJ2'), 'demo-seed', NOW(), NOW()),
    ('DEMO-VCH-202606-REJ3', '2026-06-02', 'GENERAL', 'ZERO_TAX', NULL, '티머니 개인 법인 택시', 'REJECTED', NULL, NULL, '대외 제휴 회의비 교통비 이중 청구 실비 정산 (반려됨)', (SELECT id FROM journal_entries WHERE journal_no = 'DEMO-JE-202606-REJ3'), 'demo-seed', NOW(), NOW())
ON DUPLICATE KEY UPDATE
    voucher_date = VALUES(voucher_date),
    vendor_name_snapshot = VALUES(vendor_name_snapshot),
    status = VALUES(status),
    description = VALUES(description),
    updated_at = NOW();

-- -----------------------------------------------------------------
-- voucher lines (전표 세부 상세 내역 항목 - 전표관리 화면 연동을 위한 데이터)
-- -----------------------------------------------------------------
INSERT INTO voucher_lines (
    voucher_id, line_no, account_id, account_code, account_name, debit_credit, supply_amount, vat_amount, total_amount, description, sort_order
)
VALUES
    -- [1] 설립자본금 및 비품
    ((SELECT id FROM vouchers WHERE voucher_no = 'DEMO-VCH-202601-001'), 1, (SELECT id FROM accounts WHERE code = '1002'), '1002', '보통예금', 'DEBIT', 0.00, 0.00, 500000000.00, '설립자본금 예금 입금액', 1),
    ((SELECT id FROM vouchers WHERE voucher_no = 'DEMO-VCH-202601-001'), 2, (SELECT id FROM accounts WHERE code = '3001'), '3001', '자본금', 'CREDIT', 0.00, 0.00, 500000000.00, '설립 자본금 주식 발행액', 2),

    ((SELECT id FROM vouchers WHERE voucher_no = 'DEMO-VCH-202601-002'), 1, (SELECT id FROM accounts WHERE code = '1005'), '1005', '비품', 'DEBIT', 0.00, 0.00, 30000000.00, '사무실 집기 비품 구입액', 1),
    ((SELECT id FROM vouchers WHERE voucher_no = 'DEMO-VCH-202601-002'), 2, (SELECT id FROM accounts WHERE code = '5010'), '5010', '소모품비', 'DEBIT', 0.00, 0.00, 2000000.00, '사무실 소형 소모품비', 2),
    ((SELECT id FROM vouchers WHERE voucher_no = 'DEMO-VCH-202601-002'), 3, (SELECT id FROM accounts WHERE code = '1002'), '1002', '보통예금', 'CREDIT', 0.00, 0.00, 32000000.00, '비품 및 소모품 대금 송금액', 3),

    -- [2] 1월 매출, 매입, 경비, 매출원가
    ((SELECT id FROM vouchers WHERE voucher_no = 'DEMO-VCH-202601-SAL'), 1, (SELECT id FROM accounts WHERE code = '1002'), '1002', '보통예금', 'DEBIT', 0.00, 0.00, 20000000.00, '판매 매출 현금 입금', 1),
    ((SELECT id FROM vouchers WHERE voucher_no = 'DEMO-VCH-202601-SAL'), 2, (SELECT id FROM accounts WHERE code = '1004'), '1004', '매출채권', 'DEBIT', 0.00, 0.00, 13000000.00, '도매 거래처 외상매출금', 2),
    ((SELECT id FROM vouchers WHERE voucher_no = 'DEMO-VCH-202601-SAL'), 3, (SELECT id FROM accounts WHERE code = '4001'), '4001', '제품매출', 'CREDIT', 0.00, 0.00, 25000000.00, '홍차 BOP 제품 매출', 3),
    ((SELECT id FROM vouchers WHERE voucher_no = 'DEMO-VCH-202601-SAL'), 4, (SELECT id FROM accounts WHERE code = '4002'), '4002', '상품매출', 'CREDIT', 0.00, 0.00, 8000000.00, '티포트/스트레이너 상품 매출', 4),

    ((SELECT id FROM vouchers WHERE voucher_no = 'DEMO-VCH-202601-PUR'), 1, (SELECT id FROM accounts WHERE code = '1003'), '1003', '재고자산', 'DEBIT', 0.00, 0.00, 12000000.00, '아망티 원엽/포장필터 정기 매입', 1),
    ((SELECT id FROM vouchers WHERE voucher_no = 'DEMO-VCH-202601-PUR'), 2, (SELECT id FROM accounts WHERE code = '1002'), '1002', '보통예금', 'CREDIT', 0.00, 0.00, 7000000.00, '원자재 매입 일부 선결제', 2),
    ((SELECT id FROM vouchers WHERE voucher_no = 'DEMO-VCH-202601-PUR'), 3, (SELECT id FROM accounts WHERE code = '2001'), '2001', '외상매입금', 'CREDIT', 0.00, 0.00, 5000000.00, '원자재 매입 외상매입금 잔액', 3),

    ((SELECT id FROM vouchers WHERE voucher_no = 'DEMO-VCH-202601-EXP'), 1, (SELECT id FROM accounts WHERE code = '5004'), '5004', '임차료', 'DEBIT', 0.00, 0.00, 3000000.00, '본사 사무실 건물 정기 월세', 1),
    ((SELECT id FROM vouchers WHERE voucher_no = 'DEMO-VCH-202601-EXP'), 2, (SELECT id FROM accounts WHERE code = '5002'), '5002', '급여비용', 'DEBIT', 0.00, 0.00, 12000000.00, '임직원 1월 급여 총비용', 2),
    ((SELECT id FROM vouchers WHERE voucher_no = 'DEMO-VCH-202601-EXP'), 3, (SELECT id FROM accounts WHERE code = '5005'), '5005', '광고선전비', 'DEBIT', 0.00, 0.00, 1500000.00, '네이버 포털 검색 광고비 대행', 3),
    ((SELECT id FROM vouchers WHERE voucher_no = 'DEMO-VCH-202601-EXP'), 4, (SELECT id FROM accounts WHERE code = '5007'), '5007', '복리후생비', 'DEBIT', 0.00, 0.00, 1200000.00, '탕비실 간식 지원비', 4),
    ((SELECT id FROM vouchers WHERE voucher_no = 'DEMO-VCH-202601-EXP'), 5, (SELECT id FROM accounts WHERE code = '5008'), '5008', '통신비', 'DEBIT', 0.00, 0.00, 300000.00, '보안 전용선 요금', 5),
    ((SELECT id FROM vouchers WHERE voucher_no = 'DEMO-VCH-202601-EXP'), 6, (SELECT id FROM accounts WHERE code = '5009'), '5009', '여비교통비', 'DEBIT', 0.00, 0.00, 500000.00, '시내 외근 교통비 정산', 6),
    ((SELECT id FROM vouchers WHERE voucher_no = 'DEMO-VCH-202601-EXP'), 7, (SELECT id FROM accounts WHERE code = '1002'), '1002', '보통예금', 'CREDIT', 0.00, 0.00, 18500000.00, '정기 경비 예금 이체액', 7),

    ((SELECT id FROM vouchers WHERE voucher_no = 'DEMO-VCH-202601-COG'), 1, (SELECT id FROM accounts WHERE code = '5001'), '5001', '매출원가', 'DEBIT', 0.00, 0.00, 9000000.00, '1월 출고 원자재 분량 매출원가', 1),
    ((SELECT id FROM vouchers WHERE voucher_no = 'DEMO-VCH-202601-COG'), 2, (SELECT id FROM accounts WHERE code = '1003'), '1003', '재고자산', 'CREDIT', 0.00, 0.00, 9000000.00, '재고자산 수량 차감 분개', 2),

    -- 2월
    ((SELECT id FROM vouchers WHERE voucher_no = 'DEMO-VCH-202602-SAL'), 1, (SELECT id FROM accounts WHERE code = '1002'), '1002', '보통예금', 'DEBIT', 0.00, 0.00, 20000000.00, '판매 매출 현금 입금', 1),
    ((SELECT id FROM vouchers WHERE voucher_no = 'DEMO-VCH-202602-SAL'), 2, (SELECT id FROM accounts WHERE code = '1004'), '1004', '매출채권', 'DEBIT', 0.00, 0.00, 13000000.00, '도매 거래처 외상매출금', 2),
    ((SELECT id FROM vouchers WHERE voucher_no = 'DEMO-VCH-202602-SAL'), 3, (SELECT id FROM accounts WHERE code = '4001'), '4001', '제품매출', 'CREDIT', 0.00, 0.00, 25000000.00, '홍차 BOP 제품 매출', 3),
    ((SELECT id FROM vouchers WHERE voucher_no = 'DEMO-VCH-202602-SAL'), 4, (SELECT id FROM accounts WHERE code = '4002'), '4002', '상품매출', 'CREDIT', 0.00, 0.00, 8000000.00, '티포트/스트레이너 상품 매출', 4),

    ((SELECT id FROM vouchers WHERE voucher_no = 'DEMO-VCH-202602-COL'), 1, (SELECT id FROM accounts WHERE code = '1002'), '1002', '보통예금', 'DEBIT', 0.00, 0.00, 13000000.00, '1월 매출채권 보통예금 회수액', 1),
    ((SELECT id FROM vouchers WHERE voucher_no = 'DEMO-VCH-202602-COL'), 2, (SELECT id FROM accounts WHERE code = '1004'), '1004', '매출채권', 'CREDIT', 0.00, 0.00, 13000000.00, '매출채권 잔액 반제 차감', 2),

    ((SELECT id FROM vouchers WHERE voucher_no = 'DEMO-VCH-202602-PUR'), 1, (SELECT id FROM accounts WHERE code = '1003'), '1003', '재고자산', 'DEBIT', 0.00, 0.00, 12000000.00, '아망티 원엽/포장필터 정기 매입', 1),
    ((SELECT id FROM vouchers WHERE voucher_no = 'DEMO-VCH-202602-PUR'), 2, (SELECT id FROM accounts WHERE code = '1002'), '1002', '보통예금', 'CREDIT', 0.00, 0.00, 7000000.00, '원자재 매입 일부 선결제', 2),
    ((SELECT id FROM vouchers WHERE voucher_no = 'DEMO-VCH-202602-PUR'), 3, (SELECT id FROM accounts WHERE code = '2001'), '2001', '외상매입금', 'CREDIT', 0.00, 0.00, 5000000.00, '원자재 매입 외상매입금 잔액', 3),

    ((SELECT id FROM vouchers WHERE voucher_no = 'DEMO-VCH-202602-PAP'), 1, (SELECT id FROM accounts WHERE code = '2001'), '2001', '외상매입금', 'DEBIT', 0.00, 0.00, 5000000.00, '1월 매입 외상거래 대금 지급액', 1),
    ((SELECT id FROM vouchers WHERE voucher_no = 'DEMO-VCH-202602-PAP'), 2, (SELECT id FROM accounts WHERE code = '1002'), '1002', '보통예금', 'CREDIT', 0.00, 0.00, 5000000.00, '외상매입금 반제 송금', 2),

    ((SELECT id FROM vouchers WHERE voucher_no = 'DEMO-VCH-202602-EXP'), 1, (SELECT id FROM accounts WHERE code = '5004'), '5004', '임차료', 'DEBIT', 0.00, 0.00, 3000000.00, '본사 사무실 건물 정기 월세', 1),
    ((SELECT id FROM vouchers WHERE voucher_no = 'DEMO-VCH-202602-EXP'), 2, (SELECT id FROM accounts WHERE code = '5002'), '5002', '급여비용', 'DEBIT', 0.00, 0.00, 12000000.00, '임직원 2월 급여 총비용', 2),
    ((SELECT id FROM vouchers WHERE voucher_no = 'DEMO-VCH-202602-EXP'), 3, (SELECT id FROM accounts WHERE code = '5005'), '5005', '광고선전비', 'DEBIT', 0.00, 0.00, 1500000.00, '네이버 포털 검색 광고비 대행', 3),
    ((SELECT id FROM vouchers WHERE voucher_no = 'DEMO-VCH-202602-EXP'), 4, (SELECT id FROM accounts WHERE code = '5007'), '5007', '복리후생비', 'DEBIT', 0.00, 0.00, 1200000.00, '정기 간식 지원비', 4),
    ((SELECT id FROM vouchers WHERE voucher_no = 'DEMO-VCH-202602-EXP'), 5, (SELECT id FROM accounts WHERE code = '5008'), '5008', '통신비', 'DEBIT', 0.00, 0.00, 300000.00, '보안 전용선 요금', 5),
    ((SELECT id FROM vouchers WHERE voucher_no = 'DEMO-VCH-202602-EXP'), 6, (SELECT id FROM accounts WHERE code = '5009'), '5009', '여비교통비', 'DEBIT', 0.00, 0.00, 500000.00, '시내 외근 교통비 정산', 6),
    ((SELECT id FROM vouchers WHERE voucher_no = 'DEMO-VCH-202602-EXP'), 7, (SELECT id FROM accounts WHERE code = '1002'), '1002', '보통예금', 'CREDIT', 0.00, 0.00, 18500000.00, '정기 경비 예금 이체액', 7),

    ((SELECT id FROM vouchers WHERE voucher_no = 'DEMO-VCH-202602-COG'), 1, (SELECT id FROM accounts WHERE code = '5001'), '5001', '매출원가', 'DEBIT', 0.00, 0.00, 9000000.00, '2월 출고 원자재 분량 매출원가', 1),
    ((SELECT id FROM vouchers WHERE voucher_no = 'DEMO-VCH-202602-COG'), 2, (SELECT id FROM accounts WHERE code = '1003'), '1003', '재고자산', 'CREDIT', 0.00, 0.00, 9000000.00, '재고자산 수량 차감 분개', 2),

    -- 3월
    ((SELECT id FROM vouchers WHERE voucher_no = 'DEMO-VCH-202603-SAL'), 1, (SELECT id FROM accounts WHERE code = '1002'), '1002', '보통예금', 'DEBIT', 0.00, 0.00, 20000000.00, '판매 매출 현금 입금', 1),
    ((SELECT id FROM vouchers WHERE voucher_no = 'DEMO-VCH-202603-SAL'), 2, (SELECT id FROM accounts WHERE code = '1004'), '1004', '매출채권', 'DEBIT', 0.00, 0.00, 13000000.00, '도매 거래처 외상매출금', 2),
    ((SELECT id FROM vouchers WHERE voucher_no = 'DEMO-VCH-202603-SAL'), 3, (SELECT id FROM accounts WHERE code = '4001'), '4001', '제품매출', 'CREDIT', 0.00, 0.00, 25000000.00, '홍차 BOP 제품 매출', 3),
    ((SELECT id FROM vouchers WHERE voucher_no = 'DEMO-VCH-202603-SAL'), 4, (SELECT id FROM accounts WHERE code = '4002'), '4002', '상품매출', 'CREDIT', 0.00, 0.00, 8000000.00, '티포트/스트레이너 상품 매출', 4),

    ((SELECT id FROM vouchers WHERE voucher_no = 'DEMO-VCH-202603-COL'), 1, (SELECT id FROM accounts WHERE code = '1002'), '1002', '보통예금', 'DEBIT', 0.00, 0.00, 13000000.00, '2월 매출채권 보통예금 회수액', 1),
    ((SELECT id FROM vouchers WHERE voucher_no = 'DEMO-VCH-202603-COL'), 2, (SELECT id FROM accounts WHERE code = '1004'), '1004', '매출채권', 'CREDIT', 0.00, 0.00, 13000000.00, '매출채권 잔액 반제 차감', 2),

    ((SELECT id FROM vouchers WHERE voucher_no = 'DEMO-VCH-202603-PUR'), 1, (SELECT id FROM accounts WHERE code = '1003'), '1003', '재고자산', 'DEBIT', 0.00, 0.00, 12000000.00, '아망티 원엽/포장필터 정기 매입', 1),
    ((SELECT id FROM vouchers WHERE voucher_no = 'DEMO-VCH-202603-PUR'), 2, (SELECT id FROM accounts WHERE code = '1002'), '1002', '보통예금', 'CREDIT', 0.00, 0.00, 7000000.00, '원자재 매입 일부 선결제', 2),
    ((SELECT id FROM vouchers WHERE voucher_no = 'DEMO-VCH-202603-PUR'), 3, (SELECT id FROM accounts WHERE code = '2001'), '2001', '외상매입금', 'CREDIT', 0.00, 0.00, 5000000.00, '원자재 매입 외상매입금 잔액', 3),

    ((SELECT id FROM vouchers WHERE voucher_no = 'DEMO-VCH-202603-PAP'), 1, (SELECT id FROM accounts WHERE code = '2001'), '2001', '외상매입금', 'DEBIT', 0.00, 0.00, 5000000.00, '2월 매입 외상거래 대금 지급액', 1),
    ((SELECT id FROM vouchers WHERE voucher_no = 'DEMO-VCH-202603-PAP'), 2, (SELECT id FROM accounts WHERE code = '1002'), '1002', '보통예금', 'CREDIT', 0.00, 0.00, 5000000.00, '외상매입금 반제 송금', 2),

    ((SELECT id FROM vouchers WHERE voucher_no = 'DEMO-VCH-202603-EXP'), 1, (SELECT id FROM accounts WHERE code = '5004'), '5004', '임차료', 'DEBIT', 0.00, 0.00, 3000000.00, '본사 사무실 건물 정기 월세', 1),
    ((SELECT id FROM vouchers WHERE voucher_no = 'DEMO-VCH-202603-EXP'), 2, (SELECT id FROM accounts WHERE code = '5002'), '5002', '급여비용', 'DEBIT', 0.00, 0.00, 12000000.00, '임직원 3월 급여 총비용', 2),
    ((SELECT id FROM vouchers WHERE voucher_no = 'DEMO-VCH-202603-EXP'), 3, (SELECT id FROM accounts WHERE code = '5005'), '5005', '광고선전비', 'DEBIT', 0.00, 0.00, 1500000.00, '네이버 포털 검색 광고비 대행', 3),
    ((SELECT id FROM vouchers WHERE voucher_no = 'DEMO-VCH-202603-EXP'), 4, (SELECT id FROM accounts WHERE code = '5007'), '5007', '복리후생비', 'DEBIT', 0.00, 0.00, 1200000.00, '정기 간식 지원비', 4),
    ((SELECT id FROM vouchers WHERE voucher_no = 'DEMO-VCH-202603-EXP'), 5, (SELECT id FROM accounts WHERE code = '5008'), '5008', '통신비', 'DEBIT', 0.00, 0.00, 300000.00, '보안 전용선 요금', 5),
    ((SELECT id FROM vouchers WHERE voucher_no = 'DEMO-VCH-202603-EXP'), 6, (SELECT id FROM accounts WHERE code = '5009'), '5009', '여비교통비', 'DEBIT', 0.00, 0.00, 500000.00, '시내 외근 교통비 정산', 6),
    ((SELECT id FROM vouchers WHERE voucher_no = 'DEMO-VCH-202603-EXP'), 7, (SELECT id FROM accounts WHERE code = '1002'), '1002', '보통예금', 'CREDIT', 0.00, 0.00, 18500000.00, '정기 경비 예금 이체액', 7),

    ((SELECT id FROM vouchers WHERE voucher_no = 'DEMO-VCH-202603-COG'), 1, (SELECT id FROM accounts WHERE code = '5001'), '5001', '매출원가', 'DEBIT', 0.00, 0.00, 9000000.00, '3월 출고 원자재 분량 매출원가', 1),
    ((SELECT id FROM vouchers WHERE voucher_no = 'DEMO-VCH-202603-COG'), 2, (SELECT id FROM accounts WHERE code = '1003'), '1003', '재고자산', 'CREDIT', 0.00, 0.00, 9000000.00, '재고자산 수량 차감 분개', 2),

    -- 4월
    ((SELECT id FROM vouchers WHERE voucher_no = 'DEMO-VCH-202604-SAL'), 1, (SELECT id FROM accounts WHERE code = '1002'), '1002', '보통예금', 'DEBIT', 0.00, 0.00, 20000000.00, '판매 매출 현금 입금', 1),
    ((SELECT id FROM vouchers WHERE voucher_no = 'DEMO-VCH-202604-SAL'), 2, (SELECT id FROM accounts WHERE code = '1004'), '1004', '매출채권', 'DEBIT', 0.00, 0.00, 13000000.00, '도매 거래처 외상매출금', 2),
    ((SELECT id FROM vouchers WHERE voucher_no = 'DEMO-VCH-202604-SAL'), 3, (SELECT id FROM accounts WHERE code = '4001'), '4001', '제품매출', 'CREDIT', 0.00, 0.00, 25000000.00, '홍차 BOP 제품 매출', 3),
    ((SELECT id FROM vouchers WHERE voucher_no = 'DEMO-VCH-202604-SAL'), 4, (SELECT id FROM accounts WHERE code = '4002'), '4002', '상품매출', 'CREDIT', 0.00, 0.00, 8000000.00, '티포트/스트레이너 상품 매출', 4),

    ((SELECT id FROM vouchers WHERE voucher_no = 'DEMO-VCH-202604-COL'), 1, (SELECT id FROM accounts WHERE code = '1002'), '1002', '보통예금', 'DEBIT', 0.00, 0.00, 13000000.00, '3월 매출채권 보통예금 회수액', 1),
    ((SELECT id FROM vouchers WHERE voucher_no = 'DEMO-VCH-202604-COL'), 2, (SELECT id FROM accounts WHERE code = '1004'), '1004', '매출채권', 'CREDIT', 0.00, 0.00, 13000000.00, '매출채권 잔액 반제 차감', 2),

    ((SELECT id FROM vouchers WHERE voucher_no = 'DEMO-VCH-202604-PUR'), 1, (SELECT id FROM accounts WHERE code = '1003'), '1003', '재고자산', 'DEBIT', 0.00, 0.00, 12000000.00, '아망티 원엽/포장필터 정기 매입', 1),
    ((SELECT id FROM vouchers WHERE voucher_no = 'DEMO-VCH-202604-PUR'), 2, (SELECT id FROM accounts WHERE code = '1002'), '1002', '보통예금', 'CREDIT', 0.00, 0.00, 7000000.00, '원자재 매입 일부 선결제', 2),
    ((SELECT id FROM vouchers WHERE voucher_no = 'DEMO-VCH-202604-PUR'), 3, (SELECT id FROM accounts WHERE code = '2001'), '2001', '외상매입금', 'CREDIT', 0.00, 0.00, 5000000.00, '원자재 매입 외상매입금 잔액', 3),

    ((SELECT id FROM vouchers WHERE voucher_no = 'DEMO-VCH-202604-PAP'), 1, (SELECT id FROM accounts WHERE code = '2001'), '2001', '외상매입금', 'DEBIT', 0.00, 0.00, 5000000.00, '3월 매입 외상거래 대금 지급액', 1),
    ((SELECT id FROM vouchers WHERE voucher_no = 'DEMO-VCH-202604-PAP'), 2, (SELECT id FROM accounts WHERE code = '1002'), '1002', '보통예금', 'CREDIT', 0.00, 0.00, 5000000.00, '외상매입금 반제 송금', 2),

    ((SELECT id FROM vouchers WHERE voucher_no = 'DEMO-VCH-202604-EXP'), 1, (SELECT id FROM accounts WHERE code = '5004'), '5004', '임차료', 'DEBIT', 0.00, 0.00, 3000000.00, '본사 사무실 건물 정기 월세', 1),
    ((SELECT id FROM vouchers WHERE voucher_no = 'DEMO-VCH-202604-EXP'), 2, (SELECT id FROM accounts WHERE code = '5002'), '5002', '급여비용', 'DEBIT', 0.00, 0.00, 12000000.00, '임직원 4월 급여 총비용', 2),
    ((SELECT id FROM vouchers WHERE voucher_no = 'DEMO-VCH-202604-EXP'), 3, (SELECT id FROM accounts WHERE code = '5005'), '5005', '광고선전비', 'DEBIT', 0.00, 0.00, 1500000.00, '네이버 포털 검색 광고비 대행', 3),
    ((SELECT id FROM vouchers WHERE voucher_no = 'DEMO-VCH-202604-EXP'), 4, (SELECT id FROM accounts WHERE code = '5007'), '5007', '복리후생비', 'DEBIT', 0.00, 0.00, 1200000.00, '정기 간식 지원비', 4),
    ((SELECT id FROM vouchers WHERE voucher_no = 'DEMO-VCH-202604-EXP'), 5, (SELECT id FROM accounts WHERE code = '5008'), '5008', '통신비', 'DEBIT', 0.00, 0.00, 300000.00, '보안 전용선 요금', 5),
    ((SELECT id FROM vouchers WHERE voucher_no = 'DEMO-VCH-202604-EXP'), 6, (SELECT id FROM accounts WHERE code = '5009'), '5009', '여비교통비', 'DEBIT', 0.00, 0.00, 500000.00, '시내 외근 교통비 정산', 6),
    ((SELECT id FROM vouchers WHERE voucher_no = 'DEMO-VCH-202604-EXP'), 7, (SELECT id FROM accounts WHERE code = '1002'), '1002', '보통예금', 'CREDIT', 0.00, 0.00, 18500000.00, '정기 경비 예금 이체액', 7),

    ((SELECT id FROM vouchers WHERE voucher_no = 'DEMO-VCH-202604-COG'), 1, (SELECT id FROM accounts WHERE code = '5001'), '5001', '매출원가', 'DEBIT', 0.00, 0.00, 9000000.00, '4월 출고 원자재 분량 매출원가', 1),
    ((SELECT id FROM vouchers WHERE voucher_no = 'DEMO-VCH-202604-COG'), 2, (SELECT id FROM accounts WHERE code = '1003'), '1003', '재고자산', 'CREDIT', 0.00, 0.00, 9000000.00, '재고자산 수량 차감 분개', 2),

    -- 5월
    ((SELECT id FROM vouchers WHERE voucher_no = 'DEMO-VCH-202605-SAL'), 1, (SELECT id FROM accounts WHERE code = '1002'), '1002', '보통예금', 'DEBIT', 0.00, 0.00, 20000000.00, '판매 매출 현금 입금', 1),
    ((SELECT id FROM vouchers WHERE voucher_no = 'DEMO-VCH-202605-SAL'), 2, (SELECT id FROM accounts WHERE code = '1004'), '1004', '매출채권', 'DEBIT', 0.00, 0.00, 13000000.00, '도매 거래처 외상매출금', 2),
    ((SELECT id FROM vouchers WHERE voucher_no = 'DEMO-VCH-202605-SAL'), 3, (SELECT id FROM accounts WHERE code = '4001'), '4001', '제품매출', 'CREDIT', 0.00, 0.00, 25000000.00, '홍차 BOP 제품 매출', 3),
    ((SELECT id FROM vouchers WHERE voucher_no = 'DEMO-VCH-202605-SAL'), 4, (SELECT id FROM accounts WHERE code = '4002'), '4002', '상품매출', 'CREDIT', 0.00, 0.00, 8000000.00, '티포트/스트레이너 상품 매출', 4),

    ((SELECT id FROM vouchers WHERE voucher_no = 'DEMO-VCH-202605-COL'), 1, (SELECT id FROM accounts WHERE code = '1002'), '1002', '보통예금', 'DEBIT', 0.00, 0.00, 13000000.00, '4월 매출채권 보통예금 회수액', 1),
    ((SELECT id FROM vouchers WHERE voucher_no = 'DEMO-VCH-202605-COL'), 2, (SELECT id FROM accounts WHERE code = '1004'), '1004', '매출채권', 'CREDIT', 0.00, 0.00, 13000000.00, '매출채권 잔액 반제 차감', 2),

    ((SELECT id FROM vouchers WHERE voucher_no = 'DEMO-VCH-202605-PUR'), 1, (SELECT id FROM accounts WHERE code = '1003'), '1003', '재고자산', 'DEBIT', 0.00, 0.00, 12000000.00, '아망티 원엽/포장필터 정기 매입', 1),
    ((SELECT id FROM vouchers WHERE voucher_no = 'DEMO-VCH-202605-PUR'), 2, (SELECT id FROM accounts WHERE code = '1002'), '1002', '보통예금', 'CREDIT', 0.00, 0.00, 7000000.00, '원자재 매입 일부 선결제', 2),
    ((SELECT id FROM vouchers WHERE voucher_no = 'DEMO-VCH-202605-PUR'), 3, (SELECT id FROM accounts WHERE code = '2001'), '2001', '외상매입금', 'CREDIT', 0.00, 0.00, 5000000.00, '원자재 매입 외상매입금 잔액', 3),

    ((SELECT id FROM vouchers WHERE voucher_no = 'DEMO-VCH-202605-PAP'), 1, (SELECT id FROM accounts WHERE code = '2001'), '2001', '외상매입금', 'DEBIT', 0.00, 0.00, 5000000.00, '4월 매입 외상거래 대금 지급액', 1),
    ((SELECT id FROM vouchers WHERE voucher_no = 'DEMO-VCH-202605-PAP'), 2, (SELECT id FROM accounts WHERE code = '1002'), '1002', '보통예금', 'CREDIT', 0.00, 0.00, 5000000.00, '외상매입금 반제 송금', 2),

    ((SELECT id FROM vouchers WHERE voucher_no = 'DEMO-VCH-202605-EXP'), 1, (SELECT id FROM accounts WHERE code = '5004'), '5004', '임차료', 'DEBIT', 0.00, 0.00, 3000000.00, '본사 사무실 건물 정기 월세', 1),
    ((SELECT id FROM vouchers WHERE voucher_no = 'DEMO-VCH-202605-EXP'), 2, (SELECT id FROM accounts WHERE code = '5002'), '5002', '급여비용', 'DEBIT', 0.00, 0.00, 12000000.00, '임직원 5월 급여 총비용', 2),
    ((SELECT id FROM vouchers WHERE voucher_no = 'DEMO-VCH-202605-EXP'), 3, (SELECT id FROM accounts WHERE code = '5005'), '5005', '광고선전비', 'DEBIT', 0.00, 0.00, 1500000.00, '네이버 포털 검색 광고비 대행', 3),
    ((SELECT id FROM vouchers WHERE voucher_no = 'DEMO-VCH-202605-EXP'), 4, (SELECT id FROM accounts WHERE code = '5007'), '5007', '복리후생비', 'DEBIT', 0.00, 0.00, 1200000.00, '정기 간식 지원비', 4),
    ((SELECT id FROM vouchers WHERE voucher_no = 'DEMO-VCH-202605-EXP'), 5, (SELECT id FROM accounts WHERE code = '5008'), '5008', '통신비', 'DEBIT', 0.00, 0.00, 300000.00, '보안 전용선 요금', 5),
    ((SELECT id FROM vouchers WHERE voucher_no = 'DEMO-VCH-202605-EXP'), 6, (SELECT id FROM accounts WHERE code = '5009'), '5009', '여비교통비', 'DEBIT', 0.00, 0.00, 500000.00, '시내 외근 교통비 정산', 6),
    ((SELECT id FROM vouchers WHERE voucher_no = 'DEMO-VCH-202605-EXP'), 7, (SELECT id FROM accounts WHERE code = '1002'), '1002', '보통예금', 'CREDIT', 0.00, 0.00, 18500000.00, '정기 경비 예금 이체액', 7),

    ((SELECT id FROM vouchers WHERE voucher_no = 'DEMO-VCH-202605-COG'), 1, (SELECT id FROM accounts WHERE code = '5001'), '5001', '매출원가', 'DEBIT', 0.00, 0.00, 9000000.00, '5월 출고 원자재 분량 매출원가', 1),
    ((SELECT id FROM vouchers WHERE voucher_no = 'DEMO-VCH-202605-COG'), 2, (SELECT id FROM accounts WHERE code = '1003'), '1003', '재고자산', 'CREDIT', 0.00, 0.00, 9000000.00, '재고자산 수량 차감 분개', 2),

    -- [3] 감가상각 및 결산 분개 lines
    ((SELECT id FROM vouchers WHERE voucher_no = 'DEMO-VCH-202605-DEP'), 1, (SELECT id FROM accounts WHERE code = '5006'), '5006', '감가상각비', 'DEBIT', 0.00, 0.00, 3000000.00, '2026년 상반기 비품 정기 감가상각비', 1),
    ((SELECT id FROM vouchers WHERE voucher_no = 'DEMO-VCH-202605-DEP'), 2, (SELECT id FROM accounts WHERE code = '1006'), '1006', '감가상각누계액(비품)', 'CREDIT', 0.00, 0.00, 3000000.00, '비품 감가상각누계액 차감', 2),

    ((SELECT id FROM vouchers WHERE voucher_no = 'DEMO-VCH-202605-CLS'), 1, (SELECT id FROM accounts WHERE code = '4001'), '4001', '제품매출', 'DEBIT', 0.00, 0.00, 125000000.00, '제품매출 결산 손익계정 마감', 1),
    ((SELECT id FROM vouchers WHERE voucher_no = 'DEMO-VCH-202605-CLS'), 2, (SELECT id FROM accounts WHERE code = '4002'), '4002', '상품매출', 'DEBIT', 0.00, 0.00, 40000000.00, '상품매출 결산 손익계정 마감', 2),
    ((SELECT id FROM vouchers WHERE voucher_no = 'DEMO-VCH-202605-CLS'), 3, (SELECT id FROM accounts WHERE code = '5001'), '5001', '매출원가', 'CREDIT', 0.00, 0.00, 45000000.00, '매출원가 결산 대체', 3),
    ((SELECT id FROM vouchers WHERE voucher_no = 'DEMO-VCH-202605-CLS'), 4, (SELECT id FROM accounts WHERE code = '5002'), '5002', '급여비용', 'CREDIT', 0.00, 0.00, 60000000.00, '급여비용 결산 대체', 4),
    ((SELECT id FROM vouchers WHERE voucher_no = 'DEMO-VCH-202605-CLS'), 5, (SELECT id FROM accounts WHERE code = '5004'), '5004', '임차료', 'CREDIT', 0.00, 0.00, 15000000.00, '임차료 결산 대체', 5),
    ((SELECT id FROM vouchers WHERE voucher_no = 'DEMO-VCH-202605-CLS'), 6, (SELECT id FROM accounts WHERE code = '5005'), '5005', '광고선전비', 'CREDIT', 0.00, 0.00, 7500000.00, '광고선전비 결산 대체', 6),
    ((SELECT id FROM vouchers WHERE voucher_no = 'DEMO-VCH-202605-CLS'), 7, (SELECT id FROM accounts WHERE code = '5007'), '5007', '복리후생비', 'CREDIT', 0.00, 0.00, 6000000.00, '복리후생비 결산 대체', 7),
    ((SELECT id FROM vouchers WHERE voucher_no = 'DEMO-VCH-202605-CLS'), 8, (SELECT id FROM accounts WHERE code = '5008'), '5008', '통신비', 'CREDIT', 0.00, 0.00, 1500000.00, '통신비 결산 대체', 8),
    ((SELECT id FROM vouchers WHERE voucher_no = 'DEMO-VCH-202605-CLS'), 9, (SELECT id FROM accounts WHERE code = '5009'), '5009', '여비교통비', 'CREDIT', 0.00, 0.00, 2500000.00, '여비교통비 결산 대체', 9),
    ((SELECT id FROM vouchers WHERE voucher_no = 'DEMO-VCH-202605-CLS'), 10, (SELECT id FROM accounts WHERE code = '5010'), '5010', '소모품비', 'CREDIT', 0.00, 0.00, 2000000.00, '소모품비 결산 대체', 10),
    ((SELECT id FROM vouchers WHERE voucher_no = 'DEMO-VCH-202605-CLS'), 11, (SELECT id FROM accounts WHERE code = '5006'), '5006', '감가상각비', 'CREDIT', 0.00, 0.00, 3000000.00, '감가상각비 결산 대체', 11),
    ((SELECT id FROM vouchers WHERE voucher_no = 'DEMO-VCH-202605-CLS'), 12, (SELECT id FROM accounts WHERE code = '3002'), '3002', '이익잉여금', 'CREDIT', 0.00, 0.00, 22500000.00, '당기순이익 이익잉여금 대체분개', 12),

    -- [4] 6월 기안/대기 상태별 전표 lines (DRAFT)
    ((SELECT id FROM vouchers WHERE voucher_no = 'DEMO-VCH-202606-D1'), 1, (SELECT id FROM accounts WHERE code = '5005'), '5005', '광고선전비', 'DEBIT', 0.00, 0.00, 1200000.00, '홍보 브로셔 인쇄비 청구', 1),
    ((SELECT id FROM vouchers WHERE voucher_no = 'DEMO-VCH-202606-D1'), 2, (SELECT id FROM accounts WHERE code = '1002'), '1002', '보통예금', 'CREDIT', 0.00, 0.00, 1200000.00, '홍보 브로셔 인쇄 대금 보통예금', 2),

    ((SELECT id FROM vouchers WHERE voucher_no = 'DEMO-VCH-202606-D2'), 1, (SELECT id FROM accounts WHERE code = '5007'), '5007', '복리후생비', 'DEBIT', 0.00, 0.00, 120000.00, '개발팀 야근 식대 결제건', 1),
    ((SELECT id FROM vouchers WHERE voucher_no = 'DEMO-VCH-202606-D2'), 2, (SELECT id FROM accounts WHERE code = '1002'), '1002', '보통예금', 'CREDIT', 0.00, 0.00, 120000.00, '개발팀 야근 식대 보통예금 이체', 2),

    ((SELECT id FROM vouchers WHERE voucher_no = 'DEMO-VCH-202606-D3'), 1, (SELECT id FROM accounts WHERE code = '5008'), '5008', '통신비', 'DEBIT', 0.00, 0.00, 180000.00, '사내 전산 보안 전용망 정기 청구', 1),
    ((SELECT id FROM vouchers WHERE voucher_no = 'DEMO-VCH-202606-D3'), 2, (SELECT id FROM accounts WHERE code = '1002'), '1002', '보통예금', 'CREDIT', 0.00, 0.00, 180000.00, '전용망 사용료 보통예금 이체', 2),

    ((SELECT id FROM vouchers WHERE voucher_no = 'DEMO-VCH-202606-D4'), 1, (SELECT id FROM accounts WHERE code = '5010'), '5010', '소모품비', 'DEBIT', 0.00, 0.00, 500000.00, '탕비실용 커피 믹스 및 종이컵 구입', 1),
    ((SELECT id FROM vouchers WHERE voucher_no = 'DEMO-VCH-202606-D4'), 2, (SELECT id FROM accounts WHERE code = '1002'), '1002', '보통예금', 'CREDIT', 0.00, 0.00, 500000.00, '소모품 구입 대금 보통예금 이체', 2),

    ((SELECT id FROM vouchers WHERE voucher_no = 'DEMO-VCH-202606-D5'), 1, (SELECT id FROM accounts WHERE code = '5009'), '5009', '여비교통비', 'DEBIT', 0.00, 0.00, 250000.00, '여수 공장 기술 미팅 출장 교통비 정산', 1),
    ((SELECT id FROM vouchers WHERE voucher_no = 'DEMO-VCH-202606-D5'), 2, (SELECT id FROM accounts WHERE code = '1002'), '1002', '보통예금', 'CREDIT', 0.00, 0.00, 250000.00, '출장 실비 보통예금 정산 송금', 2),

    -- REQUESTED (5건)
    ((SELECT id FROM vouchers WHERE voucher_no = 'DEMO-VCH-202606-R1'), 1, (SELECT id FROM accounts WHERE code = '1003'), '1003', '재고자산', 'DEBIT', 0.00, 0.00, 15000000.00, '선물세트 패키지용 크라프트 박스 수입 매입', 1),
    ((SELECT id FROM vouchers WHERE voucher_no = 'DEMO-VCH-202606-R1'), 2, (SELECT id FROM accounts WHERE code = '2001'), '2001', '외상매입금', 'CREDIT', 0.00, 0.00, 15000000.00, '자재 매입 외상매입금 결제 승인 요청', 2),

    ((SELECT id FROM vouchers WHERE voucher_no = 'DEMO-VCH-202606-R2'), 1, (SELECT id FROM accounts WHERE code = '5004'), '5004', '임차료', 'DEBIT', 0.00, 0.00, 2500000.00, '본사 정기 관리비 청구', 1),
    ((SELECT id FROM vouchers WHERE voucher_no = 'DEMO-VCH-202606-R2'), 2, (SELECT id FROM accounts WHERE code = '1002'), '1002', '보통예금', 'CREDIT', 0.00, 0.00, 2500000.00, '본사 관리비 보통예금 인출 결제 요청', 2),

    ((SELECT id FROM vouchers WHERE voucher_no = 'DEMO-VCH-202606-R3'), 1, (SELECT id FROM accounts WHERE code = '5005'), '5005', '광고선전비', 'DEBIT', 0.00, 0.00, 4500000.00, '구글 검색광고 매체 충전금 집행 요청', 1),
    ((SELECT id FROM vouchers WHERE voucher_no = 'DEMO-VCH-202606-R3'), 2, (SELECT id FROM accounts WHERE code = '1002'), '1002', '보통예금', 'CREDIT', 0.00, 0.00, 4500000.00, '구글 광고비 예금 이체 결제 요청', 2),

    ((SELECT id FROM vouchers WHERE voucher_no = 'DEMO-VCH-202606-R4'), 1, (SELECT id FROM accounts WHERE code = '5007'), '5007', '복리후생비', 'DEBIT', 0.00, 0.00, 3800000.00, '종합병원 임직원 건강검진 대행 정산', 1),
    ((SELECT id FROM vouchers WHERE voucher_no = 'DEMO-VCH-202606-R4'), 2, (SELECT id FROM accounts WHERE code = '1002'), '1002', '보통예금', 'CREDIT', 0.00, 0.00, 3800000.00, '건강검진 지원금 보통예금 결제 요청', 2),

    ((SELECT id FROM vouchers WHERE voucher_no = 'DEMO-VCH-202606-R5'), 1, (SELECT id FROM accounts WHERE code = '5010'), '5010', '소모품비', 'DEBIT', 0.00, 0.00, 850000.00, '사무실 프린터 드럼 및 복사 용지 벌크 구입', 1),
    ((SELECT id FROM vouchers WHERE voucher_no = 'DEMO-VCH-202606-R5'), 2, (SELECT id FROM accounts WHERE code = '1002'), '1002', '보통예금', 'CREDIT', 0.00, 0.00, 850000.00, '복사용지 대금 보통예금 결제 요청', 2),

    -- APPROVED (10건)
    ((SELECT id FROM vouchers WHERE voucher_no = 'DEMO-VCH-202606-A1'), 1, (SELECT id FROM accounts WHERE code = '1002'), '1002', '보통예금', 'DEBIT', 0.00, 0.00, 25000000.00, '메가카페 원자재 대량 여름 시즌 선매출 계약금 보통예금 입금', 1),
    ((SELECT id FROM vouchers WHERE voucher_no = 'DEMO-VCH-202606-A1'), 2, (SELECT id FROM accounts WHERE code = '4001'), '4001', '제품매출', 'CREDIT', 0.00, 0.00, 25000000.00, '메가카페 대량 제품 선급 매출액', 2),

    ((SELECT id FROM vouchers WHERE voucher_no = 'DEMO-VCH-202606-A2'), 1, (SELECT id FROM accounts WHERE code = '1002'), '1002', '보통예금', 'DEBIT', 0.00, 0.00, 12000000.00, '대한다업 기획 다기 상품 중도금 보통예금 입금', 1),
    ((SELECT id FROM vouchers WHERE voucher_no = 'DEMO-VCH-202606-A2'), 2, (SELECT id FROM accounts WHERE code = '4002'), '4002', '상품매출', 'CREDIT', 0.00, 0.00, 12000000.00, '대한다업 다기 상품 선급 매출액', 2),

    ((SELECT id FROM vouchers WHERE voucher_no = 'DEMO-VCH-202606-A3'), 1, (SELECT id FROM accounts WHERE code = '1003'), '1003', '재고자산', 'DEBIT', 0.00, 0.00, 8500000.00, '잎차 신선도 보관용 철제 틴캔 포장 매입', 1),
    ((SELECT id FROM vouchers WHERE voucher_no = 'DEMO-VCH-202606-A3'), 2, (SELECT id FROM accounts WHERE code = '2001'), '2001', '외상매입금', 'CREDIT', 0.00, 0.00, 8500000.00, '보관 용기 매입 외상매입금 발생', 2),

    ((SELECT id FROM vouchers WHERE voucher_no = 'DEMO-VCH-202606-A4'), 1, (SELECT id FROM accounts WHERE code = '5009'), '5009', '여비교통비', 'DEBIT', 0.00, 0.00, 1200000.00, '영업본부 영남지역 대리점 미팅 출장 교통 정산', 1),
    ((SELECT id FROM vouchers WHERE voucher_no = 'DEMO-VCH-202606-A4'), 2, (SELECT id FROM accounts WHERE code = '1002'), '1002', '보통예금', 'CREDIT', 0.00, 0.00, 1200000.00, '출장 항공/KTX 보통예금 정산', 2),

    ((SELECT id FROM vouchers WHERE voucher_no = 'DEMO-VCH-202606-A5'), 1, (SELECT id FROM accounts WHERE code = '5008'), '5008', '통신비', 'DEBIT', 0.00, 0.00, 3200000.00, '사내 ERP 연계 클라우드 가상 서버 사용료', 1),
    ((SELECT id FROM vouchers WHERE voucher_no = 'DEMO-VCH-202606-A5'), 2, (SELECT id FROM accounts WHERE code = '1002'), '1002', '보통예금', 'CREDIT', 0.00, 0.00, 3200000.00, 'AWS 서버 월간 이용 보통예금 자동 이체', 2),

    ((SELECT id FROM vouchers WHERE voucher_no = 'DEMO-VCH-202606-A6'), 1, (SELECT id FROM accounts WHERE code = '5005'), '5005', '광고선전비', 'DEBIT', 0.00, 0.00, 2000000.00, '유기농 말차 영문 리플렛 외주 번역료', 1),
    ((SELECT id FROM vouchers WHERE voucher_no = 'DEMO-VCH-202606-A6'), 2, (SELECT id FROM accounts WHERE code = '1002'), '1002', '보통예금', 'CREDIT', 0.00, 0.00, 2000000.00, '외주 번역료 보통예금 이체 완료', 2),

    ((SELECT id FROM vouchers WHERE voucher_no = 'DEMO-VCH-202606-A7'), 1, (SELECT id FROM accounts WHERE code = '5007'), '5007', '복리후생비', 'DEBIT', 0.00, 0.00, 450000.00, '고객 상담 지원실 노후 냉난방기 긴급 수리', 1),
    ((SELECT id FROM vouchers WHERE voucher_no = 'DEMO-VCH-202606-A7'), 2, (SELECT id FROM accounts WHERE code = '1002'), '1002', '보통예금', 'CREDIT', 0.00, 0.00, 450000.00, '상담실 냉난방기 긴급 부품 교체 보통예금', 2),

    ((SELECT id FROM vouchers WHERE voucher_no = 'DEMO-VCH-202606-A8'), 1, (SELECT id FROM accounts WHERE code = '5007'), '5007', '복리후생비', 'DEBIT', 0.00, 0.00, 950000.00, '사내 프론트엔드 역량 강화 강좌 라이선스', 1),
    ((SELECT id FROM vouchers WHERE voucher_no = 'DEMO-VCH-202606-A8'), 2, (SELECT id FROM accounts WHERE code = '1002'), '1002', '보통예금', 'CREDIT', 0.00, 0.00, 950000.00, '직무 강좌 구입비 보통예금 송금', 2),

    ((SELECT id FROM vouchers WHERE voucher_no = 'DEMO-VCH-202606-A9'), 1, (SELECT id FROM accounts WHERE code = '1005'), '1005', '비품', 'DEBIT', 0.00, 0.00, 180000.00, '사내 화상 회의실 연계 스마트 빔 프로젝터 거치대', 1),
    ((SELECT id FROM vouchers WHERE voucher_no = 'DEMO-VCH-202606-A9'), 2, (SELECT id FROM accounts WHERE code = '1002'), '1002', '보통예금', 'CREDIT', 0.00, 0.00, 180000.00, '회의실 비품 보통예금 결제 송금', 2),

    ((SELECT id FROM vouchers WHERE voucher_no = 'DEMO-VCH-202606-A10'), 1, (SELECT id FROM accounts WHERE code = '5005'), '5005', '광고선전비', 'DEBIT', 0.00, 0.00, 3000000.00, '인사팀 신규 ERP 교육 온라인 수강 연간 플랫폼 구독', 1),
    ((SELECT id FROM vouchers WHERE voucher_no = 'DEMO-VCH-202606-A10'), 2, (SELECT id FROM accounts WHERE code = '1002'), '1002', '보통예금', 'CREDIT', 0.00, 0.00, 3000000.00, '채용/교육 연간 플랫폼 구독 예금 이체', 2),

    -- REJECTED (3건)
    ((SELECT id FROM vouchers WHERE voucher_no = 'DEMO-VCH-202606-REJ1'), 1, (SELECT id FROM accounts WHERE code = '5010'), '5010', '소모품비', 'DEBIT', 0.00, 0.00, 500000.00, '개인 용품 구입비 소모품비 이중 청구', 1),
    ((SELECT id FROM vouchers WHERE voucher_no = 'DEMO-VCH-202606-REJ1'), 2, (SELECT id FROM accounts WHERE code = '1002'), '1002', '보통예금', 'CREDIT', 0.00, 0.00, 500000.00, '이중 청구에 따른 예금 반제 (반려)', 2),

    ((SELECT id FROM vouchers WHERE voucher_no = 'DEMO-VCH-202606-REJ2'), 1, (SELECT id FROM accounts WHERE code = '1003'), '1003', '재고자산', 'DEBIT', 0.00, 0.00, 1500000.00, '사전 기안 미비 부자재 수입 매입 건', 1),
    ((SELECT id FROM vouchers WHERE voucher_no = 'DEMO-VCH-202606-REJ2'), 2, (SELECT id FROM accounts WHERE code = '2001'), '2001', '외상매입금', 'CREDIT', 0.00, 0.00, 1500000.00, '외상거래 취소 및 반려 분개', 2),

    ((SELECT id FROM vouchers WHERE voucher_no = 'DEMO-VCH-202606-REJ3'), 1, (SELECT id FROM accounts WHERE code = '5009'), '5009', '여비교통비', 'DEBIT', 0.00, 0.00, 120000.00, '외근 대중교통비 영수증 증빙 중복 청구 건', 1),
    ((SELECT id FROM vouchers WHERE voucher_no = 'DEMO-VCH-202606-REJ3'), 2, (SELECT id FROM accounts WHERE code = '1002'), '1002', '보통예금', 'CREDIT', 0.00, 0.00, 120000.00, '대중교통비 이중청구 예금 반제 (반려)', 2);


-- -----------------------------------------------------------------
-- payroll ledgers / closing logs (급여대장 및 결산 로그는 AccountingDataSeeder에 의해 코드로 기동 시 자동 생성되므로 주석 처리함)
-- -----------------------------------------------------------------
-- INSERT INTO accounting_payroll_ledgers (
--     attribution_year_month, payroll_type, tax_type, settlement_cycle, target_period_mode,
--     payment_date, payment_year_month, ledger_name, settlement_item_selection_mode,
--     employee_selection_mode, status, pre_employee_checked, pre_insurance_calculated,
--     pre_settlement_validated, pre_account_validated, head_count, gross_amount,
--     deduction_amount, net_amount, bonus_rate_or_amount, journal_entry_id, created_by, created_at, updated_at
-- )
-- VALUES
--     ('2026-03', 'SALARY', 'TAXABLE', 'MONTHLY', 'BULK', '2026-03-25', '2026-03', '데모 급여대장 2026-03', 'ALL', 'ALL', 'POSTED', 1, 1, 1, 1, 7, 28600000.00, 3100000.00, 25500000.00, NULL, NULL, 'demo-seed', NOW() - INTERVAL 70 DAY, NOW() - INTERVAL 70 DAY),
--     ('2026-04', 'SALARY', 'TAXABLE', 'MONTHLY', 'BULK', '2026-04-25', '2026-04', '데모 급여대장 2026-04', 'ALL', 'ALL', 'POSTED', 1, 1, 1, 1, 7, 28600000.00, 3100000.00, 25500000.00, NULL, NULL, 'demo-seed', NOW() - INTERVAL 40 DAY, NOW() - INTERVAL 40 DAY),
--     ('2026-05', 'SALARY', 'TAXABLE', 'MONTHLY', 'BULK', '2026-06-10', '2026-05', '데모 급여대장 2026-05', 'ALL', 'ALL', 'CALCULATED', 1, 1, 1, 1, 7, 28600000.00, 3100000.00, 25500000.00, NULL, NULL, 'demo-seed', NOW() - INTERVAL 5 DAY, NOW() - INTERVAL 5 DAY)
-- ON DUPLICATE KEY UPDATE
--     ledger_name = VALUES(ledger_name),
--     status = VALUES(status),
--     head_count = VALUES(head_count),
--     gross_amount = VALUES(gross_amount),
--     deduction_amount = VALUES(deduction_amount),
--     net_amount = VALUES(net_amount),
--     updated_at = NOW();

-- INSERT INTO accounting_closing_logs (
--     accounting_period_id, action_type, from_status, to_status, actor, ip_address, message, action_at
-- )
-- VALUES
--     (
--         (SELECT id FROM accounting_periods WHERE fiscal_year = 2026 AND fiscal_month = 4),
--         'MONTH_CLOSED',
--         'OPEN',
--         'CLOSED',
--         'demo-seed',
--         '127.0.0.1',
--         '회계 대시보드용 데모 마감 로그',
--         NOW() - INTERVAL 30 DAY
--     );

SELECT 1;
