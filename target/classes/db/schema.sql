CREATE TABLE IF NOT EXISTS users (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  username VARCHAR(64) NOT NULL UNIQUE,
  email VARCHAR(255) UNIQUE,
  password_hash VARCHAR(255) NOT NULL,
  display_name VARCHAR(128) NOT NULL,
  avatar_url VARCHAR(255),
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS friends (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  friend_id BIGINT NOT NULL,
  remark VARCHAR(128),
  status VARCHAR(16) NOT NULL DEFAULT 'ACCEPTED',
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_f_user FOREIGN KEY (user_id) REFERENCES users(id),
  CONSTRAINT fk_f_friend FOREIGN KEY (friend_id) REFERENCES users(id),
  UNIQUE KEY uk_user_friend (user_id, friend_id),
  INDEX idx_f_user_status (user_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS friend_request_logs (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  target_id BIGINT NOT NULL,
  action VARCHAR(32) NOT NULL,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_fr_log_user_time (user_id, created_at),
  INDEX idx_fr_log_target_time (target_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `groups` (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  name VARCHAR(128) NOT NULL,
  owner_id BIGINT NOT NULL,
  CONSTRAINT fk_g_owner FOREIGN KEY (owner_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS group_members (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  group_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  CONSTRAINT fk_gm_group FOREIGN KEY (group_id) REFERENCES `groups`(id),
  CONSTRAINT fk_gm_user FOREIGN KEY (user_id) REFERENCES users(id),
  UNIQUE KEY uk_group_user (group_id, user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS messages (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  conversation_id BIGINT,
  group_id BIGINT,
  sender_id BIGINT NOT NULL,
  receiver_id BIGINT,
  content TEXT NOT NULL,
  content_type VARCHAR(32) NOT NULL DEFAULT 'text',
  is_read TINYINT(1) NOT NULL DEFAULT 0,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_conv_created (conversation_id, created_at),
  INDEX idx_group_created (group_id, created_at),
  INDEX idx_receiver_read (receiver_id, is_read)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS error_logs (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT,
  username VARCHAR(64),
  error_type VARCHAR(32) NOT NULL,
  severity VARCHAR(16) NOT NULL,
  message TEXT NOT NULL,
  stack TEXT,
  url VARCHAR(255),
  component VARCHAR(128),
  module VARCHAR(128),
  route VARCHAR(255),
  user_agent VARCHAR(255),
  browser VARCHAR(64),
  os VARCHAR(64),
  version VARCHAR(64),
  resource_url VARCHAR(255),
  request_method VARCHAR(16),
  status_code INT,
  extra TEXT,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_error_type_time (error_type, created_at),
  INDEX idx_error_user_time (user_id, created_at),
  INDEX idx_error_version (version),
  INDEX idx_error_browser (browser),
  INDEX idx_error_os (os)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS avatar_history (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  avatar_url VARCHAR(255) NOT NULL,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_ah_user FOREIGN KEY (user_id) REFERENCES users(id),
  INDEX idx_ah_user_time (user_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
