CREATE DATABASE IF NOT EXISTS ecommerce;
USE ecommerce;

CREATE TABLE users (
    user_id INT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    email VARCHAR(150) NOT NULL UNIQUE,
    registered_at DATETIME NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'active',
    CHECK (status IN ('active','inactive','suspended'))
);
CREATE INDEX idx_users_registered_at ON users (registered_at);
CREATE INDEX idx_users_status ON users (status);

CREATE TABLE categories (
    category_id INT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(80) NOT NULL UNIQUE,
    description TEXT
);

CREATE TABLE products (
    product_id INT AUTO_INCREMENT PRIMARY KEY,
    category_id INT NOT NULL,
    name VARCHAR(120) NOT NULL,
    price DECIMAL(10,2) NOT NULL,
    stock INT NOT NULL DEFAULT 0,
    sku VARCHAR(50) UNIQUE,
    created_at DATETIME NOT NULL,
    FOREIGN KEY (category_id) REFERENCES categories(category_id),
    CHECK (price >= 0),
    CHECK (stock >= 0)
);
CREATE INDEX idx_products_category ON products (category_id);
CREATE INDEX idx_products_price ON products (price);
CREATE INDEX idx_products_created_at ON products (created_at);

CREATE TABLE orders (
    order_id INT AUTO_INCREMENT PRIMARY KEY,
    user_id INT NOT NULL,
    order_date DATETIME NOT NULL,
    status VARCHAR(30) NOT NULL,
    total_amount DECIMAL(10,2) NOT NULL,
    shipping_address VARCHAR(250),
    FOREIGN KEY (user_id) REFERENCES users(user_id),
    CHECK (total_amount >= 0)
);
CREATE INDEX idx_orders_user_date ON orders (user_id, order_date);
CREATE INDEX idx_orders_status ON orders (status);

CREATE TABLE order_items (
    order_item_id INT AUTO_INCREMENT PRIMARY KEY,
    order_id INT NOT NULL,
    product_id INT NOT NULL,
    quantity INT NOT NULL,
    unit_price DECIMAL(10,2) NOT NULL,
    FOREIGN KEY (order_id) REFERENCES orders(order_id),
    FOREIGN KEY (product_id) REFERENCES products(product_id),
    CHECK (quantity > 0),
    CHECK (unit_price >= 0)
);
CREATE INDEX idx_order_items_order ON order_items (order_id);
CREATE INDEX idx_order_items_product ON order_items (product_id);

CREATE TABLE payments (
    payment_id INT AUTO_INCREMENT PRIMARY KEY,
    order_id INT NOT NULL,
    payment_date DATETIME NOT NULL,
    amount DECIMAL(10,2) NOT NULL,
    method VARCHAR(40) NOT NULL,
    status VARCHAR(40) NOT NULL,
    FOREIGN KEY (order_id) REFERENCES orders(order_id),
    CHECK (amount >= 0)
);
CREATE INDEX idx_payments_order ON payments (order_id);
CREATE INDEX idx_payments_status ON payments (status);
CREATE INDEX idx_payments_date ON payments (payment_date);

CREATE TABLE inventory (
    inventory_id INT AUTO_INCREMENT PRIMARY KEY,
    product_id INT NOT NULL,
    warehouse VARCHAR(100) NOT NULL,
    quantity INT NOT NULL,
    last_updated DATETIME NOT NULL,
    FOREIGN KEY (product_id) REFERENCES products(product_id),
    CHECK (quantity >= 0)
);
CREATE INDEX idx_inventory_product ON inventory (product_id);
CREATE INDEX idx_inventory_warehouse ON inventory (warehouse);

CREATE TABLE reviews (
    review_id INT AUTO_INCREMENT PRIMARY KEY,
    product_id INT NOT NULL,
    user_id INT NOT NULL,
    rating TINYINT NOT NULL CHECK (rating BETWEEN 1 AND 5),
    comment TEXT,
    review_date DATETIME NOT NULL,
    FOREIGN KEY (product_id) REFERENCES products(product_id),
    FOREIGN KEY (user_id) REFERENCES users(user_id)
);
CREATE INDEX idx_reviews_product ON reviews (product_id);
CREATE INDEX idx_reviews_user ON reviews (user_id);
CREATE INDEX idx_reviews_rating ON reviews (rating);

CREATE TABLE user_addresses (
    address_id INT AUTO_INCREMENT PRIMARY KEY,
    user_id INT NOT NULL,
    label VARCHAR(50),
    street VARCHAR(150) NOT NULL,
    city VARCHAR(80) NOT NULL,
    state VARCHAR(80) NOT NULL,
    zip_code VARCHAR(20) NOT NULL,
    country VARCHAR(80) NOT NULL,
    FOREIGN KEY (user_id) REFERENCES users(user_id)
);
CREATE INDEX idx_user_addresses_user ON user_addresses (user_id);
CREATE INDEX idx_user_addresses_city ON user_addresses (city);

CREATE TABLE coupons (
    coupon_id INT AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(40) NOT NULL UNIQUE,
    discount_type VARCHAR(20) NOT NULL,
    discount_value DECIMAL(10,2) NOT NULL,
    valid_from DATETIME NOT NULL,
    valid_to DATETIME NOT NULL,
    min_purchase_amount DECIMAL(10,2) DEFAULT 0,
    status VARCHAR(20) NOT NULL DEFAULT 'active',
    CHECK (discount_value >= 0),
    CHECK (discount_type IN ('percentage','fixed'))
);
CREATE INDEX idx_coupons_status ON coupons (status);
CREATE INDEX idx_coupons_valid_to ON coupons (valid_to);

CREATE TABLE returns (
    return_id INT AUTO_INCREMENT PRIMARY KEY,
    order_item_id INT NOT NULL,
    return_reason VARCHAR(255) NOT NULL,
    return_date DATETIME NOT NULL,
    refund_amount DECIMAL(10,2) NOT NULL,
    status VARCHAR(30) NOT NULL,
    FOREIGN KEY (order_item_id) REFERENCES order_items(order_item_id),
    CHECK (refund_amount >= 0)
);
CREATE INDEX idx_returns_order_item ON returns (order_item_id);
CREATE INDEX idx_returns_status ON returns (status);
