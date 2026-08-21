# 🧠 Synapse AI-AntiCheat v3.0.0

<p align="center">
  <img src="https://img.shields.io/badge/Minecraft-1.16.5--1.20.4+-brightgreen?style=for-the-badge&logo=minecraft" alt="Minecraft Version">
  <img src="https://img.shields.io/badge/Platform-Spigot%20%7C%20Paper%20%7C%20Purpur-orange?style=for-the-badge" alt="Platforms">
  <img src="https://img.shields.io/badge/Engine-ONNX%20Runtime%20C%2B%2B-blue?style=for-the-badge&logo=c%2B%2B" alt="Engine">
  <img src="https://img.shields.io/badge/Author-%D0%94%D0%B0%D0%BD%D1%8F%20(smyzik%20%2F%20smerchhh)-purple?style=for-the-badge" alt="Author">
  <img src="https://img.shields.io/badge/Discord-dsc.gg%2Fsynapselabs-5865F2?style=for-the-badge&logo=discord" alt="Discord">
</p>

---

## ⚡ Overview

**Synapse AI-AntiCheat** is a next-generation Minecraft combat anticheat designed for Spigot, Paper, and Purpur servers. 

Unlike traditional anticheats that rely on rigid thresholds, Synapse AI utilizes a high-performance **ONNX Runtime (Machine Learning)** engine trained on **890,000+ combat records** to analyze 16 kinematic trajectory features per attack in under **0.3 milliseconds** in RAM.

---

## 🚀 Key Features

* **🧠 Machine Learning Combat Detection:**
  * 🎯 **KillAura & RageAura** (instant multi-entity locks & snaps)
  * 🎯 **Silent Aura** (attacking entities outside field of view)
  * 🎯 **Reach & Hitbox Expander** (unnatural distance checks)
  * 🎯 **AimAssist & Smooth Aimbot** (unnatural micro-corrections)

* **🛡️ GrimAC Hybrid Integration:**
  * Connects to GrimAC's 1:1 packet simulation physics for full Movement (Fly, Speed, NoSlow) + Combat coverage.

* **❄️ Investigation & Freeze System (`/aiac freeze`):**
  * Automatic teleportation to World Spawn or custom check rooms.
  * Private Interrogation Chat between suspect and moderator.
  * Automatic celebration screen and teleport back to spawn upon passing the check.
  * Automatic ban protection on disconnect.

* **🔨 Multi-Ban Plugin Integration:**
  * Native hooks for **LiteBans, AdvancedBan, LibertyBans, EssentialsX, and Bukkit**.

* **🌐 Discord Webhook Alerts:**
  * Dispatches rich embeds with 3D player skin avatars, confidence metrics, and cheat details directly to your staff Discord channel.

* **🖥️ Interactive 54-Slot Admin GUI (`/aiac menu`):**
  * Real-time Ping analyzer, IP alt correlation, VL resets, and Spectator mode.

---

## 📋 Commands & Permissions

| Command | Description | Permission |
| :--- | :--- | :--- |
| `/aiac help` | Display interactive help menu | `aianticheat.admin` |
| `/aiac menu` | Open Admin GUI dashboard | `aianticheat.admin` |
| `/aiac inspect <player>` | Open player dossier (IP, alts, bans, VL, ping) | `aianticheat.admin` |
| `/aiac ping <player>` | Analyze network latency and stability | `aianticheat.admin` |
| `/aiac freeze <player>` | Freeze and teleport player for investigation | `aianticheat.freeze` |
| `/aiac unfreeze <player>` | Unfreeze player and teleport to spawn | `aianticheat.freeze` |
| `/aiac chat <message>` | Send message to private check chat | `aianticheat.freeze` |
| `/aiac setfreezeloc` | Save current position as freeze room | `aianticheat.admin` |
| `/aiac check <player>` | Quick violation level lookup | `aianticheat.admin` |
| `/aiac status` | View ONNX, GrimAC, and ban system status | `aianticheat.admin` |
| `/aiac reload` | Reload configuration without restart | `aianticheat.admin` |
| `/aiac about` | Author and studio information | `aianticheat.admin` |

---

## 🛠️ Building from Source

### Prerequisites:
* JDK 17 or JDK 21
* Git

### Build Instructions:
```bash
# Clone the repository
git clone https://github.com/YourUsername/SynapseAiAnticheat.git

# Navigate to project directory
cd SynapseAiAnticheat

# Build shadow JAR
./gradlew shadowJar
```
The compiled fat JAR will be located at:
`build/libs/purpur-ai-anticheat-3.0.0.jar`

---

## 👑 Authors & Credits

* **Core Developer:** Даня (`smyzik`, `smerchhh`) & `SynapseLabs`
* **Official Studio Discord:** [dsc.gg/synapselabs](https://dsc.gg/synapselabs)

---

## 📄 License
This project is licensed under the MIT License.
