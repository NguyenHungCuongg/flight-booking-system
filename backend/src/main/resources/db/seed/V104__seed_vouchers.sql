-- Voucher mẫu (BACKEND_SCHEMA §8.4). Hiệu lực tính từ nửa đêm ngày chạy migration, để lệch đồng hồ nhỏ giữa JVM và DB
-- không làm voucher vừa seed báo chưa đến hạn.
INSERT INTO vouchers (code, description, discount_type, discount_value, max_discount, min_order_amount,
                      valid_from, valid_to, usage_limit)
VALUES ('WELCOME10', 'Giảm 10% (tối đa 200.000 đ) cho mọi đơn hàng', 'PERCENT', 10, 200000, 0,
        CURRENT_DATE, CURRENT_DATE + interval '1 year', NULL),
       ('SALE100K', 'Giảm 100.000 đ cho đơn từ 1.000.000 đ', 'FIXED', 100000, NULL, 1000000,
        CURRENT_DATE, CURRENT_DATE + interval '3 months', 100);
