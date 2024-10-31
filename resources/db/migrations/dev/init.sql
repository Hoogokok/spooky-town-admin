CREATE TABLE IF NOT EXISTS schema_migrations (
    id SERIAL PRIMARY KEY,
    version varchar(255) UNIQUE NOT NULL,
    applied timestamp with time zone DEFAULT current_timestamp
); 