-- Payment Service Database Schema
-- 이 스크립트는 컨테이너 시작 시 자동으로 실행됩니다

-- Payments 테이블
CREATE TABLE IF NOT EXISTS payments (
    id BIGSERIAL PRIMARY KEY,
    payment_number VARCHAR(255) NOT NULL UNIQUE,
    order_id BIGINT NOT NULL,
    order_number VARCHAR(255) NOT NULL,
    amount NUMERIC(10, 2) NOT NULL,
    customer_name VARCHAR(255) NOT NULL,
    customer_email VARCHAR(255) NOT NULL,
    status VARCHAR(50) NOT NULL,
    payment_method VARCHAR(50),
    transaction_id VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Processed Events 테이블 (Idempotency Pattern)
CREATE TABLE IF NOT EXISTS processed_events (
    id BIGSERIAL PRIMARY KEY,
    event_id VARCHAR(255) NOT NULL UNIQUE,
    event_type VARCHAR(100) NOT NULL,
    payload TEXT NOT NULL,
    processed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    result VARCHAR(50) NOT NULL,
    error_message TEXT
);

-- 인덱스 생성
CREATE INDEX IF NOT EXISTS idx_payments_order_number ON payments(order_number);
CREATE INDEX IF NOT EXISTS idx_payments_status ON payments(status);
CREATE INDEX IF NOT EXISTS idx_payments_created_at ON payments(created_at DESC);
CREATE UNIQUE INDEX IF NOT EXISTS idx_processed_events_event_id ON processed_events(event_id);

-- 샘플 데이터 (선택사항)
-- INSERT INTO payments (payment_number, order_id, order_number, amount, customer_name, customer_email, status, payment_method)
-- VALUES ('PAY-SAMPLE01', 1, 'ORD-SAMPLE01', 10000.00, '테스트 고객', 'test@example.com', 'COMPLETED', 'CARD')
-- ON CONFLICT (payment_number) DO NOTHING;

-- 트리거: updated_at 자동 업데이트
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ language 'plpgsql';

CREATE TRIGGER update_payments_updated_at BEFORE UPDATE ON payments
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
