# Eco Chart Pro

**Eco Chart Pro** is a professional, high-performance desktop charting and backtesting application built with Java Swing. It provides a feature-rich environment for financial market analysis, custom indicator development, and disciplined trade journaling.

![Eco Chart Pro Screenshot](./docs/images/screenshot.png)

---

## Core Features

-   **Multi-Panel Charting:** View multiple instruments or timeframes simultaneously with a flexible, user-configurable layout system.
-   **Advanced Replay Mode:** A powerful backtesting engine to replay historical market data bar-by-bar, allowing traders to test and refine strategies in a simulated environment.
-   **Full Paper Trading Suite:** A complete simulated trading service featuring order management (limit, stop, market), open position tracking, and detailed P&L calculation.
-   **Interactive Drawing Tools:** A full suite of tools for technical analysis, including trendlines, Fibonacci levels, rectangles, and measurement tools, with full undo/redo support.
-   **Custom Indicator Engine:** A robust plugin system that supports loading indicators from external JARs or live-compiling them from the powerful in-app Java editor with code completion.
-   **Gamification & Coaching:** An innovative system that tracks performance, offers unlockable achievements, and provides actionable coaching insights to help traders identify behavioral patterns and improve discipline.
-   **Modern Themed UI:** A clean, themeable user interface built on the FlatLaf look and feel, featuring a polished dashboard and non-intrusive notifications.

## Technology Stack

-   **Language:** Java 21
-   **UI Framework:** Java Swing
-   **Look and Feel:** [FlatLaf](https://www.formdev.com/flatlaf/)
-   **Build System:** Gradle
-   **Core Libraries:**
    -   **SLF4J & Logback:** For robust logging.
    -   **Jackson:** For high-performance JSON serialization (session saving/loading).
    -   **SQLite-JDBC:** For local database storage of market data.
    -   **ClassGraph:** For fast, reliable plugin and classpath scanning.
    -   **RSyntaxTextArea:** For the feature-rich in-app Java code editor.

## Getting Started

### Prerequisites

-   Java Development Kit (JDK) 21 or later.
-   Git for cloning the repository.

### How to Run

1.  Clone the repository to your local machine:
    ```bash
    git clone https://github.com/YourUsername/EcoChartPro.git
    cd EcoChartPro
    ```
2.  Build and run the application using the included Gradle wrapper:
    ```bash
    # On Windows
    ./gradlew.bat run

    # On macOS / Linux
    ./gradlew run
    ```

### Building an Executable

You can build a standalone "fat JAR" or a native installer for your operating system.

-   **To build the JAR:**
    ```bash
    ./gradlew shadowJar
    ```
    The executable JAR will be located in `build/libs/`.

-   **To build a native installer (.exe, .pkg, .deb):**
    ```bash
    ./gradlew jpackage
    ```
    The installer will be located in `build/jpackage/`.

## Author

-   **Raphael Mark**

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.