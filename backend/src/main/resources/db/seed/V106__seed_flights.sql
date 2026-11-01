-- Chuyến bay mẫu (BACKEND_SCHEMA §8.6): 20 mẫu, mỗi mẫu một chuyến mỗi ngày trong 60 ngày tính từ ngày chạy
-- migration (theo giờ Việt Nam), tổng 1.200 chuyến. Cần V100–V102. Giờ đi là giờ địa phương của sân bay đi.
CREATE TEMP TABLE seed_templates (
    airline_code VARCHAR(2), flight_number VARCHAR(6), dep VARCHAR(3), arr VARCHAR(3),
    local_time TIME, minutes INT, aircraft VARCHAR(50), eco_seats INT, bus_seats INT, base_price BIGINT
) ON COMMIT DROP;
INSERT INTO seed_templates VALUES
    ('VN', 'VN213',  'HAN', 'SGN', '06:00', 130, 'Airbus A321',     168, 16, 1290000),
    ('VN', 'VN220',  'SGN', 'HAN', '17:00', 130, 'Airbus A321',     168, 16, 1290000),
    ('VN', 'VN1825', 'SGN', 'PQC', '11:00',  60, 'Airbus A321',     168, 16,  790000),
    ('VN', 'VN1826', 'PQC', 'SGN', '13:30',  60, 'Airbus A321',     168, 16,  790000),
    ('VN', 'VN1233', 'HAN', 'PQC', '09:00', 130, 'Airbus A321',     168, 16, 1490000),
    ('VN', 'VN1232', 'PQC', 'HAN', '16:00', 130, 'Airbus A321',     168, 16, 1490000),
    ('VN', 'VN125',  'HAN', 'DAD', '07:30',  80, 'Airbus A321',     168, 16,  990000),
    ('VN', 'VN136',  'DAD', 'HAN', '18:00',  80, 'Airbus A321',     168, 16,  990000),
    ('VN', 'VN651',  'SGN', 'SIN', '09:30', 120, 'Airbus A321',     168, 16, 2190000),
    ('VN', 'VN652',  'SIN', 'SGN', '14:00', 120, 'Airbus A321',     168, 16, 2190000),
    ('VN', 'VN416',  'HAN', 'ICN', '23:30', 285, 'Airbus A350-900', 271, 29, 5490000),
    ('VN', 'VN417',  'ICN', 'HAN', '10:30', 300, 'Airbus A350-900', 271, 29, 5490000),
    ('VJ', 'VJ123',  'HAN', 'SGN', '08:00', 130, 'Airbus A321neo',  220, 10,  990000),
    ('VJ', 'VJ122',  'SGN', 'HAN', '19:00', 130, 'Airbus A321neo',  220, 10,  990000),
    ('QH', 'QH201',  'HAN', 'SGN', '10:00', 130, 'Boeing 787-9',    270, 24, 1190000),
    ('QH', 'QH202',  'SGN', 'HAN', '15:00', 130, 'Boeing 787-9',    270, 24, 1190000),
    ('SQ', 'SQ176',  'SGN', 'SIN', '13:00', 125, 'Airbus A350-900', 253, 40, 2690000),
    ('SQ', 'SQ175',  'SIN', 'SGN', '10:00', 120, 'Airbus A350-900', 253, 40, 2690000),
    ('TG', 'TG561',  'HAN', 'BKK', '11:00', 110, 'Airbus A320',     150, 12, 2390000),
    ('TG', 'TG560',  'BKK', 'HAN', '08:00', 105, 'Airbus A320',     150, 12, 2390000);

-- Hệ số giá theo tên gói (BACKEND_SCHEMA §8.3). Gói trùng tên giữa các hãng có cùng hệ số.
CREATE TEMP TABLE seed_factors (name VARCHAR(50), factor NUMERIC) ON COMMIT DROP;
INSERT INTO seed_factors VALUES
    ('Economy Lite', 1.00), ('Economy Classic', 1.45), ('Economy Flex', 1.90), ('Business Classic', 3.60),
    ('Eco', 1.00), ('Deluxe', 1.40), ('SkyBoss', 2.80),
    ('Economy Saver', 1.00), ('Economy Smart', 1.45), ('Business Flex', 3.40),
    ('Economy Standard', 1.40), ('Business', 3.50);

-- Một câu lệnh: hạng ghế và giá bán chỉ gắn vào đúng các chuyến vừa tạo. Ghế còn ban đầu bằng tổng ghế; giá bán
-- = giá gốc × hệ số, làm tròn đến 10.000 đồng.
WITH new_flights AS (
    INSERT INTO flights (airline_code, flight_number, departure_airport, arrival_airport,
                         departure_time, arrival_time, aircraft_model)
    SELECT t.airline_code, t.flight_number, t.dep, t.arr,
           (d.day + t.local_time) AT TIME ZONE a.timezone,                       -- giờ địa phương → timestamptz
           ((d.day + t.local_time) AT TIME ZONE a.timezone) + t.minutes * INTERVAL '1 minute',
           t.aircraft
    FROM seed_templates t
    JOIN airports a ON a.code = t.dep
    CROSS JOIN generate_series(0, 59) AS i
    CROSS JOIN LATERAL (VALUES ((now() AT TIME ZONE 'Asia/Ho_Chi_Minh')::date + i)) AS d (day)
    RETURNING id, flight_number
), new_cabins AS (
    INSERT INTO flight_cabins (flight_id, cabin_class, total_seats, available_seats)
    SELECT n.id, c.cabin_class, c.seats, c.seats
    FROM new_flights n
    JOIN seed_templates t USING (flight_number)
    CROSS JOIN LATERAL (VALUES ('ECONOMY', t.eco_seats), ('BUSINESS', t.bus_seats)) AS c (cabin_class, seats)
)
INSERT INTO flight_fares (flight_id, fare_family_id, price)
SELECT n.id, fam.id, round(t.base_price * f.factor / 10000) * 10000
FROM new_flights n
JOIN seed_templates t USING (flight_number)
JOIN fare_families fam ON fam.airline_code = t.airline_code
JOIN seed_factors f ON f.name = fam.name;
