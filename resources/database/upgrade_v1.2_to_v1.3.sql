CREATE TABLE IF NOT EXISTS t_knowledge_ops_run (
    id VARCHAR(64) PRIMARY KEY,
    user_id VARCHAR(64),
    kb_id VARCHAR(64) NOT NULL,
    task TEXT NOT NULL,
    status VARCHAR(32) NOT NULL,
    report_json JSONB,
    summary TEXT,
    error_message TEXT,
    started_at TIMESTAMP,
    finished_at TIMESTAMP,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS t_knowledge_ops_step (
    id VARCHAR(64) PRIMARY KEY,
    run_id VARCHAR(64) NOT NULL,
    step_order INT NOT NULL,
    step_type VARCHAR(64) NOT NULL,
    tool_name VARCHAR(128),
    status VARCHAR(32) NOT NULL,
    input_json JSONB,
    output_json JSONB,
    error_message TEXT,
    started_at TIMESTAMP,
    finished_at TIMESTAMP,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_knowledge_ops_run_kb_id ON t_knowledge_ops_run (kb_id);
CREATE INDEX IF NOT EXISTS idx_knowledge_ops_run_status ON t_knowledge_ops_run (status);
CREATE INDEX IF NOT EXISTS idx_knowledge_ops_step_run_id ON t_knowledge_ops_step (run_id);
