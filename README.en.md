# LiteTask

[中文](README.md) | English

## Project Overview

A lightweight task/schedule visualization and reminder application that simplifies schedule creation through voice recognition and AI analysis.

## Core Design Philosophy

### Material Design 3 Guidelines
- Follows Google Material Design 3 specifications
- Features large rounded corners, dynamic color theming, modal bottom sheets, and elegant micro-interactions

### Color Logic
- Categories (Work/Life/Study) define theme colors
- Urgency levels define highlight alerts with clear visual hierarchy

## Key Features

### Ultra-Fast AI Voice Input
- **Interaction Flow**: Tap microphone -> Speak -> AI analyzes -> Generate structured card
- **Core Capability**: Automatically extracts title, start/end time, and deadline

### Three Core Views
1. **Timeline View**
   - Daily overview
   - Left-side colored bars distinguish categories, cards display "next action" intuitively

2. **Gantt Chart View**
   - Time span and workload management
   - Visualized time blocks

3. **Deadline Focus View**
   - Creates urgency to combat procrastination

### Task and Subtask System
- **Parent Task (Goal)**: Carries deadline and time period
- **Subtask (Action)**: Carries specific execution steps (Checklist)
- **Linkage Mechanism**: Subtask completion drives parent task progress bar in real-time, forming a complete loop from "planning" to "execution"

## Technical Architecture

### Frontend Stack
- Android Native (Kotlin) + Jetpack Compose

### Data Layer
- **Room Database**: Task (main table), SubTask (subtable), Reminder (reminder table)
- **EncryptedSharedPreferences**: Secure API Key storage

### AI Integration
- Integrates LLM API (DeepSeek) for intent recognition and JSON formatting
- Supports multiple AI providers through adapter pattern for flexible extension

## Project Structure

```
app/src/main/java/com/litetask/app/
├── data/
│   ├── ai/              # AI-related functionality
│   ├── local/           # Local data access layer
│   ├── model/           # Data models
│   └── repository/      # Data repositories
├── di/                  # Dependency injection modules
├── ui/
│   ├── components/      # Reusable UI components
│   ├── home/            # Home screen
│   ├── settings/        # Settings screen
│   └── theme/           # Theme styles
└── util/                # Utility classes
```

## Special Features

### Voice Recognition Optimization
- Real-time speech recognition synchronized with recording
- Multiple error handling mechanisms with user-friendly prompts
- Support for editing and confirming recognition results

### Intelligent Task Analysis
- AI-based natural language task parsing
- Automatic identification of task title, time, type, and other attributes
- Support for batch task creation

### Flexible Task Management
- Multiple task types (Work, Life, Urgent, Study, etc.)
- Pin important tasks
- Subtask breakdown and progress tracking

### Visual Time Management
- Timeline view for intuitive task timeline display
- Gantt view for global time arrangement control
- Deadline view highlighting urgent tasks

## Development Environment

- Android Studio Flamingo or higher
- Kotlin 1.9+
- Gradle 8.0+
- JDK 17+

## Third-Party Libraries

- Jetpack Compose - Modern Android UI toolkit
- Hilt - Dependency injection framework
- Room - SQLite database abstraction layer
- Retrofit - Network request library
- OkHttp - HTTP client
- Gson - JSON parsing library
- Kotlin Coroutines - Asynchronous programming library

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details
