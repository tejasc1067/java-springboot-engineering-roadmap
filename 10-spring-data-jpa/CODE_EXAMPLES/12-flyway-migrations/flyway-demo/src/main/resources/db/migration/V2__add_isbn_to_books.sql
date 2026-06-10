ALTER TABLE books ADD COLUMN isbn VARCHAR(13);

CREATE UNIQUE INDEX ux_books_isbn ON books (isbn);
