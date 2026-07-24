# Askvocate

Askvocate is an Android application designed to provide specialized assistance and booking services.

## 🚀 Getting Started

Follow these instructions to get a copy of the project up and running on your local machine for development and testing.

### Prerequisites

- **Android Studio Ladybug (2024.2.1)** or newer.
- **JDK 17** or newer.
- **Git** installed on your system.
- An Android device or Emulator running **API 28 (Android 9.0)** or higher.

### Installation & Setup

1. **Clone the repository:**
   ```bash
   git clone https://github.com/sudipkunduChrist/Askvocate.git
   ```

2. **Open the project:**
   - Launch Android Studio.
   - Select **Open** and navigate to the cloned folder.
   - Wait for the Gradle sync to complete.

3. **Check Branch:**
   - Ensure you are working on the `develop` branch for integration or create a new feature branch.
   ```bash
   git checkout develop
   ```

## 🌿 Branching Strategy

To keep our development organized, we follow this branching model:

| Branch | Purpose |
| :--- | :--- |
| `main` | Production-ready stable code. |
| `develop` | Integration branch for ongoing development. |
| `feature-ai` | Development of AI-related features. |
| `feature-auth` | Development of Authentication and User Management. |
| `feature-booking` | Development of Booking and Scheduling features. |
| `feature-ui` | UI/UX improvements and Design System. |

> [!TIP]
> Always create your feature branches from `develop` and merge them back to `develop` via Pull Request.

## 🏗️ Project Structure

- `:app`: The main Android application module.
  - `src/main/java`: Kotlin source code.
  - `src/main/res`: Layouts, drawables, and UI resources.
  - `build.gradle.kts`: Module-level dependencies and configuration.

## 🛠️ Running the App

1. Connect your Android device via USB or start an Emulator.
2. Select the `app` configuration in the toolbar.
3. Click the **Run** button (Green Play icon).

## 🤝 Contributing

1. Create a new branch for your task: `git checkout -b feature-your-feature-name`.
2. Commit your changes: `git commit -m "Add some feature"`.
3. Push to the branch: `git push origin feature-your-feature-name`.
4. Open a Pull Request on GitHub.

---
© 2026 Askvocate Team
