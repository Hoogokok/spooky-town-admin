-- 만화 테이블
CREATE TABLE IF NOT EXISTS comics (
    id SERIAL PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    artist VARCHAR(255),
    author VARCHAR(255),
    isbn13 VARCHAR(13) UNIQUE NOT NULL,
    isbn10 VARCHAR(10) UNIQUE,
    price INTEGER,
    image_url TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 출판사 테이블
CREATE TABLE IF NOT EXISTS publishers (
    id SERIAL PRIMARY KEY,
    name VARCHAR(255) UNIQUE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 만화-출판사 연결 테이블
CREATE TABLE IF NOT EXISTS comics_publishers (
    comic_id INTEGER REFERENCES comics(id) ON DELETE CASCADE,
    publisher_id INTEGER REFERENCES publishers(id) ON DELETE CASCADE,
    PRIMARY KEY (comic_id, publisher_id)
); 

CREATE TABLE authors (
    id SERIAL PRIMARY KEY,
    name VARCHAR(50) NOT NULL,
    type VARCHAR(10) NOT NULL CHECK (type IN ('writer', 'artist')),
    description TEXT
);

CREATE INDEX idx_authors_name ON authors (name);

CREATE TABLE comics_authors (
    comic_id INTEGER REFERENCES comics(id) ON DELETE CASCADE,
    author_id INTEGER REFERENCES authors(id) ON DELETE CASCADE,
    role VARCHAR(10) NOT NULL CHECK (role IN ('writer', 'artist')),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (comic_id, author_id, role)
);  