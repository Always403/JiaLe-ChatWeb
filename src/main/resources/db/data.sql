INSERT IGNORE INTO users(username, password_hash, display_name) VALUES
('1234567890', '$2a$10$2qM6eZJwQGkqYqF4QyWwEeg8Tq6oG3kzV3m1YwQwZQw0zYxWk9Xmu', 'Alice'),
('9876543210',   '$2a$10$2qM6eZJwQGkqYqF4QyWwEeg8Tq6oG3kzV3m1YwQwZQw0zYxWk9Xmu', 'Bob');

-- Initialize Public Channel (Group ID 1)
INSERT IGNORE INTO `groups` (id, name, owner_id) VALUES (1, 'Public Channel', 1);

INSERT IGNORE INTO friends(user_id, friend_id, remark, status)
SELECT u1.id, u2.id, 'Bob', 'ACCEPTED' FROM users u1, users u2 WHERE u1.username='1234567890' AND u2.username='9876543210';

INSERT IGNORE INTO friends(user_id, friend_id, remark, status)
SELECT u1.id, u2.id, 'Alice', 'ACCEPTED' FROM users u1, users u2 WHERE u1.username='9876543210' AND u2.username='1234567890';
