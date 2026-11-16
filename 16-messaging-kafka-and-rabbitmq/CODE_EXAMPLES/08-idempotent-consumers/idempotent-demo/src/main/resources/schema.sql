CREATE TABLE account (
    id      VARCHAR(50) PRIMARY KEY,
    balance BIGINT NOT NULL
);

-- One row per event already applied. The primary key is what makes a second insert impossible.
CREATE TABLE processed_event (
    event_id VARCHAR(100) PRIMARY KEY
);

-- The same table without the constraint, for the check-then-insert race.
CREATE TABLE processed_event_no_key (
    event_id VARCHAR(100) NOT NULL
);

CREATE TABLE customer_email (
    customer VARCHAR(50) PRIMARY KEY,
    email    VARCHAR(200) NOT NULL
);
