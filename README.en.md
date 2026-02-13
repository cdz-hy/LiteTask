# LiteTask

<div align="center">

A lightweight task management app that simplifies the schedule creation process based on speech recognition and large language model analysis.

[中文](README.md) | [English](README.en.md)

[![Release](https://img.shields.io/github/v/release/cdz-hy/LiteTask)](https://github.com/cdz-hy/LiteTask/releases)[![License](https://img.shields.io/github/license/cdz-hy/LiteTask)](LICENSE)[![Android](https://img.shields.io/badge/Android-26%2B-brightgreen)](https://developer.android.com)

</div>

## Screenshots

<div align="center">

| Main UI | Gantt Chart View | Deadline View |
| :---: | :---: | :---: |
| <img src="https://github.com/user-attachments/assets/5460e2da-4e0c-498d-9c39-f869d52e67b8" width="200"> | <img src="https://github.com/user-attachments/assets/14e65e12-f036-4533-a25f-73ba837c33db" width="200"> | <img src="https://github.com/user-attachments/assets/b535e79c-a435-48b3-8302-f9520bd7f061" width="200"> |


| AI Voice Input (Flow) | Task Details | Home Widgets (Styles) |
| :---: | :---: | :---: |
| <img src="https://github.com/user-attachments/assets/9f27162a-867e-4665-af95-5757aa6e1a1c" width="145"> <img src="https://github.com/user-attachments/assets/490b4c97-49a0-4df1-a618-3f641518b4e2" width="145"> <img src="https://github.com/user-attachments/assets/7695337d-5d24-49b3-af42-11024a6b4040" width="145"> | <img src="https://github.com/user-attachments/assets/346ec807-4071-4329-a9fc-4e7075af4298" width="160"> | <img src="https://github.com/user-attachments/assets/8025f157-527f-4273-8f59-1399445e8bfb" width="100"><br><br><img src="https://github.com/user-attachments/assets/a45c887f-ecb7-4a71-8dad-30078a282760" width="100"><br><br><img src="https://github.com/user-attachments/assets/981309ef-f6b9-4945-bf9d-aebf465d5be3" width="100"> |

</div>

## Key Features

### AI Intelligent Entry
- Tap and speak or type text, AI automatically parses task information
- Supports batch task creation and natural language recognition
- Real-time speech recognition with editable confirmation before submission

### AI Subtask Decomposition
- Breakdown complex goals into several concrete, actionable subtasks with AI assistance
- Support for additional instructions to guide the decomposition towards specific priorities or directions
- Ability to modify and reorder the generated subtask analysis results

### Multi-Dimensional Visualization
- **Timeline View**: Daily task overview with color-coded categories
- **Gantt Chart View**: Visualize time spans and track overall progress
- **Deadline View**: Focus on urgent tasks, grouped by urgency level

### Task Management System
- Parent tasks define goals and time periods; subtasks break down specific execution steps
- Real-time progress feedback via progress bars
- Support for pinning, categorization, and reminders

### Custom Categories & Colors
- Built-in Work, Life, Study, and Urgent categories
- Support for custom category names and colors (HEX)

### Hard Task Reminders
- Custom reminder times: at task start, n hours/days before deadline, etc.
- Full-screen reminder popup (visible on lock screen), configurable sound and vibration

### Home Screen Widgets
- Task List Widget: Quick view of upcoming to-dos
- Gantt Chart Widget: Time schedule at a glance
- Deadline Widget: Home screen reminders for urgent tasks

### Data Backup & Restore
- Full export to JSON file (tasks, subtasks, categories, reminders, components, etc.)
- Auto-detect duplicate tasks on import (title + time + type fingerprint)
- Smart category merging for seamless cross-device migration

## Download & Installation

Go to the [Releases](https://github.com/cdz-hy/LiteTask/releases) page to download the latest APK.

**System Requirements**: Android 8.0 (API 26) and above.

## Technical Implementation

### Architecture Design
- **UI Layer**: Jetpack Compose + Material Design 3
- **Data Layer**: Room Database + Repository Pattern
- **Dependency Injection**: Hilt
- **Asynchronous Handling**: Kotlin Coroutines + Flow

### Core Tech Stack
```
Kotlin 1.9+
Jetpack Compose - Declarative UI
Room - Local data persistence
Hilt - Dependency injection
Retrofit + OkHttp - Networking
EncryptedSharedPreferences - Secure storage
```

### AI Integration
- Supports multiple LLM providers like DeepSeek, using an adapter pattern for flexible extension
- Parses natural language into structured task data
- Keeps track of AI processing history
- **Note**: AI features require you to configure your own API Key (e.g., DeepSeek) in the app's "Settings" screen.

### Data Models
```kotlin
Task (Primary Table)
├── id, title, description, startTime, deadline
├── isDone, isPinned, isExpired, categoryId
└── createdAt, completedAt, expiredAt

SubTask (Subtasks Table)         Category (Category Table)
├── taskId (FK)                  ├── id, name, colorHex
├── content, isCompleted         └── iconName, isDefault
└── sortOrder

Reminder (Reminder Table)        TaskComponent (Component Table)
├── taskId (FK)                  ├── taskId (FK), componentType
├── triggerAt, label             └── dataPayload (JSON), createdAt
└── isFired

AIHistory (AI History Table)
├── content, sourceType (VOICE/TEXT/SUBTASK)
└── parsedCount, isSuccess, timestamp
```

## Project Structure

```
app/src/main/java/com/litetask/app/
├── data/
│   ├── ai/              # AI provider adapters
│   ├── local/           # Room DAO & Database
│   ├── model/           # Data models
│   ├── remote/          # Network API
│   ├── repository/      # Data repositories
│   └── speech/          # Speech recognition
├── di/                  # Hilt dependency injection
├── reminder/            # Reminder scheduling & notifications
├── ui/
│   ├── backup/          # Data backup & restore
│   ├── components/      # Reusable components
│   ├── home/            # Home (Timeline/Gantt/Deadline)
│   ├── search/          # Search screen
│   ├── settings/        # Settings screen
│   └── theme/           # Material 3 theme
├── util/                # Utilities
└── widget/              # Home screen widgets (List/Gantt/Deadline)
```

## Development Environment

- Android Studio Hedgehog or higher
- Kotlin 1.9+
- Gradle 8.0+
- JDK 17+
- Android SDK 26+

## Building the Project

```bash
# Clone the repository
git clone https://github.com/cdz-hy/LiteTask.git

# Open the project
# Open the root directory in Android Studio

# Build and run
# Click the Run button or use the command line
./gradlew assembleDebug

# Configure API Key
# After installing the app, configure your DeepSeek API Key in Settings to enable AI features
```

## Main Dependencies

| Library | Version | Purpose |
|---|---|---|
| Jetpack Compose | 1.5+ | UI Framework |
| Room | 2.6+ | Database |
| Hilt | 2.48+ | Dependency Injection |
| Retrofit | 2.9+ | Networking |
| OkHttp | 4.12+ | HTTP Client |
| Kotlin Coroutines | 1.7+ | Async Programming |
| Gson | 2.10+ | JSON Serialization |

## Design Philosophy

### Material Design 3
- Adheres to Google Material Design 3 specifications
- Large rounded corners, dynamic color extraction, modal sheets
- Smooth micro-interaction animations

## Contributing

Issues and Pull Requests are welcome at [Issues](https://github.com/cdz-hy/LiteTask/issues).

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.
