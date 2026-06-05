-- Minimal bootstrap data only.
-- Domain sample data should be created by runtime initializers, not by SQL startup seeds.

INSERT INTO members (
    id,
    login_id,
    password,
    name,
    role,
    active,
    created_at,
    updated_at
)
VALUES
    (
        1,
        'admin',
        '$2a$10$yOCLiBL.jnjWNnTz8H3MBugkf7Nk73W1smvvQSclH3Gj8dgyd1ERG',
        'System Admin',
        'ADMIN',
        1,
        NOW(),
        NOW()
    ),
    (
        2,
        'inventory',
        '$2a$10$jYIYRocKBwTDRrjeOc.I9uhI8Bqzn9gHzrVT0rj/FNk9sea7Qwv.W',
        'Inventory Manager',
        'INVENTORY',
        1,
        NOW(),
        NOW()
    ),
    (
        3,
        'hr',
        '$2a$10$2zR.ygAdbZzH2FDe5dBL0.uXx91hUNqbFyt8xn54Gf.ISAC/rWI9i',
        'HR Manager',
        'HR',
        1,
        NOW(),
        NOW()
    )
ON DUPLICATE KEY UPDATE
    password = VALUES(password),
    name = VALUES(name),
    role = VALUES(role),
    active = VALUES(active),
    updated_at = NOW();
