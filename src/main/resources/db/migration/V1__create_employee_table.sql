CREATE TABLE employee (
    id             UUID PRIMARY KEY,
    first_name     VARCHAR(100) NOT NULL,
    last_name      VARCHAR(100) NOT NULL,
    date_of_birth  DATE NOT NULL,
    gender         VARCHAR(20) NOT NULL,
    ssn_ciphertext VARCHAR(255) NOT NULL,
    key_version    SMALLINT NOT NULL DEFAULT 1
);
