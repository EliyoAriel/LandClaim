# LandClaim

Radius-based cylindrical land claiming plugin for Paper 1.21.4.

## Requirements

- Paper 1.21.4
- Java 21
- Vault (optional — economy features)
- Floodgate (optional — Bedrock support; run alongside Geyser to allow Bedrock players to join)

## Commands

| Command | Description |
|---|---|
| `/claim` | Open the claim management GUI (when `gui.open-on-bare-claim` is enabled). |
| `/claim gui` | Open the claim management GUI. |
| `/claim help` | Show the command help. |
| `/claim create [name]` | Claim land at your location. Name is lowercased, alphanumeric/hyphen/underscore only. Auto-names `claim-1`, `claim-2` etc. if omitted. |
| `/claim delete <name> [confirm]` | Delete a claim by name. Requires re-running or appending `confirm`. |
| `/claim list` | List your claims. |
| `/claim info [name]` | Show claim details. Targets current location if name omitted. |
| `/claim upgrade <name>` | Upgrade a claim to the next tier. |
| `/claim trust <claim> <player>` | Add a player as a member (all permissions on). |
| `/claim untrust <claim> <player>` | Remove a member from a claim. |
| `/claim flag <claim> <flag> [on\|off]` | Toggle a claim-wide flag. |
| `/claim perm <claim> <player> <flag> [on\|off]` | Toggle a member's permission. |
| `/claim rename <old> <new>` | Rename a claim. |
| `/claim displayname <claim> <text...>` | Set a claim's display name (shown in place of the name in titles, actionbar, lists, and messages; `&` color codes supported, max 48 chars; `-` or `reset` clears it). |
| `/claim paytax` | Pay overdue taxes for all claims. |
| `/claim admin info <player>` | View a player's claims. |
| `/claim admin delete <player>` | Delete all of a player's claims. |
| `/claim admin reload` | Reload config and claims from database. |

## Permissions

| Permission | Default | Description |
|---|---|---|
| `landclaim.create` | true | Create claims |
| `landclaim.delete` | true | Delete own claims |
| `landclaim.list` | true | List own claims |
| `landclaim.info` | true | View claim info |
| `landclaim.trust` | true | Trust members |
| `landclaim.untrust` | true | Untrust members |
| `landclaim.upgrade` | true | Upgrade claim tier |
| `landclaim.paytax` | true | Pay claim tax |
| `landclaim.manage` | true | Manage claim settings (flags, member perms, rename, displayname) |
| `landclaim.admin` | op | Admin commands |
| `landclaim.tier.1` | true | Unlocks tier 1 claims |
| `landclaim.tier.<n>` (n ≥ 2) | false | Unlocks tier `n` (grant to allow upgrades) |
| `landclaim.claims.<n>` | — | Overrides max claims per player (replace with number) |

## GUI

`/claim gui` (or bare `/claim` when `gui.open-on-bare-claim` is enabled) opens an inventory GUI with the same actions as the chat commands:

- **Claims List** — all your claims, paged; click one to manage it.
- **Claim Detail** — upgrade, flags, members, pay tax, rename, display name, show boundary, delete.
- **Flags** — toggle claim-wide flags.
- **Members** — trust/untrust members and toggle per-member permissions.

Text input (trust player, rename, display name) uses a chat prompt — type the value after clicking, or `cancel`. Deleting a claim requires clicking the Delete button twice to confirm.

The **Show Boundary** button is a toggle — click it to keep the claim's particle boundary visible (re-drawn every 0.5s) until you click it again. It turns off automatically when you quit or the claim is deleted.

Flag and permission toggles in the **Flags** and **Members** pages show a short description of what each option does.

### Bedrock support

With Floodgate installed, Bedrock (Geyser) players are detected automatically — no extra config required. Bedrock clients can't see item hover text, so when `gui.bedrock.inventory-descriptions-in-names` is enabled, the flag/member-permission descriptions are embedded directly in the item names for Bedrock players (Java players keep the clean names + lore). Disable the option to fall back to standard names for everyone.

## Configuration

```yaml
# Worlds where land claiming is enabled
enabled-worlds:
  - world

# Radius tiers and upgrade costs
tiers:
  1:
    radius: 5
    cost: 100.0
  2:
    radius: 10
    cost: 500.0
  3:
    radius: 15
    cost: 1000.0
  4:
    radius: 25
    cost: 2500.0

# Maximum number of claims per player (overridden by landclaim.claims.<n> permission)
max-claims-per-player: 3

# Display format for /claim list (& for color codes)
list-format: "&6{displayname} &7- &f({x}, {z}) &7Radius: &f{radius} &7Tier: &f{tier} &7[{status}]"

# Display format for /claim info
info-format: "&6=== {displayname} ===\n&7Owner: &f{owner}\n&7Location: &f({x}, {z}) in {world}\n&7Radius: &f{radius}\n&7Tier: &f{tier}\n&7Status: &f{status}\n&7Members: &f{members}"

# Title shown when entering a claim ({displayname}, {owner} placeholders)
greeting-title-format: "&aWelcome to &f{displayname}"

# Title shown when leaving a claim ({displayname}, {owner} placeholders)
farewell-title-format: "&eLeaving &f{displayname}"

# Subtitle shown under the greeting/farewell title ({owner} placeholder)
title-subtitle-format: "&7Owned by &f{owner}"

# Actionbar shown briefly when entering a claim ({owner}, {displayname} placeholders)
actionbar-format: "&f{owner} &7owns &f{displayname}"

# Refund percentage on delete (0.0 - 1.0)
refund-on-delete: 0.7

# GUI settings
gui:
  enabled: true
  open-on-bare-claim: true
  # Bedrock (Geyser/Floodgate) support
  bedrock:
    # Embed flag/member-permission descriptions in item names for Bedrock
    # players (no hover text on Bedrock); false = standard names for everyone
    inventory-descriptions-in-names: true

# Tax settings
tax:
  enabled: false
  amount-per-tier: 50.0
  period-days: 7
  grace-period-days: 7

# Per-claim flag defaults (owners can override per claim with /claim flag)
flags:
  pvp: false
  explosions: false
  mobs: true
  firespread: false
  public-use: false
  fluidflow: false
  public-build: false
  public-items: false
  teleport: false
  crops: false
  decay: false
  pistons: false
  gravity: false
```

### Flags

Claim-wide flags (defaults in `config.yml`, overridable per claim with `/claim flag`):

| Flag | Default | Effect |
|---|---|---|
| `pvp` | false | Allow PvP inside the claim |
| `explosions` | false | Allow explosions to break claim blocks |
| `mobs` | true | Allow natural mob spawns and mob damage to entities (players, animals, item frames) |
| `firespread` | false | Allow fire to spread |
| `public-use` | false | Outsiders can use containers/doors/buttons |
| `fluidflow` | false | Allow water/lava to flow through |
| `public-build` | false | Outsiders can break and place blocks |
| `public-items` | false | Outsiders can drop/pick up items inside the claim |
| `teleport` | false | Outsiders can teleport into the claim (ender pearls, chorus fruit) |
| `crops` | false | Allow farmland to be trampled |
| `decay` | false | Allow blocks to decay/fade |
| `pistons` | false | Allow pistons to move blocks into or out of the claim |
| `gravity` | false | Allow gravity blocks (sand, gravel, anvils) to fall inside the claim |

Member permissions (default all `on`, toggled per member with `/claim perm`):

`build`, `use`, `redstone`, `doors`, `vehicles`, `animals`, `items`, `pvp`, `teleport`

- `doors` covers doors, trapdoors, gates, and buttons; `redstone` covers levers and redstone interactables.
- Players can always attack hostile mobs (mobs are not protected by `mobs` or `animals`).
- The `mobs` flag blocks spawner/egg/non-player spawns as well as mob damage to players, animals, and item frames inside the claim.

### Placeholders

`{displayname}`, `{name}`, `{owner}`, `{x}`, `{z}`, `{radius}`, `{tier}`, `{status}`, `{world}`, `{id}`, `{members}`, `{flags}`

- `{displayname}` is the claim's display name (`/claim displayname`); falls back to `{name}` when unset. Supports `&` color codes; max 48 characters.
- `{name}` is the raw internal claim name (used as the command key; lowercased, `[a-z0-9_-]`).

### Titles & Actionbar

- Entering a claim shows `greeting-title-format` as a title with `title-subtitle-format` underneath.
- Leaving shows `farewell-title-format`.
- On entry, `actionbar-format` is shown briefly in the actionbar (then cleared so it doesn't block warning notifications).

## Database

SQLite (`landclaim.db` in plugin data folder). Tables: `claims`, `claim_members`, `claim_taxes`, `claim_flags`, `claim_member_flags`.

## Build

```sh
mvn clean package
```
