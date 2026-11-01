-- Gói giá mẫu (BACKEND_SCHEMA §8.3), không phải chính sách thật của các hãng.
-- Gói không cho hoàn thì refund_fee = 0. Cột "Hệ số" của tài liệu chỉ dùng khi sinh giá bán ở V106.
INSERT INTO fare_families (airline_code, cabin_class, name, carry_on_kg, checked_baggage_kg,
                           refundable, refund_fee, changeable, change_fee) VALUES
    ('VN', 'ECONOMY',  'Economy Lite',     10,  0, FALSE,      0, TRUE, 600000),
    ('VN', 'ECONOMY',  'Economy Classic',  10, 23, TRUE,  600000, TRUE, 300000),
    ('VN', 'ECONOMY',  'Economy Flex',     10, 23, TRUE,  300000, TRUE,      0),
    ('VN', 'BUSINESS', 'Business Classic', 18, 32, TRUE,  600000, TRUE,      0),
    ('VJ', 'ECONOMY',  'Eco',               7,  0, FALSE,      0, TRUE, 400000),
    ('VJ', 'ECONOMY',  'Deluxe',            7, 20, FALSE,      0, TRUE,      0),
    ('VJ', 'BUSINESS', 'SkyBoss',          10, 30, TRUE,  500000, TRUE,      0),
    ('QH', 'ECONOMY',  'Economy Saver',     7,  0, FALSE,      0, TRUE, 500000),
    ('QH', 'ECONOMY',  'Economy Smart',     7, 20, TRUE,  500000, TRUE, 300000),
    ('QH', 'BUSINESS', 'Business Flex',    14, 40, TRUE,  300000, TRUE,      0);

-- SQ và TG dùng chung bộ gói.
INSERT INTO fare_families (airline_code, cabin_class, name, carry_on_kg, checked_baggage_kg,
                           refundable, refund_fee, changeable, change_fee)
SELECT a.code, f.cabin_class, f.name, f.carry_on_kg, f.checked_baggage_kg,
       f.refundable, f.refund_fee, f.changeable, f.change_fee
FROM (VALUES ('SQ'), ('TG')) AS a (code)
CROSS JOIN (VALUES
    ('ECONOMY',  'Economy Lite',      7, 20, FALSE,       0, TRUE, 800000),
    ('ECONOMY',  'Economy Standard',  7, 30, TRUE,  1000000, TRUE, 500000),
    ('BUSINESS', 'Business',         14, 40, TRUE,  1000000, TRUE,      0)
) AS f (cabin_class, name, carry_on_kg, checked_baggage_kg, refundable, refund_fee, changeable, change_fee);
