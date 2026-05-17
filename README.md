# SellPlugin

A Minecraft Paper plugin that lets players sell items for in-game currency through a polished GUI system with per-category multipliers, daily bonuses, and a leaderboard.

## Features

- **Category shop** (`/sell`) — browse categories, view prices, and sell items one category at a time.
- **Quick sell-all** (`/sellall`) — sell every sellable item in your inventory (including shulker box contents) with a single confirm click.
- **Shulker box support** — place a shulker box in the sell GUI or run `/sellall`; the sellable items inside are sold and the (now-emptied) shulker box is returned to you automatically.
- **Per-category multipliers** — players unlock higher sell multipliers by earning money in each category. Multipliers are persistent across sessions.
- **Daily category bonuses** — a configurable number of random categories receive a flat bonus multiplier each day, encouraging varied play.
- **Top sellers leaderboard** (`/topsell`) — paginated GUI showing top earners per category.
- **Economy support** — works with Vault (any Vault-compatible economy plugin) or CoinsEngine.
- **Fully configurable** — GUI titles, item slots, filler materials, sounds, messages, category icons, and item prices are all driven by config files.

## Requirements

| Dependency | Version | Notes |
|------------|---------|-------|
| Paper | 1.21.1+ | Spigot is not supported |
| Vault | any | For standard economy plugins |
| CoinsEngine | any | Alternative economy (optional) |

## Installation

1. Drop `SellPlugin.jar` into your server's `plugins/` folder.
2. Make sure at least one of Vault (+ an economy plugin such as EssentialsX) or CoinsEngine is installed.
3. Restart the server — `plugins/SellPlugin/` will be created with default config files.
4. Edit `config.yml` and `price.yml` to your liking.
5. Run `/sell reload` or restart to apply changes.

## Commands

| Command | Permission | Description |
|---------|-----------|-------------|
| `/sell` | `sellplugin.use` | Opens the main category shop GUI |
| `/sell reload` | `sellplugin.reload` | Reloads config and price files in-place |
| `/sellall` | `sellplugin.use` | Opens the quick sell-all GUI |
| `/topsell` | `sellplugin.topsell` | Opens the top sellers leaderboard |

Aliases for `/sell`: `sellmenu`, `sellgui`, `shop`  
Aliases for `/topsell`: `sellertop`, `leaderboard`

## Permissions

| Node | Default | Description |
|------|---------|-------------|
| `sellplugin.use` | everyone | Use `/sell` and `/sellall` |
| `sellplugin.topsell` | everyone | Use `/topsell` |
| `sellplugin.reload` | op | Use `/sell reload` |

## Configuration

### `config.yml` — key options

```yaml
# Economy backend: VAULT or COINSENGINE
economy-mode: VAULT

# Multiplier unlock thresholds
start-multiplier: 1000.0   # cost of first tier (1.1×)
multiplier: 1.6             # each tier costs (previous × this)
max-multiplier: 3.0         # display cap for the progress bar

# Daily bonus
daily-bonus:
  boosted-count: 2          # how many categories get the bonus each day
  bonus-amount: 0.4         # flat bonus added to the multiplier
```

### `price.yml` — item prices

Items are grouped by category. Each top-level key is a category ID that must also appear in `category-order` inside `config.yml`.

```yaml
ores:
  DIAMOND: 50
  EMERALD: 80
  IRON_INGOT: 5
  GOLD_INGOT: 10
```

Potion prices use a `MATERIAL:POTION_TYPE` key:

```yaml
potions:
  POTION:NIGHT_VISION: 12
  SPLASH_POTION:HEALING: 8
```

## Shulker Box Selling

Any shulker box colour — including the default purple `SHULKER_BOX` and all sixteen dyed variants — is supported everywhere:

- **`/sell` GUI** — place a shulker box in the item area and close the inventory. The sellable items inside are sold, and the now-emptied shulker box is returned to your inventory.
- **`/sellall`** — shulker box contents are sold along with your regular inventory items. The box itself stays in your inventory.

If the economy transaction fails, the shulker box is returned **with its original contents intact** (no items are lost).

## Building from source

```bash
mvn clean package
```

The shaded jar is produced in `target/SellPlugin-<version>.jar`. The `libs/` directory must contain `NightCore.jar` and `CoinsEngine.jar` (provided separately) for the build to succeed.
