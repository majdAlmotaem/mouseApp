# mouseApp - Lazy Controller

Control your PC mouse, keyboard, and media using your Android device over a network connection.

## 📋 Overview

mouseApp is a remote control application that allows you to use your Android smartphone as a wireless mouse, keyboard, and media controller for your PC. It consists of two main components:

- **Android App**: Client application for sending commands
- **PC Server**: Server application that receives and executes commands on your PC

## 🎯 Features

- **Mouse Control**: Move cursor and click
- **Keyboard Navigation**: Arrow keys and other key presses
- **Scroll Control**: Scroll up and down on web pages and applications
- **Volume Control**: Adjust PC volume up and down
- **System Control**: Remote PC shutdown capability
- **Network Communication**: TCP socket-based communication between Android and PC
- **GUI Server**: User-friendly GUI for starting/stopping the server and monitoring connections

## 📁 Project Structure

```
mouseApp/
├── AndriodApp/              # Android application (Kotlin)
│   ├── app/
│   │   ├── src/
│   │   │   ├── main/       # Main Android app source
│   │   │   ├── test/       # Unit tests
│   │   │   └── androidTest/ # Android instrumentation tests
│   │   └── build.gradle.kts # Android build configuration
│   ├── gradle/             # Gradle wrapper and configuration
│   └── settings.gradle.kts
│
└── PcServerApp/            # Python PC server application
    ├── gui_mouse_server.py # GUI application for server control
    ├── pc_mouse_server.py  # Core server logic
    ├── LazyController.spec # PyInstaller specification
    ├── build_app.py        # Build script
    └── requirements.txt    # Python dependencies
```

## 🔧 Requirements

### PC Server

- Windows OS (with Python 3.7+)
- Python dependencies:
  - `pyautogui` - For mouse and keyboard automation
  - `pycaw` - For volume control
  - `pyinstaller` - For building standalone executable

Install dependencies:

```bash
pip install -r requirements.txt
```

### Android App

- Android 10 (API 29) or higher
- Kotlin support
- Network connectivity (same network as PC)

## 🚀 Quick Start

### PC Server Setup

1. Navigate to the `PcServerApp` directory
2. Install Python dependencies:
   ```bash
   pip install -r requirements.txt
   ```
3. Run the GUI server:
   ```bash
   python gui_mouse_server.py
   ```
4. Click "Start Server" to begin listening for Android connections on port 9999

### Android App Setup

1. Build and install the Android app on your device
2. Configure the PC's IP address in the app settings
3. Press connect to establish connection with the server
4. Use the app interface to control your PC

## 🎮 Supported Commands

| Command          | Action                       |
| ---------------- | ---------------------------- |
| `MOUSE_MOVE x y` | Move cursor by (x, y) pixels |
| `LEFT_CLICK`     | Perform left mouse click     |
| `ARROW_LEFT`     | Press left arrow key         |
| `ARROW_RIGHT`    | Press right arrow key        |
| `SCROLL_UP`      | Scroll up                    |
| `SCROLL_DOWN`    | Scroll down                  |
| `VOLUME_UP`      | Increase system volume by 2% |
| `VOLUME_DOWN`    | Decrease system volume by 2% |
| `SHUTDOWN`       | Shutdown the PC              |

## 🔌 Network Communication

- **Protocol**: TCP Socket
- **Port**: 9999
- **Host**: 0.0.0.0 (listens on all available interfaces)
- **Data Format**: UTF-8 encoded text commands

## 📝 Configuration

The PC server is configured with the following defaults:

- Host: `0.0.0.0` (accepts connections from any IP)
- Port: `9999`
- Volume adjustment step: 2% per command
- Mouse movement: Instant (no acceleration)

## 🛠️ Building

### PC Server Executable

To build a standalone Windows executable:

```bash
python build_app.py
```

This will create a `.exe` file using PyInstaller for distribution without requiring Python installation.

### Android App

Build the APK using Android Studio or Gradle:

```bash
cd AndriodApp
./gradlew assembleRelease
```

## 📄 License

See [LICENSE](LICENSE) file for details.

## 🤝 Contributing

Contributions are welcome! Please feel free to submit issues or pull requests.

## 📞 Support

For issues or questions, please open an issue in the repository.

---

**Note**: This application requires both devices to be on the same network. Ensure your firewall allows connections on port 9999.
