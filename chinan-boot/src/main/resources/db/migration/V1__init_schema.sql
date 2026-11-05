-- =====================================================
-- Chinan 数据库迁移脚本 V1: 初始化架构
-- 适用数据库: PostgreSQL / H2 (兼容模式)
-- =====================================================

-- 1. 学生表变更：去掉旧状态字段，新增预置状态日期范围
-- （JPA ddl-auto: update 会自动处理，此处作为参考）

-- 如果旧字段存在则跳过（JPA 会自动处理），这里只记录新增字段
-- ALTER TABLE student ADD COLUMN IF NOT EXISTS is_long_sick_leave BOOLEAN DEFAULT FALSE;
-- ALTER TABLE student ADD COLUMN IF NOT EXISTS military_start_date DATE;
-- ALTER TABLE student ADD COLUMN IF NOT EXISTS military_end_date DATE;
-- ALTER TABLE student ADD COLUMN IF NOT EXISTS intern_start_date DATE;
-- ALTER TABLE student ADD COLUMN IF NOT EXISTS intern_end_date DATE;
-- ALTER TABLE student ADD COLUMN IF NOT EXISTS day_student_start_date DATE;
-- ALTER TABLE student ADD COLUMN IF NOT EXISTS day_student_end_date DATE;

-- 2. 查寝记录表变更：新增预置状态字段
-- ALTER TABLE check_record ADD COLUMN IF NOT EXISTS preset_status VARCHAR(32);
-- ALTER TABLE check_record ADD COLUMN IF NOT EXISTS preset_overridden BOOLEAN DEFAULT FALSE;
-- ALTER TABLE check_record ADD COLUMN IF NOT EXISTS status_source VARCHAR(16);
-- -- status_source 取值：PRESET / MANUAL / DEFAULT

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