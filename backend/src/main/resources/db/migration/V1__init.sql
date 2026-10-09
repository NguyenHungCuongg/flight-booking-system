-- =====================================================================
-- V1__init.sql — Flight Booking System
-- =====================================================================

-- ------------------------------------------------------------ identity
CREATE TABLE users (
    id             BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    email          VARCHAR(255) NOT NULL UNIQUE CHECK (email = lower(email)),   -- BR-100: lưu chữ thường
    password_hash  VARCHAR(100) NOT NULL,                                       -- BCrypt
    full_name      VARCHAR(100) NOT NULL,
    phone          VARCHAR(20)  NOT NULL,
    role           VARCHAR(20)  NOT NULL CHECK (role IN ('CUSTOMER', 'STAFF', 'ADMIN')),
    status         VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'LOCKED')),
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE password_reset_tokens (
    id          BIGINT      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id     BIGINT      NOT NULL REFERENCES users (id),
    token_hash  VARCHAR(64) NOT NULL UNIQUE,      -- SHA-256 (hex) của token, không lưu token gốc
    expires_at  TIMESTAMPTZ NOT NULL,
    used_at     TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- ------------------------------------------------------------ common
CREATE TABLE system_settings (
    key          VARCHAR(100) PRIMARY KEY,
    value        VARCHAR(100) NOT NULL,
    description  VARCHAR(255) NOT NULL,
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_by   BIGINT       REFERENCES users (id)
);

INSERT INTO system_settings (key, value, description) VALUES
    ('booking.hold_minutes',                  '15',  'Thời gian giữ chỗ và hạn thanh toán đổi chuyến (phút)'),
    ('booking.min_hours_before_departure',    '3',   'Không bán chuyến cất cánh trong vòng N giờ tới'),
    ('booking.max_seated_passengers',         '9',   'Số khách chiếm ghế tối đa mỗi booking'),
    ('pricing.child_percent',                 '90',  'Giá vé trẻ em (% giá người lớn)'),
    ('pricing.infant_percent',                '10',  'Giá vé em bé (% giá người lớn)'),
    ('aftersales.min_hours_before_departure', '24',  'Hạn chót hoàn/đổi: trước giờ cất cánh N giờ'),
    ('search.min_connection_minutes',         '60',  'Thời gian nối chuyến tối thiểu (phút)'),
    ('search.max_connection_minutes',         '720', 'Thời gian nối chuyến tối đa (phút)');

-- ------------------------------------------------------------ catalog
CREATE TABLE airports (
    code          VARCHAR(3)   PRIMARY KEY CHECK (code ~ '^[A-Z]{3}$'),
    name          VARCHAR(100) NOT NULL,
    city          VARCHAR(100) NOT NULL,
    country_code  VARCHAR(2)   NOT NULL CHECK (country_code ~ '^[A-Z]{2}$'),
    timezone      VARCHAR(50)  NOT NULL,          -- IANA, VD 'Asia/Ho_Chi_Minh'
    active        BOOLEAN      NOT NULL DEFAULT TRUE
);

CREATE TABLE airlines (
    code           VARCHAR(2)   PRIMARY KEY CHECK (code ~ '^[A-Z0-9]{2}$'),
    name           VARCHAR(100) NOT NULL,
    ticket_prefix  VARCHAR(3)   NOT NULL UNIQUE CHECK (ticket_prefix ~ '^[0-9]{3}$'),   -- BR-43
    active         BOOLEAN      NOT NULL DEFAULT TRUE
);

CREATE TABLE fare_families (
    id                  BIGINT      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    airline_code        VARCHAR(2)  NOT NULL REFERENCES airlines (code),
    cabin_class         VARCHAR(20) NOT NULL CHECK (cabin_class IN ('ECONOMY', 'PREMIUM_ECONOMY', 'BUSINESS', 'FIRST')),
    name                VARCHAR(50) NOT NULL,
    carry_on_kg         INT         NOT NULL CHECK (carry_on_kg >= 0),
    checked_baggage_kg  INT         NOT NULL CHECK (checked_baggage_kg >= 0),
    refundable          BOOLEAN     NOT NULL,
    refund_fee          BIGINT      NOT NULL DEFAULT 0 CHECK (refund_fee >= 0),   -- mỗi khách chiếm ghế, mỗi chiều
    changeable          BOOLEAN     NOT NULL,
    change_fee          BIGINT      NOT NULL DEFAULT 0 CHECK (change_fee >= 0),   -- mỗi khách chiếm ghế, mỗi chiều
    active              BOOLEAN     NOT NULL DEFAULT TRUE,
    UNIQUE (airline_code, name)                                                   -- BR-92
);

CREATE TABLE baggage_options (
    id            BIGINT     GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    airline_code  VARCHAR(2) NOT NULL REFERENCES airlines (code),
    weight_kg     INT        NOT NULL CHECK (weight_kg > 0),
    price         BIGINT     NOT NULL CHECK (price > 0),    -- mỗi hành khách, mỗi chiều (BR-22)
    active        BOOLEAN    NOT NULL DEFAULT TRUE,
    UNIQUE (airline_code, weight_kg)                        -- BR-92
);

-- ------------------------------------------------------------ flight
CREATE TABLE flights (
    id                 BIGINT      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    airline_code       VARCHAR(2)  NOT NULL REFERENCES airlines (code),
    flight_number      VARCHAR(6)  NOT NULL CHECK (flight_number ~ '^[A-Z0-9]{2}[0-9]{1,4}$'),
    departure_airport  VARCHAR(3)  NOT NULL REFERENCES airports (code),
    arrival_airport    VARCHAR(3)  NOT NULL REFERENCES airports (code),
    departure_time     TIMESTAMPTZ NOT NULL,
    arrival_time       TIMESTAMPTZ NOT NULL,
    aircraft_model     VARCHAR(50),
    status             VARCHAR(20) NOT NULL DEFAULT 'SCHEDULED' CHECK (status IN ('SCHEDULED', 'CANCELLED')),
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (left(flight_number, 2) = airline_code),          -- BR-80
    CHECK (departure_airport <> arrival_airport),           -- BR-81
    CHECK (arrival_time > departure_time),                  -- BR-81
    UNIQUE (airline_code, flight_number, departure_time)    -- BR-80
);
CREATE INDEX ix_flights_route_time  ON flights (departure_airport, arrival_airport, departure_time);
CREATE INDEX ix_flights_origin_time ON flights (departure_airport, departure_time);

CREATE TABLE flight_cabins (
    id               BIGINT      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    flight_id        BIGINT      NOT NULL REFERENCES flights (id) ON DELETE CASCADE,
    cabin_class      VARCHAR(20) NOT NULL CHECK (cabin_class IN ('ECONOMY', 'PREMIUM_ECONOMY', 'BUSINESS', 'FIRST')),
    total_seats      INT         NOT NULL CHECK (total_seats > 0),
    available_seats  INT         NOT NULL,                -- chỉ đổi bằng Q-01, Q-02, Q-03
    UNIQUE (flight_id, cabin_class),
    CHECK (available_seats BETWEEN 0 AND total_seats)     -- DB chặn bán vượt ghế
);

CREATE TABLE flight_fares (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    flight_id       BIGINT NOT NULL REFERENCES flights (id) ON DELETE CASCADE,
    fare_family_id  BIGINT NOT NULL REFERENCES fare_families (id),
    price           BIGINT NOT NULL CHECK (price > 0),    -- giá người lớn của chặng (BR-20)
    UNIQUE (flight_id, fare_family_id)
);
CREATE INDEX ix_flight_fares_fare_family ON flight_fares (fare_family_id);

-- ------------------------------------------------------------ promotion
CREATE TABLE vouchers (
    id                BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code              VARCHAR(30)  NOT NULL UNIQUE CHECK (code ~ '^[A-Z0-9]{3,30}$'),
    description       VARCHAR(255) NOT NULL,
    discount_type     VARCHAR(10)  NOT NULL CHECK (discount_type IN ('PERCENT', 'FIXED')),
    discount_value    BIGINT       NOT NULL CHECK (discount_value > 0),   -- phần trăm (1–100) hoặc số đồng
    max_discount      BIGINT       CHECK (max_discount > 0),              -- chỉ dùng cho PERCENT; NULL = không giới hạn
    min_order_amount  BIGINT       NOT NULL DEFAULT 0 CHECK (min_order_amount >= 0),
    valid_from        TIMESTAMPTZ  NOT NULL,
    valid_to          TIMESTAMPTZ  NOT NULL,
    usage_limit       INT          CHECK (usage_limit > 0),               -- NULL = không giới hạn
    used_count        INT          NOT NULL DEFAULT 0,                    -- chỉ đổi bằng Q-14, Q-15
    active            BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CHECK (valid_to > valid_from),
    CHECK (discount_type <> 'PERCENT' OR discount_value <= 100),
    CHECK (discount_type = 'PERCENT' OR max_discount IS NULL),
    CHECK (used_count >= 0 AND (usage_limit IS NULL OR used_count <= usage_limit))
);

-- ------------------------------------------------------------ booking
CREATE TABLE bookings (
    id               BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code             VARCHAR(6)   NOT NULL UNIQUE CHECK (code ~ '^[A-HJ-NP-Z2-9]{6}$'),   -- BR-46
    user_id          BIGINT       NOT NULL REFERENCES users (id),
    status           VARCHAR(20)  NOT NULL CHECK (status IN ('PENDING_PAYMENT', 'ISSUED', 'EXPIRED', 'CANCELLED', 'REFUNDED')),
    contact_name     VARCHAR(100) NOT NULL,
    contact_email    VARCHAR(255) NOT NULL,
    contact_phone    VARCHAR(20)  NOT NULL,
    voucher_id       BIGINT       REFERENCES vouchers (id),
    fare_total       BIGINT       NOT NULL CHECK (fare_total >= 0),       -- tổng giá vé lúc giữ chỗ
    baggage_total    BIGINT       NOT NULL CHECK (baggage_total >= 0),
    discount_total   BIGINT       NOT NULL CHECK (discount_total >= 0),
    total_amount     BIGINT       NOT NULL CHECK (total_amount >= 0),
    hold_expires_at  TIMESTAMPTZ  NOT NULL,
    issued_at        TIMESTAMPTZ,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CHECK (total_amount = fare_total + baggage_total - discount_total),   -- BR-23
    CHECK (voucher_id IS NOT NULL OR discount_total = 0)
);
CREATE INDEX ix_bookings_user ON bookings (user_id, created_at DESC);
CREATE INDEX ix_bookings_hold ON bookings (hold_expires_at) WHERE status = 'PENDING_PAYMENT';
-- BR-30: mỗi tài khoản dùng một voucher tối đa một lần; booking EXPIRED/CANCELLED trả lại quyền dùng
CREATE UNIQUE INDEX uq_bookings_voucher_user ON bookings (voucher_id, user_id)
    WHERE voucher_id IS NOT NULL AND status IN ('PENDING_PAYMENT', 'ISSUED', 'REFUNDED');

CREATE TABLE booking_journeys (
    id                  BIGINT      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    booking_id          BIGINT      NOT NULL REFERENCES bookings (id),
    direction           VARCHAR(10) NOT NULL CHECK (direction IN ('OUTBOUND', 'RETURN')),
    status              VARCHAR(10) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'REFUNDED')),
    airline_code        VARCHAR(2)  NOT NULL REFERENCES airlines (code),
    fare_family_id      BIGINT      NOT NULL REFERENCES fare_families (id),
    -- snapshot gói giá tại thời điểm giữ chỗ (BR-24)
    fare_family_name    VARCHAR(50) NOT NULL,
    cabin_class         VARCHAR(20) NOT NULL CHECK (cabin_class IN ('ECONOMY', 'PREMIUM_ECONOMY', 'BUSINESS', 'FIRST')),
    carry_on_kg         INT         NOT NULL,
    checked_baggage_kg  INT         NOT NULL,
    refundable          BOOLEAN     NOT NULL,
    refund_fee          BIGINT      NOT NULL,
    changeable          BOOLEAN     NOT NULL,
    change_fee          BIGINT      NOT NULL,
    discount_amount     BIGINT      NOT NULL DEFAULT 0 CHECK (discount_amount >= 0),   -- BR-33
    UNIQUE (booking_id, direction)
);

CREATE TABLE booking_segments (
    id             BIGINT      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    journey_id     BIGINT      NOT NULL REFERENCES booking_journeys (id),
    flight_id      BIGINT      NOT NULL REFERENCES flights (id),   -- không CASCADE: chặn xoá chuyến đã có booking (BR-82)
    seq            SMALLINT    NOT NULL CHECK (seq IN (1, 2)),
    status         VARCHAR(20) NOT NULL CHECK (status IN ('ACTIVE', 'PENDING_CHANGE', 'CANCELLED', 'REPLACED')),
    adult_price    BIGINT      NOT NULL CHECK (adult_price > 0),   -- snapshot flight_fares.price
    reschedule_id  BIGINT,                                         -- yêu cầu đổi đã tạo chặng này; FK thêm ở dưới
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (status <> 'PENDING_CHANGE' OR reschedule_id IS NOT NULL)
);
CREATE INDEX ix_segments_journey ON booking_segments (journey_id);
CREATE INDEX ix_segments_flight  ON booking_segments (flight_id, status);
CREATE UNIQUE INDEX uq_segments_active_seq ON booking_segments (journey_id, seq) WHERE status = 'ACTIVE';

CREATE TABLE passengers (
    id               BIGINT      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    booking_id       BIGINT      NOT NULL REFERENCES bookings (id),
    type             VARCHAR(10) NOT NULL CHECK (type IN ('ADULT', 'CHILD', 'INFANT')),
    last_name        VARCHAR(50) NOT NULL CHECK (last_name ~ '^[A-Z]+( [A-Z]+)*$'),    -- BR-12
    first_name       VARCHAR(50) NOT NULL CHECK (first_name ~ '^[A-Z]+( [A-Z]+)*$'),   -- BR-12
    gender           VARCHAR(10) NOT NULL CHECK (gender IN ('MALE', 'FEMALE')),
    date_of_birth    DATE        NOT NULL,
    nationality      VARCHAR(2)  CHECK (nationality ~ '^[A-Z]{2}$'),                   -- BR-13
    passport_number  VARCHAR(20) CHECK (passport_number ~ '^[A-Z0-9]{6,20}$'),         -- BR-13
    passport_expiry  DATE
);
CREATE INDEX ix_passengers_booking ON passengers (booking_id);

CREATE TABLE tickets (
    id             BIGINT      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    segment_id     BIGINT      NOT NULL REFERENCES booking_segments (id),
    passenger_id   BIGINT      NOT NULL REFERENCES passengers (id),
    fare_amount    BIGINT      NOT NULL CHECK (fare_amount >= 0),          -- BR-21
    ticket_number  VARCHAR(13) UNIQUE CHECK (ticket_number ~ '^[0-9]{13}$'),   -- BR-43; NULL cho tới khi xuất vé
    issued_at      TIMESTAMPTZ,
    UNIQUE (segment_id, passenger_id),
    CHECK ((ticket_number IS NULL) = (issued_at IS NULL))
);
CREATE INDEX ix_tickets_passenger ON tickets (passenger_id);
CREATE INDEX ix_tickets_issued_at ON tickets (issued_at) WHERE issued_at IS NOT NULL;
CREATE SEQUENCE ticket_number_seq;

CREATE TABLE booking_baggage (
    id                 BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    journey_id         BIGINT NOT NULL REFERENCES booking_journeys (id),
    passenger_id       BIGINT NOT NULL REFERENCES passengers (id),
    baggage_option_id  BIGINT NOT NULL REFERENCES baggage_options (id),
    weight_kg          INT    NOT NULL CHECK (weight_kg > 0),   -- snapshot
    price              BIGINT NOT NULL CHECK (price > 0),       -- snapshot
    UNIQUE (journey_id, passenger_id)                           -- BR-22: tối đa một mức mỗi khách mỗi chiều
);

-- ------------------------------------------------------------ aftersales
CREATE TABLE reschedules (
    id                BIGINT      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    booking_id        BIGINT      NOT NULL REFERENCES bookings (id),
    journey_id        BIGINT      NOT NULL REFERENCES booking_journeys (id),
    status            VARCHAR(20) NOT NULL CHECK (status IN ('PENDING_PAYMENT', 'COMPLETED', 'EXPIRED', 'CANCELLED')),
    old_fare_total    BIGINT      NOT NULL CHECK (old_fare_total >= 0),
    new_fare_total    BIGINT      NOT NULL CHECK (new_fare_total >= 0),
    change_fee_total  BIGINT      NOT NULL CHECK (change_fee_total >= 0),
    amount_due        BIGINT      NOT NULL,
    hold_expires_at   TIMESTAMPTZ NOT NULL,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at      TIMESTAMPTZ,
    CHECK (amount_due = change_fee_total + GREATEST(new_fare_total - old_fare_total, 0))   -- BR-72
);
CREATE UNIQUE INDEX uq_reschedules_open_journey ON reschedules (journey_id) WHERE status = 'PENDING_PAYMENT';
CREATE INDEX ix_reschedules_hold    ON reschedules (hold_expires_at) WHERE status = 'PENDING_PAYMENT';
CREATE INDEX ix_reschedules_booking ON reschedules (booking_id);

ALTER TABLE booking_segments
    ADD CONSTRAINT fk_segments_reschedule FOREIGN KEY (reschedule_id) REFERENCES reschedules (id);

CREATE TABLE refund_requests (
    id             BIGINT      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    booking_id     BIGINT      NOT NULL REFERENCES bookings (id),
    journey_id     BIGINT      REFERENCES booking_journeys (id),    -- NULL khi source = INVALID_PAYMENT
    payment_id     BIGINT,                                          -- lượt thu cần hoàn khi source = INVALID_PAYMENT; FK thêm ở dưới
    source         VARCHAR(20) NOT NULL CHECK (source IN ('CUSTOMER', 'FLIGHT_CANCELLED', 'INVALID_PAYMENT')),
    status         VARCHAR(20) NOT NULL CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'COMPLETED')),
    base_amount    BIGINT      NOT NULL CHECK (base_amount >= 0),   -- giá trị chiều (BR-52) hoặc phần chưa hoàn của lượt thu
    fee_amount     BIGINT      NOT NULL CHECK (fee_amount >= 0),
    refund_amount  BIGINT      NOT NULL,
    reason         VARCHAR(500),                                    -- lý do của khách
    staff_note     VARCHAR(500),                                    -- ghi chú duyệt, lý do từ chối, ghi chú hoàn thủ công
    last_error     VARCHAR(500),                                    -- lỗi gần nhất khi gọi Refund API
    requested_by   BIGINT      REFERENCES users (id),               -- NULL khi hệ thống tạo
    processed_by   BIGINT      REFERENCES users (id),
    processed_at   TIMESTAMPTZ,
    completed_at   TIMESTAMPTZ,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (refund_amount = GREATEST(base_amount - fee_amount, 0)),   -- BR-61
    CHECK ((source = 'INVALID_PAYMENT') = (journey_id IS NULL)),
    CHECK ((source = 'INVALID_PAYMENT') = (payment_id IS NOT NULL)),
    CHECK (source = 'CUSTOMER' OR fee_amount = 0),                   -- BR-66
    CHECK (source = 'CUSTOMER' OR status <> 'REJECTED')              -- BR-66
);
CREATE UNIQUE INDEX uq_refund_open_journey ON refund_requests (journey_id) WHERE status IN ('PENDING', 'APPROVED');
CREATE INDEX ix_refund_status  ON refund_requests (status, created_at);
CREATE INDEX ix_refund_booking ON refund_requests (booking_id);

-- ------------------------------------------------------------ payment
CREATE TABLE payments (
    id                   BIGINT      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    booking_id           BIGINT      NOT NULL REFERENCES bookings (id),
    kind                 VARCHAR(10) NOT NULL CHECK (kind IN ('CHARGE', 'REFUND')),
    provider             VARCHAR(10) NOT NULL CHECK (provider IN ('VNPAY', 'MANUAL')),
    status               VARCHAR(10) NOT NULL CHECK (status IN ('PENDING', 'SUCCESS', 'FAILED')),
    amount               BIGINT      NOT NULL CHECK (amount > 0),
    reschedule_id        BIGINT      REFERENCES reschedules (id),      -- lượt thu của đổi chuyến
    refund_request_id    BIGINT      REFERENCES refund_requests (id),  -- lượt hoàn: thuộc yêu cầu hoàn nào
    original_payment_id  BIGINT      REFERENCES payments (id),         -- lượt hoàn: hoàn vào lượt thu nào
    txn_ref              VARCHAR(40) NOT NULL UNIQUE,   -- vnp_TxnRef (lượt thu) hoặc vnp_RequestId (lượt hoàn); UUID không gạch nối
    provider_txn_no      VARCHAR(30),                   -- vnp_TransactionNo
    provider_txn_date    VARCHAR(14),                   -- vnp_PayDate (yyyyMMddHHmmss, GMT+7), cần khi gọi Refund API
    response_code        VARCHAR(10),                   -- vnp_ResponseCode
    raw_response         JSONB,                         -- toàn bộ tham số/phản hồi từ VNPay, để đối soát
    expires_at           TIMESTAMPTZ,                   -- lượt thu: hạn của link thanh toán
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at         TIMESTAMPTZ,                   -- thời điểm chuyển SUCCESS/FAILED; dùng cho báo cáo dòng tiền
    CHECK ((kind = 'REFUND') = (original_payment_id IS NOT NULL)),
    CHECK ((kind = 'REFUND') = (refund_request_id IS NOT NULL)),
    CHECK (kind = 'CHARGE' OR reschedule_id IS NULL),
    CHECK (provider = 'VNPAY' OR kind = 'REFUND'),
    CHECK (kind = 'CHARGE' OR status <> 'PENDING'),
    CHECK ((status = 'PENDING') = (completed_at IS NULL))
);
CREATE INDEX ix_payments_booking        ON payments (booking_id);
CREATE INDEX ix_payments_original       ON payments (original_payment_id) WHERE original_payment_id IS NOT NULL;
CREATE INDEX ix_payments_refund_request ON payments (refund_request_id) WHERE refund_request_id IS NOT NULL;
CREATE INDEX ix_payments_completed      ON payments (completed_at) WHERE status = 'SUCCESS';

ALTER TABLE refund_requests
    ADD CONSTRAINT fk_refund_requests_payment FOREIGN KEY (payment_id) REFERENCES payments (id);
