CREATE TABLE IF NOT EXISTS schema_migrations (version varchar(255) NOT NULL, applied_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP, description text, PRIMARY KEY (version));
