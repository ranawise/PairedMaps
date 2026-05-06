# PairedMaps

[![Paper](https://img.shields.io/badge/Paper-1.21.1+-blue.svg)](https://papermc.io)
[![Java](https://img.shields.io/badge/Java-21-orange.svg)](https://adoptium.net)
[![Gradle](https://img.shields.io/badge/Gradle-8.10-02303A.svg?logo=gradle)](https://gradle.org)
[![License](https://img.shields.io/badge/License-MIT-green.svg)](LICENSE)

> Live 3D minimaps for Paper servers using display entities

Render real-time player positions and ground items as glowing dots on 3D minimaps. Perfect for arena tracking, spectator overlays, and immersive HUD elements.

---

## Features

- **Real-time Tracking** — Players and items update every tick (configurable)
- **Terrain Render** — Samples arena surface into minimap using tiny block displays
- **3D Volume Mapping** — Map any region volume to any display volume with aspect ratio validation
- **Visual Effects**
  - Player dots (colored concrete blocks or player heads)
  - Ground item dots that flash when expiring
  - Death burst particles
  - Explosion shockwave rings
- **Persistent Storage** — MySQL backend with HikariCP connection pooling
- **Admin GUI** — Clickable inventory interface for minimap management
- **WorldGuard Integration** — Optional hook for region selection

---

## Quick Start

### 1. Build

```bash
./gradlew build
```

Output: `build/libs/PairedMaps-1.0.0.jar`

### 2. Install

```bash
cp build/libs/PairedMaps-1.0.0.jar /path/to/server/plugins/
```

### 3. Configure

Edit `plugins/PairedMaps/config.yml`:

```yaml
mysql:
  host: localhost
  port: 3306
  database: pairedmaps
  username: root
  password: changeme
  pool-size: 5

update-interval-ticks: 1
dot-scale: 0.1
player-dot-color: "#FF0000"
item-dot-color: "#FFFFFF"
```

### 4. Create Your First Minimap

```bash
/pm add 3000 120 100 3200 220 300 100 64 100 300 164 300
```

Arguments:
`/pm add <map_x1> <map_y1> <map_z1> <map_x2> <map_y2> <map_z2> <region_x1> <region_y1> <region_z1> <region_x2> <region_y2> <region_z2>`

---

## Commands

| Command | Permission | Description |
|---------|------------|-------------|
| `/pm add <map_start> <map_end> <region_start> <region_end>` | `pairedmaps.admin` | Create minimap from two 3D bounding boxes |
| `/pm list` | `pairedmaps.admin` | Open GUI showing all minimaps |
| `/pm remove <id>` | `pairedmaps.admin` | Delete minimap by ID |
| `/pm reload` | `pairedmaps.admin` | Reload configuration |
| `/pm reset` | `pairedmaps.admin` | Respawn all display entities |
| `/pm info <id>` | `pairedmaps.admin` | Show minimap details and give a live map item |

---

## Configuration

### Database

| Option | Default | Description |
|--------|---------|-------------|
| `mysql.host` | `localhost` | MySQL server address |
| `mysql.port` | `3306` | MySQL port |
| `mysql.database` | `pairedmaps` | Database name |
| `mysql.pool-size` | `5` | HikariCP connection pool size |
| `mysql.mode` | `mysql` | `mysql` for persistent DB, `memory` to run without MySQL |

### Display

| Option | Default | Description |
|--------|---------|-------------|
| `update-interval-ticks` | `1` | Tick interval between updates |
| `dot-scale` | `0.1` | Size of dot entities |
| `player-dot-color` | `#FF0000` | Color for player dots (hex) |
| `item-dot-color` | `#FFFFFF` | Color for item dots (hex) |
| `aspect-ratio-epsilon` | `0.05` | Allowed aspect ratio variance |
| `item-flash-threshold-ticks` | `60` | When items start flashing |

### Features

| Option | Default | Description |
|--------|---------|-------------|
| `player-head-dots` | `false` | Use player heads instead of blocks |
| `item-dots` | `true` | Show ground items on minimap |
| `terrain-render` | `true` | Render sampled terrain blocks on minimap |
| `terrain-samples-per-axis` | `20` | Terrain sample density per X/Z axis |
| `terrain-dot-scale` | `0.08` | Scale of terrain render blocks |
| `death-effect` | `true` | Particle burst on player death |
| `explosion-ring` | `true` | Shockwave ring on explosions |
| `death-effect-duration-ticks` | `60` | How long death particles last |

---

## How It Works

### Volume Mapping

PairedMaps uses two axis-aligned bounding boxes:

1. **Region Volume** — The game world area to monitor (e.g., arena bounds)
2. **Map Volume** — Where to render the minimap display (e.g., spectator lobby)

Points are linearly projected from region space to map space in real-time.

### Aspect Ratio Validation

Map and region volumes must have matching aspect ratios (within `epsilon`). This ensures circular players don't appear as ovals on the minimap.

### Display Entities

- Uses `BlockDisplay` for dots (colored concrete)
- Uses `ItemDisplay` for player head mode
- Entities are non-persistent and cleaned up on unload/disable

---

## Technical Stack

| Component | Technology |
|-----------|------------|
| Platform | Paper 1.21.1+ |
| Language | Java 21 |
| Build Tool | Gradle 8.10 (Kotlin DSL) |
| Database | MySQL 8+ with HikariCP |
| Commands | Brigadier via Paper's Lifecycle API |
| Messaging | MiniMessage |
| Math | JOML for transformations |

---

## Permissions

| Permission | Default | Description |
|------------|---------|-------------|
| `pairedmaps.admin` | `op` | Access to all commands |

---

## Development

```bash
# Clone
git clone https://github.com/yourname/PairedMaps.git
cd PairedMaps

# Build
./gradlew build

# Run tests
./gradlew test
```

---

## Troubleshooting

### Display entities not showing

- Verify map volume coordinates are loaded chunks
- Check `dot-scale` isn't too small
- Ensure `update-interval-ticks` > 0

### Database connection failed

- Verify MySQL credentials in `config.yml`
- Check MySQL server is running
- Ensure `mysql.host` points to reachable DB server (not localhost unless DB is on same machine)
- Ensure database exists: `CREATE DATABASE pairedmaps;`

### Aspect ratio error when adding minimap

- Map and region must be full 3D volumes (all axis sizes > 0)
- Map and region volumes must have similar proportions
- Adjust `aspect-ratio-epsilon` to allow more variance
- Or use cubic volumes (equal dimensions)

---

## License

MIT License — see [LICENSE](LICENSE) for details.

---

<div align="center">

Built for PaperMC

</div>
