-- =====================================================
-- V3: 查宿者账户改为 xlsx 导入，新增学生关联字段
-- 适用数据库: PostgreSQL
-- =====================================================

-- counselor 表新增字段
ALTER TABLE counselor ADD COLUMN IF NOT EXISTS student_no VARCHAR(64);
ALTER TABLE counselor ADD COLUMN IF NOT EXISTS class_name VARCHAR(128);
ALTER TABLE counselor ADD COLUMN IF NOT EXISTS gender VARCHAR(16);

-- 学号唯一索引
CREATE UNIQUE INDEX IF NOT EXISTS idx_counselor_student_no ON counselor(student_no);

-- 废弃 employee_no 列（保留但不使用，避免数据丢失）
-- ALTER TABLE counselor DROP COLUMN IF EXISTS employee_no;