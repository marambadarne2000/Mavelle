CREATE TABLE order_mail_jobs (
 order_id bigint PRIMARY KEY REFERENCES orders(id),
 recipient varchar(254) NOT NULL,
 status varchar(16) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING','RUNNING','COMPLETED','FAILED')),
 delivery_mode varchar(16) CHECK (delivery_mode IN ('file','smtp')),
 attempts integer NOT NULL DEFAULT 0 CHECK (attempts BETWEEN 0 AND 3),
 next_attempt timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
 claimed_at timestamptz,
 completed_at timestamptz,
 created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX order_mail_pending ON order_mail_jobs(next_attempt) WHERE status='PENDING';
