# LiteTask (轻任务)

中文 | [English](README.en.md)

## 项目简介

一款轻量化的任务/日程可视化提醒程序，基于语音识别与大模型分析简化日程创建流程；轻任务 (LiteTask) ；

## 核心设计理念

### Material Design 3 规范
- 遵循 Google Material Design 3 规范
- 采用大圆角、动态取色、模态浮层（Bottom Sheet）和优雅的微交互动效

### 色彩逻辑
- 以分类（Work/Life/Study）定义主题色
- 以紧迫度（Urgent）定义高亮警示，视觉层级分明

## 核心功能亮点

### 极速 AI 语音录入
- **交互流程**：点击麦克风 -> 说话 -> AI 分析 -> 生成结构化卡片
- **核心能力**：自动提取标题、起止时间、截止日期

### 三大核心视图
1. **列表视图 (Timeline)**
   - 日常概览
   - 左侧彩色竖条区分分类，卡片内直观展示"下一步行动"

2. **甘特图视图 (Gantt Chart)**
   - 时间跨度与忙闲管理
   - 可视化的时间条块

3. **截止日视图 (Deadline Focus)**
   - 营造紧迫感，治疗拖延症

### 任务与子任务体系
- **父任务 (Goal)**：承载 DDL 和时间段
- **子任务 (Action)**：承载具体的执行步骤（Checklist）
- **联动机制**：子任务的勾选实时驱动父任务的进度条增长，形成从"规划"到"落地"的完整闭环

## 技术架构

### 前端技术栈
- Android Native (Kotlin) + Jetpack Compose

### 数据层
- **Room Database**：Task (主表), SubTask (子表), Reminder (提醒表)
- **EncryptedSharedPreferences**：安全存储 API Key

### AI 集成
- 集成 LLM API (DeepSeek) 进行意图识别与 JSON 格式化
- 支持多种 AI 提供商，通过适配器模式实现灵活扩展

## 项目结构

```
app/src/main/java/com/litetask/app/
├── data/
│   ├── ai/              # AI 相关功能
│   ├── local/           # 本地数据访问层
│   ├── model/           # 数据模型
│   └── repository/      # 数据仓库
├── di/                  # 依赖注入模块
├── ui/
│   ├── components/      # 可复用 UI 组件
│   ├── home/            # 主页界面
│   ├── settings/        # 设置界面
│   └── theme/           # 主题样式
└── util/                # 工具类
```

## 特色功能

### 语音识别优化
- 实时语音识别与录音同步进行
- 多种错误处理机制，提供友好的用户提示
- 支持识别结果编辑和确认

### 智能任务分析
- 基于 AI 的自然语言任务解析
- 自动识别任务标题、时间、类型等属性
- 支持批量任务创建

### 灵活的任务管理
- 多种任务类型（工作、生活、紧急、学习等）
- 置顶重要任务
- 子任务分解与进度追踪

### 可视化时间管理
- Timeline 视图直观展示任务时间线
- Gantt 视图全局把控时间安排
- Deadline 视图突出紧迫任务

## 开发环境

- Android Studio Flamingo 或更高版本
- Kotlin 1.9+
- Gradle 8.0+
- JDK 17+

## 第三方库

- Jetpack Compose - 现代 Android UI 工具包
- Hilt - 依赖注入框架
- Room - SQLite 数据库抽象层
- Retrofit - 网络请求库
- OkHttp - HTTP 客户端
- Gson - JSON 解析库
- Kotlin Coroutines - 异步编程库

## 许可证

本项目采用 MIT 许可证 - 查看 [LICENSE](LICENSE) 文件了解更多详情