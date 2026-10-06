CREATE DATABASE IF NOT EXISTS photo_system
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

USE photo_system;

SET FOREIGN_KEY_CHECKS = 0;

DROP TABLE IF EXISTS embedding_records;
DROP TABLE IF EXISTS benchmark_samples;
DROP TABLE IF EXISTS benchmark_runs;
DROP TABLE IF EXISTS photo_description_matches;
DROP TABLE IF EXISTS ai_task_descriptions;
DROP TABLE IF EXISTS ai_task_photos;
DROP TABLE IF EXISTS ai_tasks;
DROP TABLE IF EXISTS descriptions;
DROP TABLE IF EXISTS photos;
DROP TABLE IF EXISTS users;

SET FOREIGN_KEY_CHECKS = 1;

CREATE TABLE users (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  username VARCHAR(64) NOT NULL,
  password_hash VARCHAR(255) NOT NULL,
  phone_num VARCHAR(32) NULL,
  name VARCHAR(64) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_users_username (username),
  KEY idx_users_phone_num (phone_num)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE photos (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id BIGINT UNSIGNED NOT NULL,
  storage_path VARCHAR(1024) NOT NULL,
  access_url VARCHAR(1024) NULL,
  original_name VARCHAR(255) NOT NULL,
  stored_name VARCHAR(255) NOT NULL,
  content_hash CHAR(64) NULL,
  mime_type VARCHAR(128) NULL,
  size_bytes BIGINT UNSIGNED NULL,
  status ENUM('ACTIVE', 'DELETED') NOT NULL DEFAULT 'ACTIVE',
  deleted_at DATETIME NULL,
  delete_expire_at DATETIME NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_photos_stored_name (stored_name),
  KEY idx_photos_user_status_created (user_id, status, created_at),
  KEY idx_photos_user_delete_expire (user_id, status, delete_expire_at),
  KEY idx_photos_content_hash (content_hash),
  CONSTRAINT fk_photos_user
    FOREIGN KEY (user_id) REFERENCES users (id)
    ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE descriptions (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id BIGINT UNSIGNED NOT NULL,
  content TEXT NOT NULL,
  text_type ENUM('CATEGORY_LABEL', 'SEARCH_QUERY', 'DESCRIPTION') NOT NULL DEFAULT 'DESCRIPTION',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_descriptions_user_type_created (user_id, text_type, created_at),
  CONSTRAINT fk_descriptions_user
    FOREIGN KEY (user_id) REFERENCES users (id)
    ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE ai_tasks (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id BIGINT UNSIGNED NOT NULL,
  task_type ENUM('CATEGORY', 'TEXT_SEARCH', 'EMBEDDING_BACKFILL') NOT NULL,
  status ENUM('PENDING', 'RUNNING', 'SUCCESS', 'FAILED') NOT NULL DEFAULT 'PENDING',
  error_message TEXT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_ai_tasks_user_type_status_created (user_id, task_type, status, created_at),
  CONSTRAINT fk_ai_tasks_user
    FOREIGN KEY (user_id) REFERENCES users (id)
    ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE ai_task_photos (
  task_id BIGINT UNSIGNED NOT NULL,
  photo_id BIGINT UNSIGNED NOT NULL,
  PRIMARY KEY (task_id, photo_id),
  KEY idx_ai_task_photos_photo (photo_id),
  CONSTRAINT fk_ai_task_photos_task
    FOREIGN KEY (task_id) REFERENCES ai_tasks (id)
    ON DELETE CASCADE,
  CONSTRAINT fk_ai_task_photos_photo
    FOREIGN KEY (photo_id) REFERENCES photos (id)
    ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE ai_task_descriptions (
  task_id BIGINT UNSIGNED NOT NULL,
  description_id BIGINT UNSIGNED NOT NULL,
  PRIMARY KEY (task_id, description_id),
  KEY idx_ai_task_descriptions_description (description_id),
  CONSTRAINT fk_ai_task_descriptions_task
    FOREIGN KEY (task_id) REFERENCES ai_tasks (id)
    ON DELETE CASCADE,
  CONSTRAINT fk_ai_task_descriptions_description
    FOREIGN KEY (description_id) REFERENCES descriptions (id)
    ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE photo_description_matches (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  task_id BIGINT UNSIGNED NOT NULL,
  photo_id BIGINT UNSIGNED NOT NULL,
  description_id BIGINT UNSIGNED NOT NULL,
  match_type ENUM('CATEGORY', 'TEXT_SEARCH') NOT NULL,
  score DECIMAL(10, 8) NOT NULL,
  rank_no INT UNSIGNED NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_matches_task_photo_description (task_id, photo_id, description_id, match_type),
  KEY idx_matches_description_rank (description_id, match_type, rank_no),
  KEY idx_matches_photo (photo_id),
  CONSTRAINT fk_matches_task
    FOREIGN KEY (task_id) REFERENCES ai_tasks (id)
    ON DELETE CASCADE,
  CONSTRAINT fk_matches_photo
    FOREIGN KEY (photo_id) REFERENCES photos (id)
    ON DELETE CASCADE,
  CONSTRAINT fk_matches_description
    FOREIGN KEY (description_id) REFERENCES descriptions (id)
    ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE embedding_records (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id BIGINT UNSIGNED NOT NULL,
  target_type ENUM('PHOTO', 'DESCRIPTION') NOT NULL,
  target_id BIGINT UNSIGNED NOT NULL,
  embedding_model VARCHAR(128) NOT NULL,
  vector_db VARCHAR(64) NOT NULL,
  collection_name VARCHAR(128) NOT NULL,
  vector_id VARCHAR(255) NOT NULL,
  dim INT UNSIGNED NOT NULL,
  status ENUM('READY', 'STALE', 'FAILED') NOT NULL DEFAULT 'READY',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_embedding_target_model (target_type, target_id, embedding_model),
  UNIQUE KEY uk_embedding_vector (vector_db, collection_name, vector_id),
  KEY idx_embedding_user_target (user_id, target_type, target_id),
  CONSTRAINT fk_embedding_user
    FOREIGN KEY (user_id) REFERENCES users (id)
    ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE benchmark_runs (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  run_id VARCHAR(64) NOT NULL,
  status ENUM('PENDING', 'RUNNING', 'COMPLETED', 'FAILED', 'CANCELLED') NOT NULL,
  resolved_plan_json JSON NOT NULL,
  error_type VARCHAR(64) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  completed_at DATETIME NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_benchmark_runs_run_id (run_id),
  KEY idx_benchmark_runs_status_created (status, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE benchmark_samples (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  run_id VARCHAR(64) NOT NULL,
  sequence_no INT UNSIGNED NOT NULL,
  is_warmup BOOLEAN NOT NULL,
  total_ms DECIMAL(12, 3) NULL,
  success BOOLEAN NOT NULL,
  error_type VARCHAR(64) NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_benchmark_samples_run_sequence (run_id, sequence_no),
  CONSTRAINT fk_benchmark_samples_run
    FOREIGN KEY (run_id) REFERENCES benchmark_runs (run_id)
    ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
