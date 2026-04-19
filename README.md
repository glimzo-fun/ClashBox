# ClashBox — Glimzo Network

> **PvP Mining Arena Gamemode** | Spigot 1.8.8 | Depends on GlimzoCore

---

## Table of Contents

1. [Overview](#overview)
2. [Dependencies](#dependencies)
3. [Building with Maven](#building-with-maven)
4. [Installation](#installation)
5. [First-Time Server Setup](#first-time-server-setup)
6. [config.yml Reference](#configyml-reference)
7. [Registering Ore Nodes](#registering-ore-nodes)
8. [Portal System](#portal-system)
9. [Commands Reference](#commands-reference)
10. [Permissions](#permissions)
11. [Architecture Notes](#architecture-notes)

---

## Overview

ClashBox is a zone-based PvP arena gamemode. Players physically jump from the lobby into a nested pit arena, mine resources, fight other players, and extract their earnings back to the lobby through randomly-rotating return portals.

**Three zones descend into the pit:**
- **Outer Zone** — Safe, low-yield mining. No PvP.
- **Mid Zone** — PvP enabled, better ore, 50% inventory loss on death.
- **Core Zone** — Full loot drop on death, 3x kill rewards, rarest ores, constant events.

---

## Dependencies

| Dependency | Version | Notes |
|---|---|---|
| Spigot / CraftBukkit | 1.8.8 | Must be 1.8.x |
| GlimzoCore | 1.0.0 | Required — must be loaded first |
| Java | 17+ | Compile target |
| Maven | 3.6+ | Build tool |

Place your local `GlimzoCore.jar` in a `libs/` folder at project root:
```
ClashBox/
├── libs/
│   └── GlimzoCore-1.0.0.jar   ← put it here
├── src/
└── pom.xml
```

Then install it to your local Maven repo:
```bash
mvn install:install-file \
  -Dfile=libs/GlimzoCore-1.0.0.jar \
  -DgroupId=net.glimzo \
  -DartifactId=GlimzoCore \
  -Dversion=1.0.0 \
  -Dpackaging=jar
```

---

## Building with Maven

```bash
cd ClashBox
mvn clean package
```

Output: `target/ClashBox-1.0.0-SNAPSHOT.jar`

---

## Installation

1. Drop `ClashBox-1.0.0-SNAPSHOT.jar` into your server's `plugins/` folder.
2. Ensure `GlimzoCore.jar` is also in `plugins/`.
3. Start the server — ClashBox generates `plugins/ClashBox/config.yml`.
4. Stop the server, configure `config.yml` (see below), restart.

---

## First-Time Server Setup

Follow these steps **in order** after installation:

### Step 1 — Configure the world and lobby spawn

In `config.yml`, set:
```yaml
world:
  name: "your_world_name"       # World the arena is in
  center-x: 0                   # X coordinate of arena center (XZ)
  center-z: 0                   # Z coordinate of arena center (XZ)
  lobby-spawn:
    x: 0.5
    y: 100.0                    # Y above the pit
    z: 0.5
  pit-radius: 5.0               # XZ radius of the pit opening
  pit-entry-y: 95.0             # Y level where entry is detected (below lobby floor)
```

### Step 2 — Set zone radii to match your map

```yaml
zones:
  outer:
    radius: 80      # XZ radius from center
    y-min: 55       # Y floor of outer zone
    y-max: 80       # Y ceiling of outer zone
  mid:
    radius: 50
    y-min: 35
    y-max: 55
  core:
    radius: 25
    y-min: 10
    y-max: 35
```

Measure your map in-game and fill in these values to match the physical arena boundaries.

### Step 3 — Set portal spawn locations

Portals need predefined candidate locations. Set at least 6 (the system picks randomly from the pool). Spread them across all zones:

```yaml
portal:
  active-count: 1           # Portals active at once
  relocation-interval: 120  # Seconds between relocations
  spawn-locations:
    - x: 30.5
      y: 62.0
      z: 30.5
    - x: -30.5
      y: 62.0
      z: 30.5
    # ... add more
```

### Step 4 — Register ore nodes in-game

Join the server as an OP, go into the arena, look at a stone block where you want ore to spawn, and run:

```
/cb ore add OUTER    ← registers as an outer zone ore node
/cb ore add MID      ← mid zone
/cb ore add CORE     ← core zone
```

The block will immediately convert to a zone-appropriate ore. Register **at least 20 nodes per zone** for a good experience (50+ recommended for large maps).

When done, save them:
```
/cb ore save
```

Nodes auto-load from `nodes.yml` on every server start.

### Step 5 — Test the full loop

1. Jump into the pit from the lobby
2. Mine an ore block — should give coins in actionbar
3. Move between zones — should get title transitions
4. Kill a player in Mid/Core — should receive kill reward
5. Use a portal — should teleport back to lobby
6. Check `/bank balance`

---

## config.yml Reference

### Economy Tuning

```yaml
economy:
  base-kill-reward: 150          # Coins for a base kill
  base-ore-prices:
    COAL_ORE: 10
    IRON_ORE: 35
    GOLD_ORE: 80
    DIAMOND_ORE: 300
    EMERALD_ORE: 500
  ore-zone-multipliers:
    outer: 1.0
    mid: 1.8
    core: 4.0                    # Core ore sells for 4x
  bank:
    default-capacity: 50000      # Starting bank capacity
    deposit-tax-percent: 0.5     # 0.5% tax on every deposit
    investment-tiers:
      7200: 0.03                 # 2h lock → 3% interest
      28800: 0.06                # 8h lock → 6% interest
      86400: 0.10                # 24h lock → 10% interest
```

### Combat Tuning

```yaml
combat:
  base-kill-reward: 150
  streak-coin-multiplier-per-kill: 0.10    # +10% per streak level
  streak-announce-milestones: [3, 5, 8, 12, 20]
  bounty:
    base-multiplier: 2.0
    minimum-value: 500
    expiry-seconds: 600
```

### Events

```yaml
events:
  min-players-to-fire: 3        # Events won't fire below this
  supply-drop:
    enabled: true
    min-delay-seconds: 480      # 8 min minimum between drops
    max-delay-seconds: 840      # 14 min maximum
  vein-burst:
    enabled: true
    min-delay-seconds: 180
    max-delay-seconds: 360
    ore-count: 20
  bank-raid:
    enabled: true
    min-delay-seconds: 9000     # ~2.5 hours minimum
    max-delay-seconds: 12600
```

---

## Registering Ore Nodes

Ore nodes are the heart of the mining system. They are predefined world locations that regenerate ore on a timer after being mined.

```
/cb ore add [OUTER|MID|CORE]   — Register looked-at block as node
/cb ore remove                 — Remove looked-at node
/cb ore list                   — List all nodes grouped by zone
/cb ore save                   — Save to nodes.yml
/cb ore load                   — Reload from nodes.yml
/cb ore clear --confirm        — Delete all nodes
```

**Tips:**
- You must be looking directly at a stone/rock block (within 5 blocks)
- If zone is omitted, it auto-detects based on the block's position
- After `/cb ore save`, nodes persist through restarts automatically
- The regen manager checks nodes every 5 seconds — newly mined nodes reappear within 10–90 seconds depending on zone

**Recommended node counts:**

| Zone | Minimum | Recommended |
|------|---------|-------------|
| Outer | 20 | 40–60 |
| Mid | 15 | 30–40 |
| Core | 10 | 20–25 |

---

## Portal System

Return portals spawn at random locations from the pool in `config.yml` → `portal.spawn-locations`.

- Every `relocation-interval` seconds, all active portals disappear and new ones spawn at freshly randomised locations
- The new coordinates are **broadcast to all players** in chat with X/Y/Z coordinates and zone label
- Players step into the portal radius (1.5 blocks) to teleport back to the lobby
- Visuals: spinning portal particle ring + END_ROD column — visible from a distance
- Use `/cb portal` to force a manual relocation for testing

**Important:** Portal locations must be set in `config.yml` before first launch. The system cannot auto-discover locations — it picks from your pre-defined pool. Add at least 8–10 locations spread across different zones and Y-levels for variety.

---

## Commands Reference

### Player Commands

| Command | Description |
|---|---|
| `/bank` | View bank balance, wallet, vault status |
| `/bank deposit <amount\|all>` | Deposit wallet coins to bank (0.5% tax) |
| `/bank withdraw <amount\|all>` | Withdraw from bank to wallet |
| `/bank invest <amount> <2h\|8h\|24h>` | Lock coins for interest |
| `/bank claim` | Claim matured investment |
| `/stats [player]` | View season and lifetime stats |
| `/leaderboard [kills\|coins\|kd]` | Season leaderboard top 10 |
| `/bounty list` | View all active bounties |
| `/team create` | Create a new team |
| `/team invite <player>` | Invite player to team |
| `/team accept` | Accept pending invite |
| `/team leave` | Leave current team |
| `/team vault deposit <amount>` | Deposit to team vault |
| `/team vault withdraw <amount>` | Withdraw from team vault (leader only) |
| `/team info` | View team details |

### Admin Commands (`/cb` or `/clashbox`)

| Command | Description |
|---|---|
| `/cb reload` | Reload config.yml |
| `/cb ore add [zone]` | Register ore node at looked-at block |
| `/cb ore remove` | Remove ore node at looked-at block |
| `/cb ore list` | List all nodes |
| `/cb ore save` | Save nodes to nodes.yml |
| `/cb ore load` | Reload nodes from nodes.yml |
| `/cb ore clear --confirm` | Delete all nodes |
| `/cb portal` | Force portal relocation |
| `/cb give <player> <amount>` | Give coins to player |
| `/cb saveall` | Force save all online profiles |
| `/cb upgrade <player> <type>` | Admin-apply upgrade |

---

## Permissions

| Permission | Default | Description |
|---|---|---|
| `clashbox.admin` | OP | Full admin access, all `/cb` commands |
| `clashbox.player` | true | All player commands |

---

## Architecture Notes

ClashBox is a **GlimzoCore consumer plugin**. It adds zero duplicate infrastructure — all data persistence, economy, GUI, and event hooks run through GlimzoCore's existing systems.

**Key design decisions:**

- **Zone detection** uses XZ cylinder radius + Y range checks, not WorldGuard regions. This is lightweight for 1.8 and requires no external dependency.
- **Ore nodes** are defined manually by admins, not procedurally. This gives full control over ore distribution and avoids performance issues.
- **All coin movement** routes through `EconomyHook` — one choke point for auditing, logging, and future anti-cheat integration.
- **PlayerState enum** gates every system. If state is wrong, nothing fires. This prevents dual-trigger bugs between lobby and arena.
- **Investment vault** uses Unix timestamps, not tick counters, so values survive server restarts cleanly.
- **Portals** are particle-only (no block placement) — zero world modification, no cleanup needed on restart.

**Data files:**
```
plugins/ClashBox/
├── config.yml          ← All tunable values
├── nodes.yml           ← Saved ore node locations (auto-generated)
└── playerdata/
    └── <uuid>.yml      ← Per-player profile (wallet, bank, upgrades, stats)
```

---

*Built for Glimzo Network. Package: `net.glimzo.clashbox`*
