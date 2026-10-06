CREATE TABLE benchmark_runs (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  run_id VARCHAR(64) NOT NULL,
  status ENUM('PENDING','RUNNING','COMPLETED','FAILED','CANCELLED') NOT NULL,
  resolved_plan_json JSON NOT NULL,
  error_type VARCHAR(64) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  completed_at DATETIME NULL,
  PRIMARY KEY (id), UNIQUE KEY uk_benchmark_runs_run_id (run_id), KEY idx_benchmark_runs_status_created (status, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE benchmark_samples (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  run_id VARCHAR(64) NOT NULL,
  sequence_no INT UNSIGNED NOT NULL,
  is_warmup BOOLEAN NOT NULL,
  total_ms DECIMAL(12,3) NULL,
  success BOOLEAN NOT NULL,
  error_type VARCHAR(64) NULL,
  PRIMARY KEY (id), UNIQUE KEY uk_benchmark_samples_run_sequence (run_id, sequence_no),
  CONSTRAINT fk_benchmark_samples_run FOREIGN KEY (run_id) REFERENCES benchmark_runs(run_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
