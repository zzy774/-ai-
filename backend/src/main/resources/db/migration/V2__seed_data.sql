-- V2__seed_data.sql
-- 预置管理员账户和系统配置

USE labreport;

INSERT INTO `user` (username, password, display_name, role) VALUES
('admin', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', '管理员', 'ADMIN')
ON DUPLICATE KEY UPDATE username=username;
-- 密码: admin123

INSERT INTO `system_config` (config_key, config_value, description) VALUES
('ai.default_provider', 'GEMINI', '默认AI供应商'),
('ai.gemini.api_key', '', 'Gemini API Key（从 https://aistudio.google.com/apikey 获取）'),
('ai.gemini.model', 'gemini-2.0-flash', 'Gemini模型名称'),
('ai.deepseek.api_key', '', 'DeepSeek API Key（从 https://platform.deepseek.com 获取）'),
('ai.deepseek.model', 'deepseek-chat', 'DeepSeek模型名称'),
('ai.qwen.api_key', '', '千问 API Key（从 https://dashscope.aliyun.com 获取）'),
('ai.qwen.model', 'qwen-turbo', '千问模型名称'),
('storage.upload_dir', './uploads', '文件上传存储目录'),
('storage.report_output_dir', './outputs', '报告输出目录')
ON DUPLICATE KEY UPDATE config_key=config_key;
