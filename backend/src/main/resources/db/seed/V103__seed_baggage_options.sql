-- Bảng giá hành lý mẫu (BACKEND_SCHEMA §8.4): 4 mức mỗi hãng, hãng quốc tế giá gấp đôi.
INSERT INTO baggage_options (airline_code, weight_kg, price)
SELECT a.code, w.weight_kg, w.price * a.factor
FROM (VALUES ('VN', 1), ('VJ', 1), ('QH', 1), ('SQ', 2), ('TG', 2)) AS a (code, factor)
CROSS JOIN (VALUES (15, 250000), (20, 350000), (25, 450000), (30, 550000)) AS w (weight_kg, price);
