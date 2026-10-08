CREATE TABLE authentication_attempts (
    bucket_hash varchar(64) PRIMARY KEY,
    window_start timestamptz NOT NULL,
    attempts integer NOT NULL CHECK(attempts BETWEEN 1 AND 100000),
    expires_at timestamptz NOT NULL
);
CREATE INDEX authentication_attempts_expiry ON authentication_attempts(expires_at);
