-- Categories table — created first because products reference it
CREATE TABLE categories (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(100) NOT NULL UNIQUE,
    description VARCHAR(500)
);

-- Seed some default categories
INSERT INTO categories (name, description) VALUES
    ('Electronics', 'Phones, laptops, tablets, and accessories'),
    ('Clothing', 'Men and women apparel'),
    ('Books', 'Physical and digital books'),
    ('Home & Garden', 'Furniture, decor, and garden supplies'),
    ('Sports', 'Sports equipment and activewear');
