CREATE TABLE IF NOT EXISTS comics (
    id SERIAL PRIMARY KEY,
    title TEXT NOT NULL,
    artist TEXT NOT NULL,
    author TEXT NOT NULL,
    isbn13 TEXT NOT NULL UNIQUE,
    isbn10 TEXT NOT NULL UNIQUE,
    publication_date DATE,
    publisher TEXT,
    price DECIMAL,
    page_count INTEGER,
    description TEXT,
    image_url TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_comics_isbn13 ON comics(isbn13);
CREATE INDEX idx_comics_isbn10 ON comics(isbn10);

CREATE TABLE IF NOT EXISTS publishers (
    id SERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL UNIQUE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS comics_publishers (
    comic_id INTEGER REFERENCES comics(id),
    publisher_id INTEGER REFERENCES publishers(id),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (comic_id, publisher_id)
);