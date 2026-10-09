-- Tài khoản mẫu (BACKEND_SCHEMA §8.5). Mật khẩu của cả 3 tài khoản: Demo@1234 (chỉ lưu hash BCrypt).
INSERT INTO users (email, password_hash, full_name, phone, role) VALUES
    ('admin@demo.local',    '$2a$10$mreYm.pOnhP/p1QIEh5GHe0Lc18Mf6EUrQMCZB4c064MflGwIiVMy', 'Quản trị viên Demo', '0900000001', 'ADMIN'),
    ('staff@demo.local',    '$2a$10$d4cEhHAMVgXhX9qAVmatWu1dKvRslYDt95UIw7uQfCZ8jXC44fLTe', 'Nhân viên Demo',     '0900000002', 'STAFF'),
    ('customer@demo.local', '$2a$10$rSNzjIMI5DsXYU0zbPKUq.zuWUTIQ0HP7U2hMoIhVTtHx31n2CRxu', 'Khách hàng Demo',    '0900000003', 'CUSTOMER');
