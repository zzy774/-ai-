# 📝 实验报告，交给我来写

> 一个让 AI 替你熬夜写实验报告的全栈 Web 应用。**上传模板 + 代码 → 一杯咖啡的时间 → Word 报告到手。**

<p align="center">
  <img src="https://img.shields.io/badge/Java-17-blue?logo=openjdk" />
  <img src="https://img.shields.io/badge/Spring%20Boot-3.2.5-green?logo=springboot" />
  <img src="https://img.shields.io/badge/Vue-3.4-4FC08D?logo=vue.js" />
  <img src="https://img.shields.io/badge/AI-DeepSeek%20|%20Qwen-purple" />
  <img src="https://img.shields.io/badge/license-MIT-green" />
</p>

---

## 🤔 这玩意儿是干嘛的

你是不是经历过这些：

- 😩 实验做完了，报告一个字都不想写
- 📄 老师给了 DOCX 模板，格式复杂到令人窒息
- 🖼 代码截图、UML 图、实验数据，排版排到怀疑人生
- 📚 同一门课要做 N 个实验报告，写到吐

**这个项目就是来解决这些问题的。** 你只需要做三件事：

1. **上传你的 DOCX 模板**
2. **把整个代码文件夹拖进来**
3. **点一下生成按钮**

剩下的交给 AI——它按你模板的格式写稿、引用你代码里的真实逻辑、还能帮你画 UML 图。不满意？点个「👁 AI 预览」先看稿，觉得 OK 再下载。

---

## 🎬 怎么跑起来

你的电脑需要：

| 软件 | 干嘛用 |
|------|--------|
| JDK 17+ | 运行后端 |
| Maven 3.9+ | 构建后端 |
| Node.js 18+ | 运行前端 |
| MySQL | 存数据 |
| Redis | 缓存和登录管理 |
| 一个 AI API Key | DeepSeek（便宜大碗）或通义千问都行 |

```bash
# 1. 初始化数据库
mysql -u root -p < backend/src/main/resources/db/migration/V1__init_schema.sql

# 2. 启动后端
cd backend
mvn spring-boot:run

# 3. 启动前端
cd frontend
npm install && npm run dev

# 4. 打开 http://localhost:5173
# 默认账号 admin / admin123
# 进去后先去「系统设置」填 AI API Key
```

---

## 🧠 AI 怎么帮你写报告

```
你丢进来                          AI 干活                              输出
┌──────────────┐        ┌──────────────────────────────┐        ┌──────────────┐
│ 📄 DOCX 模板 │        │ ① 解析模板的章节结构           │        │ 📄 完整 Word │
│ 💻 代码文件夹 │ ────→ │ ② 读取代码，理解真实逻辑        │ ────→  │   实验报告    │
│ 📋 参考样例   │       │ ③ 逐个章节按模板格式撰写内容    │        │              │
│ (可选)        │       │ ④ 只改文字，格式百分百保留      │        │ 模板格式完美  │
└──────────────┘        │ ⑤ UML 图自动嵌入（PlantUML）   │        │ AI 内容充实  │
                        └──────────────────────────────┘        └──────────────┘
```

核心保证：

- ✅ **格式 100% 保留**——改的是文字，不是字体/字号/行间距，模板啥样出来就啥样
- ✅ **内容基于你的代码**——AI 引用的类名、方法名都是你真实上传的，不编造
- ✅ **不瞎编**——没传代码就不写代码分析，不伪造实验数据
- ✅ **支持 .doc**——老版 Word 文件自动转 .docx（需本机装 Word）

---

## 📂 上传区说明

进入项目工作区，有 4 张卡片：

| 卡片 | 上传啥 | 说明 |
|------|--------|------|
| 📄 **DOCX 模板** | 老师给的实验报告模板 | 你的格式怎么定，AI 就怎么写 |
| 💻 **代码文件** | 整个代码文件夹 | 支持 Java / Python / C++ / ArkTS 等 |
| 🖼 **截图/图片** | 可选：实验截图 | 后续版本支持自动嵌入报告 |
| 📋 **参考样例** | 可选：学长学姐的报告 | AI 参考其风格和深度，不照抄 |

> 💡 模板里用 `（此处填写）` 或 `...（略）` 标记要填的位置，AI 会自动精确定位。

---

## 🏗️ 技术栈

| 层 | 技术 |
|----|------|
| 前端 | Vue 3 + Element Plus + Pinia + Axios + ECharts + Vite |
| 后端 | Spring Boot 3 + Spring Security + JWT + MyBatis-Plus |
| AI | DeepSeek / 通义千问（统一接口，工厂模式切换） |
| 文档 | python-docx + poi-tl + PlantUML |
| 存储 | MySQL + Redis |

---

## 📁 项目结构

```
lab-report-writer-app/
├── backend/                          # ☕ Spring Boot 后端
│   ├── src/main/java/com/labreport/server/
│   │   ├── ai/         # AI 供应商接口 + DeepSeek / Qwen 实现
│   │   ├── config/     # Security / CORS / Async / MyBatisPlus
│   │   ├── controller/ # Auth / Project / Report / File / Uml / Ai
│   │   ├── engine/     # CodeAnalyzer / UmlGenerator / TemplateEngine
│   │   ├── model/      # Entity / DTO / Mapper
│   │   ├── security/   # JWT + Spring Security
│   │   ├── service/    # ReportGenerator / AiService / FileStorage
│   │   └── common/     # Result<T> / GlobalExceptionHandler
│   └── scripts/
│       └── fill_report_template.py   # ★ 核心：DOCX 精确定位填充
│
├── frontend/                         # 🎨 Vue 3 前端
│   └── src/
│       ├── views/     # Login / Project / UML / AiChat / Settings
│       ├── stores/    # Pinia 状态管理
│       ├── api/       # Axios 封装
│       └── components/ # 布局 / 卡片 / 编辑器
│
└── .gitignore           # 保护 API Key、上传文件、生成报告等隐私
```

---

## ⚠️ 友情提醒

- AI 生成的报告建议自己过一遍——格式对了不代表内容完全没问题
- 生成的报告文档在lab-report-writer-app\backend\outputs里面
- 记得更改API Key，在lab-report-writer-app\backend\src\main\resources的application.yml文件中的67行和70行，如果有Geminmi的也可以加
- 代码文件建议不超过 100 个，否则 prompt 太长 AI 会被截断
- 本工具是**辅助写作**，最终提交前请确认符合课程学术规范

---

## 📄 License

MIT — 学校里随便用，不用给我打钱。

<p align="center">
  <b>⭐ 觉得有用就点个 Star，让更多同学少熬夜写报告</b>
</p>
