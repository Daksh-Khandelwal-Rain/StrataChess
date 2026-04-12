# ♟️ StrataChess — A Multiplayer Strategy Game

> A chess-inspired, LAN-based two-player strategy game built in Java. StrataChess keeps the core tension of chess while introducing an economy system, deployable traps, and a one-time crown transfer mechanic that fundamentally changes how the endgame is played.

---

## Table of Contents

- [Project Overview](#project-overview)
- [Team](#team)
- [Game Rules](#game-rules)
  - [Board & Setup](#board--setup)
  - [Turn Structure](#turn-structure)
  - [Movement](#movement)
  - [Economy System](#economy-system)
  - [Trap System](#trap-system)
  - [Crown Transfer](#crown-transfer)
  - [Win Condition](#win-condition)
  - [Timer](#timer)
- [Proposed Features & Open Discussions](#proposed-features--open-discussions)
  - [Expanded Coin Usage](#expanded-coin-usage)
  - [Piece Protection / Shield Mechanic](#piece-protection--shield-mechanic)
  - [Other Ideas Under Consideration](#other-ideas-under-consideration)
- [Rules Open for Modification](#rules-open-for-modification)
- [Architecture Overview](#architecture-overview)
- [Tech Stack](#tech-stack)
- [Getting Started](#getting-started)
- [Branching & Contribution Guidelines](#branching--contribution-guidelines)
- [Roadmap](#roadmap)

---

## Project Overview

StrataChess is a student-built Java project developed over 25 days as part of a college software engineering course. It is played over a LAN connection — two players on the same Wi-Fi network can host and join a session. The host acts as the authoritative server; all game actions are validated server-side before being applied.

The game is designed with a strong emphasis on clean OOP architecture, an MVC pattern, and a custom game engine written entirely by the team (no AI-generated core logic).

---

## Team

| Member | Responsibility |
|--------|---------------|
| [Daksh] | Networking — ServerThread, ClientThread, action serialization, Engine Manager |

---

## Game Rules

### Board & Setup

The game is played on a standard 8×8 grid. Pawns are fixed on the second row for each player and cannot be repositioned at the start. The back row (row 1) allows **custom piece arrangement** — players can place their non-pawn pieces in any order they choose before the game begins. This is the only setup choice available.

### Turn Structure

Each turn allows exactly **one action**. A player must choose between:

1. **Moving a piece** to a valid square.
2. **Buying and placing a trap** (costs 3 coins; consumes the entire turn).
3. **Executing a crown transfer** (once per game; consumes the entire turn).

Actions cannot be combined. Buying a trap and moving a piece in the same turn is not allowed.

### Movement

Pieces move according to standard chess rules with the following simplifications for this version:

- **En passant is not implemented.**
- **Castling is not implemented.**
- **Pawn promotion** automatically promotes to a Queen (no choice required).
- All other piece movements — Pawn, Knight, Bishop, Rook, Queen, King — follow standard chess movement rules exactly.

A move that would place your own king (or crown holder) in check is illegal and cannot be played.

### Economy System

Players earn coins by capturing opponent pieces. Coins are the only currency in the game and are used exclusively to purchase traps.

| Captured Piece | Coins Awarded |
|---------------|--------------|
| Pawn | 1 coin |
| Knight | 2 coins |
| Bishop | 2 coins |
| Rook | 3 coins |
| Queen | 4 coins |

**Important:** Killing a piece with a trap does **not** award coins to the trap owner. Coins are only earned through direct captures via movement.

### Trap System

Traps are hidden deployable hazards that a player can place on empty squares.

**Purchasing and Placement:**
- Each trap costs **3 coins**.
- A player may own a **maximum of 3 traps** total across the entire game (not 3 at once — 3 total ever).
- Traps can only be placed on empty squares within the player's **permitted territory** (see Territory Shrink below).
- Placing a trap consumes the player's entire turn.

**Activation:**
- A trap activates when an opponent's piece moves onto it.
- The piece is immediately destroyed and the trap disappears.
- **The king cannot be killed by a trap.** If the king steps onto a trap, the trap is removed and the king survives.

**Visibility:**
- Traps are always visible to the **owner**.
- Traps are visible to the **opponent's king** when within a 1-square detection radius (the king "senses" nearby traps).
- Traps are invisible to all other opponent pieces.

**Territory Shrink:**
- Trap placement is restricted to the player's own side of the board.
- The permitted zone shrinks every **15 turns** (total turns across both players).
  - Phase 1 (turns 1–14): Player may place traps in their own 4 rows.
  - Phase 2 (turns 15–29): Placement restricted to 3 rows.
  - Phase 3 (turns 30–44): Placement restricted to 2 rows.
  - Phase 4 (turn 45+): Placement restricted to 1 row (home row only).

### Crown Transfer

The crown transfer is a powerful one-time ability that shifts the game's win condition.

**How it works:**
- A player may, once per game, transfer the "crown" (king status) from their current king to another piece they own.
- The **target piece** must be an original, non-pawn piece that was placed at the start of the game and is still alive on the board.
- After the transfer, the **target piece becomes the new king** — it moves like a king, is subject to check and checkmate, and is the win condition.
- The **original king becomes a normal piece** — it retains king-movement range but is no longer the win condition. It can be captured like any other piece.
- The crowned piece **loses its original movement** and gains king movement permanently.

**Restrictions:**
- Cannot be used while the current king is in check.
- Consumes the player's entire turn.
- Can only be used once per game per player.

### Win Condition

The game ends when one player's **current crown holder** is in checkmate — meaning they are in check and have no legal move that removes the check.

The win condition tracks the crown. If a player has used crown transfer, the checkmate target is the new crown holder, not the original king.

### Timer

Each player has **7 minutes** on their personal clock. The clock only runs during that player's turn. If a player's clock reaches zero, they lose immediately regardless of board position.

---

## Proposed Features & Open Discussions

The following features are **proposed additions** that the team and collaborators are actively discussing. None of these are in the current build. Open a GitHub Issue or Discussion thread to vote on or debate any of these.

### Expanded Coin Usage

Currently, coins can only buy traps. Several alternative uses are being considered to make the economy feel more meaningful and create more strategic decisions:

**Option A — Piece Revival:** Spend a fixed number of coins (e.g., 5) to bring back a captured pawn and place it on your back row. This gives pawns indirect strategic value beyond just blocking.

**Option B — Trap Upgrade:** Spend 2 additional coins (total 5) on a "reinforced trap" that requires two activations to destroy rather than one. Useful for defending a critical square.

**Option C — Extra Move:** Spend 4 coins to take a second movement action in the same turn (move one piece twice, or move two different pieces). This would be powerful and needs careful balancing.

**Option D — Intel Purchase:** Spend 2 coins to reveal the exact position of all opponent traps for one turn. Adds a risk/reward dynamic for scouting.

> **Team discussion needed:** Which of these, if any, should make it into Phase 2? Open an Issue labeled `feature/economy` to discuss.

---

### Piece Protection / Shield Mechanic

An idea to introduce a "shield" or "guard" state for pieces:

- A piece standing adjacent to (or directly in front of) a friendly piece would be considered **protected**.
- A protected piece, when targeted by a trap, has a **chance to survive** (either always survives, or requires spending coins to activate the shield).
- Alternatively, a protected piece cannot be the target of certain special abilities.

This mechanic could add depth to formation play and make defense feel more active.

> **Concern:** This significantly increases rule complexity and could be hard to implement within the timeline. Consider pushing to a post-project update. Label with `discussion/balance`.

---

### Other Ideas Under Consideration

**Fog of War (Partial):** Opponent pieces beyond a certain range are hidden until they enter a player's "vision radius" (defined by their king or scout pieces). Very complex to implement — flag for future version.

**Piece Abilities:** Each piece type could have a passive ability triggered on capture or movement. For example, a Rook capturing a piece "pushes" a nearby piece one square. High complexity, interesting strategically.

**Draw Mechanic:** Currently there is no draw condition. Standard chess stalemate rules (no legal moves while not in check = draw) should potentially be included. This is straightforward to add to the rules engine.

**Coin Cap:** To prevent hoarding, a maximum coin limit per player (e.g., 10 coins) could create more active spending decisions.

---

## Rules Open for Modification

These are existing rules that the team has flagged as potentially needing tweaks. Discuss by opening a GitHub Issue with the label `rules/balance`.

| Rule | Current Value | Why It Might Change |
|------|--------------|---------------------|
| Trap cost | 3 coins | May be too cheap if coin economy is expanded |
| Max traps per player | 3 total | Might need to be per-phase rather than per-game |
| Timer duration | 7 minutes | Could feel rushed or too slow depending on playtesting |
| Territory shrink trigger | Every 15 turns | Arbitrary — needs playtesting to validate |
| Crown transfer restriction | Cannot use while in check | Should blocked-from-moving-the-king also block it? |
| Trap kills award no coins | No coins from trap kills | Is this too punishing given trap cost? |
| Pawn promotion | Auto-queen | Should players have a choice? |

---

## Architecture Overview

The project follows an MVC pattern with four clear layers. The `GameController` is the single entry point for all game actions — the GUI, the networking layer, and any test harness all call the same controller method.

```
Engine (core logic — no external dependencies)
  ├── Game.java          — state machine (WAITING, PLAYING, GAME_OVER)
  ├── Board.java         — 8×8 grid, piece placement
  ├── Piece.java         — abstract base, getValidMoves()
  ├── [Piece subclasses] — King, Queen, Rook, Bishop, Knight, Pawn
  ├── RulesEngine.java   — isInCheck(), isCheckmate(), filterLegalMoves()
  ├── Trap.java          — trap placement, activation logic
  ├── Economy.java       — coin tracking, award on capture
  └── Player.java        — player state, timer, crown status

Controller
  └── GameController.java — processAction(Action a): validate → apply → broadcast

View (JavaFX — AI-assisted)
  └── BoardView.java     — renders board state, handles clicks

Networking
  ├── Server.java        — authoritative host, validates all actions
  └── Client.java        — connects to server, sends/receives actions

Shared
  ├── Action.java        — MOVE / PLACE_TRAP / CROWN_TRANSFER + serialize/parse
  └── Position.java      — (row, col) value object
```

---

## Tech Stack

- **Language:** Java 17+
- **GUI:** JavaFX 17
- **Networking:** Java Sockets (TCP, LAN only)
- **Build:** Manual `javac` compilation (no Maven/Gradle required for MVP)
- **Version Control:** Git + GitHub

---

## Getting Started

### Prerequisites

- Java 17 or higher installed
- JavaFX 17 SDK downloaded and placed in `/lib/javafx/`
- Two machines on the same Wi-Fi network (or two terminals on the same machine for testing)

### Running the Server (Host)

```bash
javac -cp src src/networking/Server.java
java -cp src networking.Server
# Server starts on port 5000 — share your local IP with the other player
```

### Running the Client

```bash
javac -cp src src/networking/Client.java
java -cp src networking.Client <host-ip-address>
```

### Running Locally (Both Players on One Machine)

Open two terminal windows. Start the server in one, then connect the client in the other using `localhost` as the IP.

---

## Branching & Contribution Guidelines

### Branch Naming

Use descriptive branch names prefixed by type:

```
feature/trap-system
feature/crown-transfer
fix/checkmate-detection-bug
engine/piece-movement-rook
gui/board-rendering
network/client-sync
```

### Workflow

1. Never push directly to `main`.
2. Create a branch from `main` for your feature or fix.
3. When done, open a **Pull Request** and request a review from at least one other team member.
4. The PR must have no merge conflicts before merging.
5. Delete the branch after merging.

### Commit Messages

Write commit messages in the present tense, describing what the change does:

```
Add getValidMoves() to Rook class
Fix off-by-one in Board.isInBounds()
Connect GameController to BoardView click handler
```

### Issues

Use GitHub Issues to track tasks, bugs, and discussions. Apply labels:

- `engine` — core game logic
- `gui` — JavaFX view work
- `networking` — socket/server work
- `rules/balance` — rule change discussions
- `feature/economy` — coin system expansions
- `bug` — something broken
- `discussion` — open questions needing team input

---

## Roadmap

| Phase | Days | Focus |
|-------|------|-------|
| Phase 1 — Engine | 1–8 | Board, pieces, check/checkmate, game state |
| Phase 2 — Networking | 9–11 | LAN sockets, action sync, server authority |
| Phase 3 — GUI | 12–17 | JavaFX board, click-to-move, timer display |
| Phase 4 — Custom Rules | 18–22 | Traps, economy, crown transfer |
| Phase 5 — Polish | 23–25 | Bug fixing, demo prep, cleanup |

---

> Built with Java by a team of four students as part of a college project. All core game logic is written by the team — no AI-generated engine code.