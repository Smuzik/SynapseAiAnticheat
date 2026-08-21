# Combat scenarios — shared regression fixtures

These JSON files are the **single, language-neutral set of behavioural test cases** for the detection
pipeline. They are consumed by both sides so Java and the Python prototype are judged against identical
cases:

- **Python** (`freecam-pvp-collector/python/run_scenarios.py`) — prototypes and proves the Context/Risk
  math here in-sandbox (Phase 1), before it is ported to Java.
- **Java** (`src/test/java/.../ScenarioResourcesTest.java`) — loads these same files as test resources
  and, once the Risk Engine exists, asserts each `expected_verdict`.

## Format

```jsonc
{
  "id": "unique_snake_case_id",
  "description": "Human-readable narrative: what the player did and why the verdict is what it is.",
  "expected_verdict": "LEGIT | SUSPICIOUS | CHEAT",
  "feature_schema_version": "combat.v1",

  // 16 canonical features (see docs/FEATURE_SCHEMA.md). SIGNED yaw/pitch. Any omitted key falls back
  // to FeatureSchema.neutralDefault(). These feed the ONNX model — one signal among many.
  "features": { "distance": 3.05, "angle_offset_deg": 3.5, /* ... */ },

  // Raw detector signals that fired for this event. Each is ONE input to the Risk Engine — never a
  // verdict on its own. `value` is optional (e.g. measured reach distance).
  "signals": [ { "type": "HARD_SNAP", "fired": true }, { "type": "REACH", "fired": true, "value": 3.42 } ],

  // Context used by the Risk Engine as multiplicative modifiers on each signal's base weight.
  "context": {
    "near_wall": false, "in_corner": false, "victim_knockback": false,
    "attacker_ping_ms": 45, "is_crit": false, "target_switch": false, "repeated_pattern": false
  }
}
```

### Verdicts
- **LEGIT** — final risk below the action threshold; no punishment.
- **SUSPICIOUS** — elevated risk; observe / alert staff, but do not punish automatically.
- **CHEAT** — risk high enough to act on.

### Signal types (extended as checks are ported in Phase 1)
`HARD_SNAP`, `HITBOX_MISS`, `REACH`, plus the model output (added by the engine, not stored here).

## Current fixtures

| id | verdict | why it matters |
|---|---|---|
| `corner_360_crit_legit` | LEGIT | **Headline false positive.** HardSnap + hitbox-miss fire, but corner + knockback + high ping + decelerating flick + crit ⇒ human. |
| `normal_combat_legit` | LEGIT | Baseline clean melee; no signals fire. |
| `reach_lagspike_legit` | LEGIT | Reach looks like 3.42m; lag compensation over the ping window makes it valid. |
| `blatant_killaura_cheat` | CHEAT | Robotic centred aim, no deceleration, target switching, repeated pattern. |

> **Rule:** every confirmed false positive we fix gets a fixture added here that must pass as LEGIT, so
> it can never silently regress.
