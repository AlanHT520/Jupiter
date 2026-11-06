CREATE TABLE IF NOT EXISTS task_template (
    id VARCHAR(64) PRIMARY KEY,
    name VARCHAR(60) NOT NULL,
    items_json TEXT NOT NULL,
    item_count INTEGER,
    created_at TIMESTAMP
);
