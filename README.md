# KitchenMind

A smart kitchen inventory management mobile application, designed to help users track their food items and minimize food waste. This repository contains the **Draft Version** of the app. 

### Educational Purpose
This project is developed as part of **Homework 2: Website Development & AI Agent Planning**. While the assignment title mentions website development, the draft implementation provided here is an **Android Mobile App** using modern Jetpack Compose.

## Project Overview

*   **Topic:** Smart Kitchen Inventory & Food Waste Reduction
*   **Target Users:** Individuals and families looking for meal inspiration based on existing ingredients.
*   **Technologies Used:**
    *   **Language:** Kotlin
    *   **UI Toolkit:** Jetpack Compose (Material Design 3)
    *   **Architecture:** MVI (Model-View-Intent)
    *   **Local Database:** Room Database & KSP
    *   **Asynchrony:** Kotlin Coroutines & Flow
    *   **Navigation:** Jetpack Navigation Compose

## Setup and Run Instructions

### Prerequisites
*   Android Studio (Iguana 2023.2.1 or newer recommended)
*   Java Development Kit (JDK) 17
*   An Android device or emulator running API Level 24 (Android 7.0) or higher.

### Steps to Run
1.  **Clone the repository:**
    ```bash
    git clone https://github.com/YOUR_USERNAME/KitchenMind.git
    cd KitchenMind
    ```
2.  **Open in Android Studio:**
    *   Launch Android Studio.
    *   Select **File -> Open** and navigate to the cloned `KitchenMind` directory.
    *   Wait for Gradle to sync dependencies.
3.  **Build and Run:**
    *   Select a virtual or physical device from the Run configurations dropdown.
    *   Click the **Run 'app'** button (green play icon on the toolbar) or press `Shift + F10`.

## AI Agent Planning

This project is built with the explicit intention of integrating a Multi-Agent Artificial Intelligence System (such as CrewAI or LangGraph) in future iterations. 

The application architecture utilizes **MVI (Model-View-Intent)**, which provides a highly decoupled and state-driven environment perfectly suited for background AI interactions. 

For the complete strategic plan on how the AI Agent will be integrated, what problem it solves, and the high-level system architecture, please read the included planning document:

*   📄 **[AI Agent Planning Document (Markdown)](./AI_Agent_Planning_Document.md)**

---
*Created for the Website Development & AI Agent Planning assignment. March 2026.*
