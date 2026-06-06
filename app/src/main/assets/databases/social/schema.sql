CREATE DATABASE IF NOT EXISTS social;
USE social;

CREATE TABLE users (
    user_id INT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(80) NOT NULL UNIQUE,
    full_name VARCHAR(120) NOT NULL,
    email VARCHAR(150) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    bio TEXT,
    profile_pic VARCHAR(255),
    location VARCHAR(100),
    website VARCHAR(255),
    is_verified BOOLEAN DEFAULT FALSE,
    joined_at DATETIME NOT NULL,
    last_login DATETIME
);
CREATE INDEX idx_users_joined_at ON users (joined_at);

CREATE TABLE posts (
    post_id INT AUTO_INCREMENT PRIMARY KEY,
    user_id INT NOT NULL,
    content TEXT NOT NULL,
    image_url VARCHAR(500),
    location_tag VARCHAR(100),
    created_at DATETIME NOT NULL,
    likes_count INT NOT NULL DEFAULT 0,
    comments_count INT NOT NULL DEFAULT 0,
    FOREIGN KEY (user_id) REFERENCES users(user_id),
    CHECK (likes_count >= 0),
    CHECK (comments_count >= 0)
);
CREATE INDEX idx_posts_user ON posts (user_id);
CREATE INDEX idx_posts_created_at ON posts (created_at);
CREATE INDEX idx_posts_likes_count ON posts (likes_count);

CREATE TABLE comments (
    comment_id INT AUTO_INCREMENT PRIMARY KEY,
    post_id INT NOT NULL,
    user_id INT NOT NULL,
    comment_text TEXT NOT NULL,
    commented_at DATETIME NOT NULL,
    FOREIGN KEY (post_id) REFERENCES posts(post_id),
    FOREIGN KEY (user_id) REFERENCES users(user_id)
);
CREATE INDEX idx_comments_post ON comments (post_id);
CREATE INDEX idx_comments_user ON comments (user_id);
CREATE INDEX idx_comments_date ON comments (commented_at);

CREATE TABLE likes (
    like_id INT AUTO_INCREMENT PRIMARY KEY,
    post_id INT NOT NULL,
    user_id INT NOT NULL,
    liked_at DATETIME NOT NULL,
    FOREIGN KEY (post_id) REFERENCES posts(post_id),
    FOREIGN KEY (user_id) REFERENCES users(user_id),
    UNIQUE KEY ux_likes_post_user (post_id, user_id)
);
CREATE INDEX idx_likes_post ON likes (post_id);
CREATE INDEX idx_likes_user ON likes (user_id);

CREATE TABLE followers (
    follower_id INT AUTO_INCREMENT PRIMARY KEY,
    user_id INT NOT NULL,
    follower_user_id INT NOT NULL,
    followed_at DATETIME NOT NULL,
    FOREIGN KEY (user_id) REFERENCES users(user_id),
    FOREIGN KEY (follower_user_id) REFERENCES users(user_id),
    UNIQUE KEY ux_followers_user_pair (user_id, follower_user_id)
);
CREATE INDEX idx_followers_user ON followers (user_id);
CREATE INDEX idx_followers_follower ON followers (follower_user_id);

CREATE TABLE messages (
    message_id INT AUTO_INCREMENT PRIMARY KEY,
    sender_id INT NOT NULL,
    recipient_id INT NOT NULL,
    message_text TEXT NOT NULL,
    is_read BOOLEAN DEFAULT FALSE,
    sent_at DATETIME NOT NULL,
    read_at DATETIME,
    FOREIGN KEY (sender_id) REFERENCES users(user_id),
    FOREIGN KEY (recipient_id) REFERENCES users(user_id)
);
CREATE INDEX idx_messages_sender ON messages (sender_id);
CREATE INDEX idx_messages_recipient ON messages (recipient_id);
CREATE INDEX idx_messages_sent_at ON messages (sent_at);

CREATE TABLE hashtags (
    hashtag_id INT AUTO_INCREMENT PRIMARY KEY,
    tag VARCHAR(80) NOT NULL UNIQUE,
    post_count INT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL
);
CREATE INDEX idx_hashtags_tag ON hashtags (tag);

CREATE TABLE post_tags (
    post_tag_id INT AUTO_INCREMENT PRIMARY KEY,
    post_id INT NOT NULL,
    hashtag_id INT NOT NULL,
    FOREIGN KEY (post_id) REFERENCES posts(post_id),
    FOREIGN KEY (hashtag_id) REFERENCES hashtags(hashtag_id)
);
CREATE INDEX idx_post_tags_post ON post_tags (post_id);
CREATE INDEX idx_post_tags_hashtag ON post_tags (hashtag_id);

CREATE TABLE stories (
    story_id INT AUTO_INCREMENT PRIMARY KEY,
    user_id INT NOT NULL,
    media_url VARCHAR(500) NOT NULL,
    created_at DATETIME NOT NULL,
    expires_at DATETIME NOT NULL,
    FOREIGN KEY (user_id) REFERENCES users(user_id)
);
CREATE INDEX idx_stories_user ON stories (user_id);
CREATE INDEX idx_stories_expires ON stories (expires_at);

CREATE TABLE notifications (
    notification_id INT AUTO_INCREMENT PRIMARY KEY,
    user_id INT NOT NULL,
    from_user_id INT NOT NULL,
    type ENUM('like','comment','follow','message') NOT NULL,
    post_id INT,
    is_read BOOLEAN DEFAULT FALSE,
    created_at DATETIME NOT NULL,
    FOREIGN KEY (user_id) REFERENCES users(user_id),
    FOREIGN KEY (from_user_id) REFERENCES users(user_id)
);
CREATE INDEX idx_notifications_user ON notifications (user_id);
CREATE INDEX idx_notifications_read ON notifications (user_id, is_read);