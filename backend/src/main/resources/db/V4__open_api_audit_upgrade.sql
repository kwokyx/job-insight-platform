CREATE TABLE IF NOT EXISTS sys_api_call_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    api_key_id BIGINT NOT NULL,
    endpoint VARCHAR(255) NOT NULL,
    method VARCHAR(16) NOT NULL,
    request_params VARCHAR(500),
    response_code INT NOT NULL,
    response_time BIGINT NOT NULL,
    ip_address VARCHAR(64),
    user_agent VARCHAR(255),
    request_id VARCHAR(64),
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_api_call_log_api_key_time (api_key_id, created_at),
    INDEX idx_api_call_log_request_id (request_id),
    INDEX idx_api_call_log_endpoint_time (endpoint, created_at)
);

ALTER TABLE sys_api_call_log
    ADD COLUMN IF NOT EXISTS request_id VARCHAR(64) NULL AFTER user_agent;

ALTER TABLE sys_api_call_log
    MODIFY COLUMN request_params VARCHAR(500) NULL;

SET @request_id_index_sql = (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.statistics
            WHERE table_schema = DATABASE()
              AND table_name = 'sys_api_call_log'
              AND index_name = 'idx_api_call_log_request_id'
        ),
        'SELECT 1',
        'CREATE INDEX idx_api_call_log_request_id ON sys_api_call_log (request_id)'
    )
);
PREPARE request_id_index_stmt FROM @request_id_index_sql;
EXECUTE request_id_index_stmt;
DEALLOCATE PREPARE request_id_index_stmt;

SET @endpoint_time_index_sql = (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.statistics
            WHERE table_schema = DATABASE()
              AND table_name = 'sys_api_call_log'
              AND index_name = 'idx_api_call_log_endpoint_time'
        ),
        'SELECT 1',
        'CREATE INDEX idx_api_call_log_endpoint_time ON sys_api_call_log (endpoint, created_at)'
    )
);
PREPARE endpoint_time_index_stmt FROM @endpoint_time_index_sql;
EXECUTE endpoint_time_index_stmt;
DEALLOCATE PREPARE endpoint_time_index_stmt;
