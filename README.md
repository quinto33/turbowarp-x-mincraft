# Advanced Minecraft-to-Scratch Bridge (Fabric 1.21.1)
An open-source Fabric mod and Scratch 3.0 extension that creates a real-time, bi-directional network bridge between Minecraft 1.21.1 and the Scratch/TurboWarp programming environments.By embedding a lightweight HTTP server directly inside the Minecraft client lifecycle, this project allows custom block layouts to communicate across local networks via standard TCP/IP routing.🚀 Core Architectural FeaturesBi-Directional Event Synchronization: Bridges game-state updates (like user authentication logs or specific block breaks) back to Scratch’s event engine tracking hooks.Embedded High-Performance HTTP Server: Employs a low-latency thread framework running natively inside Minecraft to listen on port 8080 without interrupting rendering processes.Dynamic Network Handshakes: Includes active connection states feeding directly into Scratch's pointy-edged Boolean blocks (is game online?) for fallback execution monitoring.Remote In-Game Command Execution: Allows immediate translation of Scratch string packets into complex native Minecraft console configurations like /summon or /say.🛠️ Repository System SpecificationsTarget Game Platform: Minecraft Java Edition 1.21.1Development Framework: Fabric Loader 0.16.9 / Fabric API 0.103.0Compilation Environment: Java Development Kit (JDK) 21 / Gradle 8.xExtension Compatibility: Vanilla Scratch 3.0 Custom Environments & TurboWarp Web Application Workers
## 🚀 How to Install the Extension in TurboWarp

Because this extension requires network permissions to speak to your Minecraft server, it must be loaded using TurboWarp's **Unsandboxed** mode. Follow these steps to load it instantly from the cloud:

1. Copy this direct raw extension link:
   `https://githubusercontent.com`
2. Open the [TurboWarp Web Editor](https://turbowarp.org).
3. Click the blue **Add Extension** button in the bottom-left corner of the editor canvas.
4. Scroll down to the very bottom of the extension library page and click on **Custom Extension**.
5. **CRUCIAL STEP:** Look for the checkbox that says **"Run extension without sandbox"** (or **"Unsandboxed"**) and make sure it is **checked (turned ON)**. If left unchecked, your browser will block the network bridge.
6. Paste the copied link into the text input field box.
7. Click the **Load** button!

Your custom category section will instantly populate your editor sidebar palette with your customized **cyan, green, and orange blocks**!
