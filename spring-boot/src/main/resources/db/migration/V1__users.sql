-- USRSEC VSAM record (CSUSR01Y.cpy) as a relational table.
CREATE TABLE users (
    user_id    varchar(8)  NOT NULL PRIMARY KEY,
    first_name varchar(20) NOT NULL,
    last_name  varchar(20) NOT NULL,
    password   varchar(8)  NOT NULL,
    user_type  varchar(1)  NOT NULL CHECK (user_type IN ('A', 'U'))
);
