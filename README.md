# Eco Chart Pro

**Eco Chart Pro** is a professional, high-performance desktop charting and backtesting application built with Java Swing. It provides a feature-rich environment for financial market analysis, custom indicator development, and disciplined trade journaling.

![Eco Chart Pro Screenshot](./docs/images/screenshot.png) <!-- TODO: Replace this with an actual screenshot of your application! -->

---

## Core Features

-   **Multi-Panel Charting:** View multiple instruments or timeframes simultaneously with a flexible, user-configurable layout system.
-   **Advanced Replay Mode:** A powerful backtesting engine to replay historical market data bar-by-bar, allowing traders to test and refine strategies in a simulated environment.
-   **Full Paper Trading Suite:** A complete simulated trading service featuring order management (limit, stop, market), open position tracking, and detailed P&L calculation.
-   **Interactive Drawing Tools:** A full suite of tools for technical analysis, including trendlines, Fibonacci levels, rectangles, and measurement tools, with full undo/redo support.
-   **Custom Indicator Engine:** A robust plugin system that supports loading indicators from external JARs or live-compiling them from the powerful in-app Java editor with code completion.
-   **Gamification & Coaching:** An innovative system that tracks performance, offers unlockable achievements, and provides actionable coaching insights to help traders identify behavioral patterns and improve discipline.
-   **Modern Themed UI:** A clean, themeable user interface built on the FlatLaf look and feel, featuring a polished dashboard and non-intrusive notifications.

## Roadmap & Future Development

This project is under active development. The roadmap is divided into several key phases focused on architectural improvements, feature expansion, and usability enhancements.

### Phase 1: Core Architecture Refinement
-   **[In Progress] Polymorphic Drawing Tool Settings:** Refactor the drawing tool settings logic to use a polymorphic approach, removing `instanceof` checks and allowing each drawing tool to manage its own settings dialog. This will make the system more extensible and easier to maintain.
-   **[Planned] Info Cursor Tool:** Introduce a new cursor mode that displays an information panel with details about the candle, indicators, and drawings under the cursor.

### Phase 2: Advanced Analysis & Trading Features
-   **[Planned] P&L Calculation Service:** Refactor all unrealized P&L calculations into a dedicated, centralized service to ensure consistency and eliminate logic duplication across the UI.
-   **[Planned] Drawing Tool Templates:** Implement a system for users to create, save, and apply multiple named style templates for each drawing tool, allowing for rapid customization of charts.

### Phase 3: Live Data & Trading
-   **[Planned] Multi-Source Data Integration:** Expand data handling to support multiple live and historical data providers (e.g., Binance, OKX, Yahoo Finance), allowing users to select their preferred source.
-   **[Planned] Live Data Integration:** Connect the charting engine to a live WebSocket data feed for real-time market data.
-   **[Planned] Live Trading Integration:** Implement a `TradingService` for live brokerage APIs (e.g., Binance) to enable real-money trading directly from the platform.

### Phase 4: Usability & Polish
-   **[Planned] Right-Click Context Menus:** Provide faster access to common actions (e.g., delete drawing, modify order) via right-click menus on the chart.
-   **[Planned] Enhanced Chart Interaction:** Implement zoom-to-cursor and click-and-drag axis scaling for a more fluid and intuitive chart navigation experience.
-   **[Planned] Onboarding & Help System:** Add a "Help" menu and an "About" dialog with application information and links to documentation.

---

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
    git clone https://github.com/rphlmrk/EcoChartPro.git
    cd EcoChartPro
    ```
2.  Build and run the application using the included Gradle wrapper:
    ```bash
    # On Windows
    ./gradlew.bat run

    # On macOS / Linux
    ./gradlew run
    ```

## Support the Project ❤️

If you find Eco Chart Pro useful, please consider starring the repository. Your support helps motivate continued development and new features!

## Author

-   **Raphael Mark**

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.