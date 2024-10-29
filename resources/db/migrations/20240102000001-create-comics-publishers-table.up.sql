CREATE TABLE IF NOT EXISTS comics_publishers (
    comic_id INTEGER REFERENCES comics(id),
    publisher_id INTEGER REFERENCES publishers(id),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (comic_id, publisher_id)
) 