# LandClaim

Radius-based cylindrical land claiming plugin for Paper 1.21.4.

## Requirements

- Paper 1.21.4
- Java 21
- Vault (optional — economy features)

## Commands

| Command | Description |
|---|---|
| `/claim create [name]` | Claim land at your location. Name is lowercased, alphanumeric/hyphen/underscore only. Auto-names `claim-1`, `claim-2` etc. if omitted. |
| `/claim delete <name> [confirm]` | Delete a claim by name. Requires re-running or appending `confirm`. |
| `/claim list` | List your claims. |
| `/claim info [name]` | Show claim details. Targets current location if name omitted. |
| `/claim upgrade <name>` | Upgrade a claim to the next tier. |
| `/claim trust <player>` | Add a player to the claim at your location. |
| `/claim untrust <player>` | Remove a player from the claim at your location. |
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
| `landclaim.admin` | op | Admin commands |
| `landclaim.tier.1` | true | Unlocks tier 1 claims |
| `landclaim.tier.<n>` (n ≥ 2) | false | Unlocks tier `n` (grant to allow upgrades) |
| `landclaim.claims.<n>` | — | Overrides max claims per player (replace with number) |

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
list-format: "&6{name} &7- &f({x}, {z}) &7Radius: &f{radius} &7Tier: &f{tier} &7[{status}]"

# Display format for /claim info
info-format: "&6=== {name} ===\n&7Owner: &f{owner}\n&7Location: &f({x}, {z}) in {world}\n&7Radius: &f{radius}\n&7Tier: &f{tier}\n&7Status: &f{status}\n&7Members: &f{members}"

# Refund percentage on delete (0.0 - 1.0)
refund-on-delete: 0.7

# Tax settings
tax:
  enabled: false
  amount-per-tier: 50.0
  period-days: 7
  grace-period-days: 7
```

### Placeholders

`{name}`, `{owner}`, `{x}`, `{z}`, `{radius}`, `{tier}`, `{status}`, `{world}`, `{id}`, `{members}`

## Database

SQLite (`landclaim.db` in plugin data folder). Three tables: `claims`, `claim_members`, `claim_taxes`.

## Build

```sh
mvn clean package
```
