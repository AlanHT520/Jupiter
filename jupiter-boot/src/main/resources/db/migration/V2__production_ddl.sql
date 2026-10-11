-- =====================================================
-- Jupiter 数据库迁移脚本 V2: 生产环境 DDL
-- 适用数据库: PostgreSQL
-- 用于在已有数据库上执行变更
-- =====================================================

-- 1. 学生表：新增预置状态和日期范围字段
ALTER TABLE student ADD COLUMN IF NOT EXISTS is_long_sick_leave BOOLEAN DEFAULT FALSE;
ALTER TABLE student ADD COLUMN IF NOT EXISTS military_start_date DATE;
ALTER TABLE student ADD COLUMN IF NOT EXISTS military_end_date DATE;
ALTER TABLE student ADD COLUMN IF NOT EXISTS intern_start_date DATE;
ALTER TABLE student ADD COLUMN IF NOT EXISTS intern_end_date DATE;
ALTER TABLE student ADD COLUMN IF NOT EXISTS day_student_start_date DATE;
ALTER TABLE student ADD COLUMN IF NOT EXISTS day_student_end_date DATE;

-- 2. 查寝记录表：新增预置状态和状态来源字段
ALTER TABLE check_record ADD COLUMN IF NOT EXISTS preset_status VARCHAR(32);
ALTER TABLE check_record ADD COLUMN IF NOT EXISTS preset_overridden BOOLEAN DEFAULT FALSE;
ALTER TABLE check_record ADD COLUMN IF NOT EXISTS status_source VARCHAR(16);

-- 3. 新增查寝记录修改日志表
CREATE TABLE IF NOT EXISTS check_record_modify_log (
    id VARCHAR(255) PRIMARY KEY,
    check_record_id VARCHAR(255) NOT NULL,
    field_name VARCHAR(64) NOT NULL,
    old_value VARCHAR(512),
    new_value VARCHAR(512),
    modified_by VARCHAR(255),
    modified_at TIMESTAMP,
    reason VARCHAR(512),
    created_at TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_modify_log_record_id ON check_record_modify_log(check_record_id);