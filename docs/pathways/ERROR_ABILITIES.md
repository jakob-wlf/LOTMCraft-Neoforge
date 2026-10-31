# Error Pathway Abilities

## Spirituality

Spirituality regenerates at **0.06% of max per tick** (1.2% per second) passively.

| Sequence | Max Spirituality | Regen/sec |
|----------|-----------------|-----------|
| 9        | 360             | 4.3/s     |
| 8        | 400             | 4.8/s     |
| 7        | 1,560           | 18.7/s    |
| 6        | 2,400           | 28.8/s    |
| 5        | 3,800           | 45.6/s    |
| 4        | 7,800           | 93.6/s    |
| 3        | 10,000          | 120.0/s   |
| 2        | 20,000          | 240.0/s   |
| 1        | 40,000          | 480.0/s   |
| 0        | 120,000         | 1,440.0/s |

---

## Ability Theft Mechanic

**Ability Theft** is the Error pathway's signature: stealing copies of another Beyonder's abilities and temporarily disabling them on the original owner. The TheftHandler governs success, count, uses, and disable time.

**Theft failure:** Against a same-sequence or stronger target there is a **15% fail chance per sequence of difference** (reduced by luck, capped at 60–80%). Against a significantly stronger target, theft automatically fails. Error Beyonders of a lower sequence are **immune**. A loophole found with **Find Loophole** lowers the failure chance to 30% of its value.

**Decryption:** Ability Theft (Prometheus and above) only finds something worth taking with a chance of **35% + 13% per decryption level** of the target (100% at level 5).

**On success**, the thief receives copies with limited uses, and the stolen abilities are sealed on the victim:

| Thief Sequence | Abilities Stolen | Uses per Copy |
|----------------|-----------------|---------------|
| 6–5            | 1               | 1–2           |
| 4              | 2               | 4             |
| 3              | 3               | 4             |
| 2              | 4               | 6             |
| 1              | 5               | 6             |
| 0              | 6               | 8             |

**Seal duration (Ability Theft):** 4 minutes against peers or stronger targets, +6 minutes for every sequence the target is below the thief, up to **40 minutes**. Thefts performed by other abilities (loopholes, parasitation) keep the short 8–20 second seal.

**Second bodies (Sequence 3+):** robbing an avatar, marionette, body double or historical projection seals the same abilities on its true body and performs an additional theft on it.

---

## Active Abilities

---

### Time Manipulation
**Sequence Requirement:** 1
**Spirituality Cost:** 20000 / 10000 (Seq 0 / 1)
**Cooldown:** 10 / 15 seconds

Every use calls the phantom of an **ancient stone clock infested with worms of time** (Photon effect). Its hands show what happens to time: frozen when stopped, racing when accelerated, crawling when slowed, and turning back when time reverses.

**Stop / Accelerate / Slow Time**
- Spawns a **Time Change entity** (radius 50, 10 seconds) with a time multiplier of **0.001× / 4× / 0.2×**.
- **Cooldowns** of everyone in the field progress at the same speed: frozen, four times as fast or five times as slow.

**Reverse Time**
- The clock records everything within 30 blocks for **8 seconds** (blocks within 10 blocks as well). Then its hands turn back and every recorded creature is rewound along its path, regaining health and losing new fire. Using it again during the recording reverses early.

**Summon Stone Clock**
- Summons the phantom clock for **30 seconds**. Every second its worms leech time from enemies within 14 blocks (+0.5 s on all their cooldowns, Slowness) and feed it to the owner (shorter cooldowns, stored Time for gifting).

**Steal Time**
- The target's time is stolen for `30 × multiplier` seconds: Slowness II, Mining Fatigue II, cooldowns progress at half speed, digestion and sanity drain are halved. The stolen seconds can be gifted (**Gift Time**), which returns time to someone it was stolen from or speeds anyone else up.

**Steal Day/Night** (moved here from Conceptual Theft)
- Steals the day (making it midnight) or the night (making it noon) and gives a **Sun** or **Moon** item.

---

### Conceptual Theft
**Sequence Requirement:** 1
**Spirituality Cost:** 7500 (12000 at Seq 0) for Area, Digestion and Sanity
**Cooldown:** 5 / 8 seconds

Four selectable modes (Steal Day/Night moved to Time Manipulation):

**Area Theft**
- **Target Range:** 50 blocks; **Radius:** 24×9 ellipsoid (requires griefing)
- Removes all blocks in the target area (up to **10,000 blocks**) and stores them as an **Excavated Area Item**.

**Digestion / Spirituality / Sanity Theft**
- **Target Range:** `15 × multiplier²` blocks
- Drains the target's digestion, spirituality or sanity and partially restores the caster's. Subject to theft failure.

---

### Fate Siphoning
**Sequence Requirement:** 2
**Spirituality Cost:** 4000
**Cooldown:** 20 seconds
*(Cannot be copied)*

- **Target Range:** 30 blocks
- Links the caster's fate to the target for **14 seconds**.
- While linked:
  - **All incoming damage** to the caster is **cancelled** and redirected to the target instead.
  - **All negative potion effects** applied to the caster are blocked and redirected to the target instead.
- **Resistance:** Higher-sequence targets may resist the link being established. Error Beyonders of lower sequence always resist.
- Only one link can be active per caster at a time.

---

### Loopholes
**Sequence Requirement:** 2
**Spirituality Cost:** 6700–24000
**Cooldown:** 7–16 seconds

- **Create Loophole** – the original area loophole: entities within 3 blocks are dragged to its center, robbed of abilities and their abilities are cast by the caster instead.
- **Find Loophole** – on a second body (avatar, marionette, body double, void summon) below **50% health**: reveals where its true body is. On a true body: for 60 seconds its resistance against your theft and deceit is greatly lowered.
- **Exploit Loophole** – strips invincibility (invulnerability, Deceive Harm, ability invulnerability, Resistance IV+). If the target is not invincible, their next ability within 60 seconds also damages them (15% max health × multiplier).
- **Reality Loophole** – banishes historical void summons, dream summons and manifested abilities with a duration around the target point (time fields may only lose 80% of their duration instead).
- **Dimension Loophole** – use on a door, then on another door: they are linked and walking through one leads out of the other.
- **Time Loophole** *(Sequence 1)* – everything with a duration the target maintains dissipates: toggled abilities, effects, clocks and time fields, summons, deceit traps, invulnerabilities.

---

### Deceit
**Sequence Requirement:** 2
**Spirituality Cost:** 3400–9000
**Cooldown:** 5–9 seconds

- **Deceive Laws** – every prohibition of a Justiciar within 90 blocks (not cast by someone significantly stronger) works in reverse for its remaining time: forbidden abilities grant Strength and spirituality, forbidden combat grants Strength II and Resistance, forbidden flight grants Slow Falling, Jump Boost and Speed, and so on.
- **Deceive Ability** – grab the manifested ability (tornado, meteor, portal, time field, ...) closest to your crosshair; use again within 30 seconds to put it where you look.
- **Deceive Senses** – nearby enemies forget about you (mobs lose their target, 0.8× damage modifier for 10 seconds).
- **Deceive Harm** – for 5 seconds equal or stronger sources cannot hurt you.

---

### Avatar Creation
**Sequence Requirement:** 4
**Spirituality Cost:** 1500
**Cooldown:** 5 seconds
*(Cannot be copied, replicated, used by NPCs, or used in artifacts)*

- Spawns an **Error Avatar** entity at the caster's position.
- The avatar uses the caster's pathway ("error") at **caster sequence + 1** (one sequence weaker).

---

### Parasitation
**Sequence Requirement:** 4
**Spirituality Cost:** 4000
**Cooldown:** 5 seconds
*(Cannot be copied, replicated, used by NPCs, or used in artifacts)*

- **Range:** 8 blocks
- Cannot attach to Beyonders of equal or lower sequence than the caster.
- Merges the caster into a selected host entity:
  - The caster becomes invisible (zero-size bounding box).
  - The host is continuously dragged to move alongside the caster.
  - **All damage** directed at the caster is redirected to the host instead.
  - Mobs targeting the caster have their targets cleared.
  - All **harmful effects** on the caster are removed each tick.
  - The host's fire is cleared on the caster each tick.
  - The host follows the caster's last attack target.
- Deactivates if the host dies, is removed, or moves more than ~11 blocks away.

**Trojan Horse of Destiny (Seq 2+):**
- The worms no longer alert the target (no initial damage that would trigger substitutes) and control is **20% more likely** to succeed.
- Using Control with a **sealed artifact in the off hand** (and no target) lets the worms settle inside the artifact; its negative effects then only affect you a quarter of the time. Use again to call them back.

---

### Host Controlling
**Sequence Requirement:** 4
**Spirituality Cost:** 0
**Cooldown:** 0.5 seconds
*(Cannot be copied, replicated, or used in artifacts)*

*Requires an active Parasitation host.*

Two selectable modes:

**Mode 0 — Drain Health**
- Deals **~19.4 damage × caster multiplier** to the host and heals the caster for the same amount.

**Mode 1 — Kill**
- Sets the host's health to 0.5 and deals 1000 damage, instantly killing it.

---

### Mundane Conceptual Theft
**Sequence Requirement:** 5
**Spirituality Cost:** 50
**Cooldown:** 1 second

Four selectable modes (Mode 0–2 require a target within `15 × multiplier²` blocks; subject to theft failure against Beyonders):

**Mode 0 — Steal Walk**
- Removes the target's ability to move (**removes all movement speed**) and zeroes their velocity every 2 ticks.
- **Duration:** scales with sequence difference:
  - Target weaker: **5–30 seconds** (shorter for larger gaps)
  - Target equal: **30 seconds**
  - Target stronger: **up to 120 seconds**

**Mode 1 — Steal Sight**
- Applies **Blindness (Level 5)** to the target for the same duration as Mode 0.
- Clears mob targeting.

**Mode 2 — Steal Health**
- Deals **~14.2 damage × caster multiplier** to the target and heals the caster for the same amount.

**Mode 3 — Steal Distance**
- *Only usable at Sequence 6 or lower.*
- Teleports the caster to the targeted location within range. Teleport range scales with sequence:
  - Seq 6: **8 blocks** | Seq 5: **16 blocks** | Seq 4: **32 blocks** | Seq 3: **64 blocks** | Seq 2: **128 blocks** | Seq 1: **256 blocks**

**Mode 4 — Steal Thoughts**
- Seals the target's abilities for half the theft duration, applies Nausea and makes mobs forget their target.

Everything stolen with Walk, Sight, Thoughts, Distance and excess Health is stored and can be given away through the **Gifting Menu**.

---

### Ability Theft
**Sequence Requirement:** 6
**Spirituality Cost:** 200
**Cooldown:** 3 seconds
*(Cannot be copied or replicated)*

Two selectable modes:

**Mode 0 — Steal**
- **Target Range:** 20 blocks
- Attempts to steal abilities from the target (see Ability Theft mechanic above).

**Mode 1 — Use Copied**
- Opens the **Copied Ability Wheel** to select and use a previously stolen ability.

---

### Gift
**Sequence Requirement:** 6
**Spirituality Cost:** 60
**Cooldown:** 1–3 seconds

- **Gift Item** – gives the off-hand item to the target. TNT, anvils, ender pearls, fire charges, lava and water buckets have special effects.
- **Gifting Menu** – lists everything that was stolen and gives the chosen thing to whoever you look at:
  - **Distance** – pushes the target along your gaze
  - **Movement** – allies get Speed III and Jump Boost; enemies stumble around uncontrollably
  - **Sight** – allies get Night Vision and see enemies glowing; enemies get Nausea and Darkness
  - **Thoughts** – allies' cooldowns shrink; enemies' abilities are sealed
  - **Fire / Frost** (from Steal Yourself) – sets the target on fire or freezes them
  - **Time** (Worm of Time) – returns stolen time or speeds the target up
  - **Vitality** – heals the target
  - **Effects** stolen from yourself and **stolen abilities** (players receive the remaining uses)
- **Gift Health** (Seq 5–2), **Gift Digestion** and **Gift Luck** (Seq 1–0) as before.

---

### Decryption
**Sequence Requirement:** 7
**Spirituality Cost:** 0.25 per tick (toggle)

- **No longer sees through walls.** Night Vision while active.
- The longer you look at a target (40 blocks, line of sight), the more is revealed: **1** Beyonder or not, **2** sequence, **3** pathway, **4** true body or second body, **5** true name (and honorific name). Abilities the target uses make decryption faster; stronger targets are slower.
- **Illusions:** low level invisibility of beings not stronger than you flickers into a translucent silhouette.
- **Dreams & illusions:** while asleep, caught in a dream weave or a nightmare, Decryption activates on its own and keeps shortening or breaking the illusion.
- **Marked items:** holding an item that was stolen from / dropped by someone for 5 seconds reveals the owner's location if they are weaker.
- **Artifacts:** with Decryption active, the anvil already shows what a sealed artifact will become (abilities and negative effects), or that the forging will fail.

---

### Mental Disruption
**Sequence Requirement:** 8
**Spirituality Cost:** 40
**Cooldown:** 2 seconds

- **Target Range:** 20 blocks
- **Hits:** **1 hit** per cast
- **Damage:** **~6.3 damage × caster multiplier** (Losing Control damage type).
- Applies **Slowness (Level 9, 6 seconds)** and **Blindness (Level 9, 1.5 seconds)**.
- Zeroes the target's velocity every 2 ticks for **4 seconds**.

---

### Theft
**Sequence Requirement:** 8
**Spirituality Cost:** 130–3000
**Cooldown:** 1–3 seconds

- **Swindler / Cryptologist – Theft:** only things that carry spirituality can be stolen (characteristics, potions, ingredients, recipes, artifacts, stolen abilities, conceptual items). Spiritual items on the ground within 7 blocks can be taken as well.
- **Prometheus and above – Quick Theft:** steals a random item (inventory or equipment) from a target within 50 blocks, or an item lying on the ground. While Quick Theft is selected, a small red **X** marks what would be stolen.
- **Thorough Theft:** opens the target's pockets. Same sequence: 10 seconds and 1 pick; every sequence the target is below you adds 5 seconds and one pick (max 40 seconds, 6 picks). Against stronger targets or full inventories some slots are hidden behind question marks – they can still be stolen.
- Stolen items remember their owner (see Decryption).

---

## New Active Abilities

### Pickpocket
**Sequence Requirement:** 9 · Toggle

- An empty hand no longer strikes: no damage, no punch sound, no knockback, no aggro. Instead there is a 60% chance (on a fully charged swing) to lift something from the target.
- With something in the hand you attack normally and have a 30% chance to steal at the same time.

### Eloquence / Deceiving Words
**Sequence Requirement:** 8 · Toggle

- **Eloquence:** every chat message has a chance to forcefully make the people in front of you (180°, 12 blocks) one-way allies for 3 minutes (30% for non-Beyonders, 20% ± 6% per sequence for Beyonders).
- **Deceiving Words (Seq 3):** while you are typing, everyone in front of you (24 blocks) cannot take their eyes off you, with a small chance per second to be bound; allies last 6 minutes.

### Superior Observation (toggle)
**Sequence Requirement:** 6 · Toggle

- Every container and every item lying on the ground within 32 blocks is highlighted.

### Steal Yourself
**Sequence Requirement:** 6

- Takes fire, frost and harmful effects off your body and stores them for the Gifting Menu.

### Treasure Hunter
**Sequence Requirement:** 6 · Toggle

- Looking at a creature (32 blocks) shows a window next to it with its equipment, hotbar, valuables and estimated value. Items on the ground (including shulker box contents) are appraised as well.

### Deceive Minds
**Sequence Requirement:** 3

- **Prepare Deceit** configures and alloys deceits. **Apply Deceive** opens a wheel to apply one deceit or the alloy to the target – sneak while using it to deceive everyone within 20 blocks. Duration 60–180 seconds (halved against stronger targets).
- **Parasite** – the host stays frozen for 8 seconds after its parasite leaves or dies.
- **Spiritual Intuition** – danger premonitions no longer warn the target about you.
- **Sanity** – the target sees a false sanity; observers read the false value for you as well.
- **Health** – the target sees false health. At 0 a fake death screen appears: Respawn kills them for real, Title Screen just closes it.
- **Spirituality** – fluctuating, fixed, "overused" or "unused" spirituality bar.
- **Item** – the held item looks like and is named like another item (only for the target).
- **Hunger** – drains quickly, barely drains, completely drained (visual) or food barely fills them (real).
- **Inventory** – the target's inventory screen shows a fake inventory painted in the editor.
- **Divination** – their divinations show a fake location and sequence, point at someone else, or at a higher being that hurts their sanity.
- **Ally** – you appear in their ally list; their hits land silently with no knockback.
- **Ability** – their own ability effects look feeble and their abilities always end up slightly off target.

### Deceive World
**Sequence Requirement:** 3

- **Body** – after 4 deceits on a person: one of their second bodies is killed and, with a chance, the world believes the true body died.
- **Connection** – the true body and a second body trade places (guaranteed after 3 deceits or against weaker targets). A player fighting through a marionette is dragged back into their real body, which is pulled onto the battlefield.
- **Block** – for 10 minutes: furnaces, smokers and blast furnaces burn without fuel, brewing stands brew without blaze powder, enchanting tables act as if fully surrounded by bookshelves, anvils repair themselves and never break, neither costs experience; crops and saplings grow rapidly, doors and trapdoors believe they were opened.
- **History** – after decrypting someone to level 3, step out next to their body in the Historical Void; use again to return.

### Deceive Connection
**Sequence Requirement:** 3

- **Set Up a Deceive** – Teleport In / Teleport Out coordinates.
- **Opened Trap** – an area around you (32–80 blocks): anyone teleporting in lands at the "In" coordinates, anyone teleporting out at the "Out" coordinates. Every kind of teleport is detected.

### Tamper Fate
**Sequence Requirement:** 2

- **Tamper** – small accidents for enemies (fumbling their item, tripping, losing luck, lightning, confusion) and small fortunes for allies.
- **Counter-Tamper** – sends every prophecy a weaker being made about you back to its maker. Any Tamper does this automatically while such a prophecy is pending.

### Steal Fate
**Sequence Requirement:** 2

- **Steal** – takes a fraction of the target's fate: items and armor, effects, luck, allies, prophecies and psychological cues, and – at half or more – their name and face. Mobs hunting you hunt them instead, their attacks cannot touch you for 3 minutes, and they slowly die for 2 minutes.

| Target relation | Fraction taken | Sanity cost |
|---|---|---|
| 2+ sequences weaker | everything | none |
| 1 sequence weaker | 1/2 | 8% |
| same sequence | 1/3 | 30% |
| 1 sequence stronger | 1/4 | 70% of current |

Low sanity (<30%), low health (<20%) or sealed abilities count as one sequence weaker.

- **Give Fate** – hands everything taken from the last person (including their slow death) to someone else.

### Steal Characteristic
**Sequence Requirement:** 2

- **Steal from someone** – Seq 9–7: 50%, 6–5: 30%, 4: 10%, 3: 6%, 2: 3%, 1: 1% (tripled after Steal Fate, ×1.5 with a found loophole). Extra characteristics of the target's sequence are taken first. Allies give theirs up with 100% (not Sequence 0).
- **Steal from yourself** – extract one of your own characteristics (extra stacks first, otherwise you regress).

### Steal Connection
**Sequence Requirement:** 2

- **Steal Spirit Body Threads** – immune to the Fool's manipulation (puppeteering, marionettes) for `2 min × multiplier`; a marionettist loses one marionette and their puppeteering for 30 seconds.
- **Sever Strings** – a marionette or avatar now answers to you.
- **Steal Bonds** – the target forgets all their allies for a minute.

### Characteristic Consumption
**Sequence Requirement:** 2

- Sequence 9 and 8 characteristics of the **Fool** and **Door** pathways can be consumed: full spirituality, 30% health, +5% digestion, 3 uses of each ability of that characteristic, only 1% sanity.

### Steal Authority
**Sequence Requirement:** 1

- Every ability resting on the authority of the target's pathway is sealed for 2–8 minutes (Fool: change, Door: teleportation, Sun: light, ...). You keep 2 uses of two of them.

### Steal Uniqueness
**Sequence Requirement:** 1 (King of Angels)

- A Worm of Time counts as a King of Angels with at least 2 additional Sequence 1 characteristics. 1% chance to steal the target's uniqueness, 30% right after stealing their fate.

---

## Passive Abilities

---

### Physical Enhancements (Error)
**Sequence Requirement:** 9

Mining efficiency moved to **Agile Hands**.

| Sequence | Speed | Bonus Health | Regeneration |
|----------|-------|--------------|--------------|
| 9        | +1    | —            | —            |
| 8        | +2    | +3           | —            |
| 7        | +3    | +3           | —            |
| 6        | +3    | +4           | —            |
| 5        | +3    | +5           | —            |
| 4        | +5    | +12          | +1           |
| 3        | +5    | +13          | +1           |
| 2        | +6    | +21          | +2           |
| 1        | +6    | +26          | +2           |
| 0        | +7    | +36          | +3           |

---

### Agile Hands
**Sequence Requirement:** 9

- Mining Efficiency +1 (Seq 9–8), +2 (7–5), +4 (4–3), +5 (2–1), +6 (0).

### Combat Proficiency
**Sequence Requirement:** 9

- Strength I while holding a sword.

### Superior Observation
**Sequence Requirement:** 9

- **Seq 9:** golden light flashes at the edge of the screen in the direction of valuables – people carrying them (also through low level invisibility) and untouched loot chests.
- **Seq 8:** staring at someone's face for 10 seconds reveals their sanity as a thin orange bar above their head.
- **Seq 7:** armor durability shows as cracks (20 blocks), held items glimmer and a spark jumps when someone switches items.
- **Seq 3:** strings between marionettes, avatars, body doubles, hosts and void summons and their masters become visible; high sequence (4 and above) Justiciars, Black Emperors, Errors and Wheels of Fortune within 160 blocks are sensed as their pathway symbol slowly becomes visible.

### Charm
**Sequence Requirement:** 8

- Permanent Hero of the Village I.

### Quick Reaction Speed
**Sequence Requirement:** 8

- 15% chance to dodge a physical attack (melee or projectile) from someone of the same sequence, ±5% per sequence of difference (0–60%).

### Mental Resistance
**Sequence Requirement:** 6

- Sanity attacks from other beings deal 60% (Seq 6–5), 45% (Seq 4–3) or 35% (Seq 2–0) of their damage.

### Fate
**Sequence Requirement:** 2

- Immune to divination. Senses who killed your avatars and hosts, anyone shapeshifted into you within 500 blocks, and every prophecy made about you by beings of your sequence or lower. Prophecies of weaker casters are returned by your next Fate Tamper.

### Luck Sense
**Sequence Requirement:** 2

- Perceive the luck of everyone within 24 blocks, like a Monster.
