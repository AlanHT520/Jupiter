-- =====================================================
-- Chinan 数据库迁移脚本 V4: 性能索引
-- 适用数据库: PostgreSQL / H2
-- =====================================================

-- 1. check_record 查询索引
CREATE INDEX IF NOT EXISTS idx_check_record_task ON check_record(task_id);
CREATE INDEX IF NOT EXISTS idx_check_record_task_room ON check_record(task_id, room_id);
CREATE INDEX IF NOT EXISTS idx_check_record_task_student ON check_record(task_id, student_id);

-- 2. check_record_modify_log 查询索引
CREATE INDEX IF NOT EXISTS idx_modify_log_modified_at ON check_record_modify_log(modified_at);