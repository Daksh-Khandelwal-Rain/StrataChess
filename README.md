# ♟️ StrataChess

> A chess-inspired, LAN-based two-player strategy game built in Java.  
> StrataChess extends classical chess with an **economy system**, **deployable traps**, and a **one-time crown transfer** mechanic — fundamentally reshaping how the endgame is played.

---

## 🧠 Why This Project Exists

StrataChess is not just a game — it is a **learning vehicle**. Every file in this repository is written to teach a specific concept in software engineering:

| File / Layer | Concept You Will Learn |
|---|---|
| `Position.java` | Value Objects and immutability |
| `Action.java` | Serialization and the Command Pattern |
| `Piece.java` | Abstract classes and polymorphism |
| `Board.java` | 2D arrays and coordinate systems |
| `RulesEngine.java` | Pure functions and separation of concerns |
| `Game.java` | Finite State Machines |
| `GameController.java` | The MVC Controller — single entry point |
| `BoardView.java` | Event-driven GUI programming + animations |
| `Server.java / Client.java` | TCP sockets and networked applications |

Read the comments inside each file. They explain *why* a pattern was chosen before explaining *how* it works.

---

## 📁 Project Structure

```
StrataChess/
├── README.md                  ← You are here
├── .gitignore                 ← Tells Git which files to ignore
├── compile.sh                 ← One-command build script
├── run_server.sh              ← Start the game as host
├── run_client.sh              ← Join a game as guest
├── assets/
│   └── music/
│       └── README.md          ← Where to place your .mp3 music file
└── src/
    ├── shared/
    │   ├── Position.java      ← (row, col) coordinate — used everywhere
    │   └── Action.java        ← Represents one player action (move/trap/crown)
    ├── engine/
    │   ├── Piece.java         ← Abstract base class for all pieces
    │   ├── pieces/
    │   │   ├── King.java
    │   │   ├── Queen.java
    │   │   ├── Rook.java
    │   │   ├── Bishop.java
    │   │   ├── Knight.java
    │   │   └── Pawn.java
    │   ├── Board.java         ← 8×8 grid, holds pieces and traps
    │   ├── Trap.java          ← Trap placement, activation, visibility
    │   ├── Economy.java       ← Coin tracking and awards on capture
    │   ├── RulesEngine.java   ← Check, checkmate, legal move filtering
    │   ├── Player.java        ← Player state, timer, crown status
    │   └── Game.java          ← State machine coordinating everything
    ├── controller/
    │   └── GameController.java ← The single entry point for all game actions
    ├── view/
    │   ├── Main.java          ← JavaFX Application entry point
    │   ├── BoardView.java     ← Renders the board, handles clicks, animations
    │   └── MusicPlayer.java   ← Background music manager
    └── networking/
        ├── Server.java        ← Authoritative host — validates all actions
        └── Client.java        ← Connects to host, sends and receives actions
```

---

## 🎮 Game Rules

### Board & Setup

The game is played on a standard **8×8 grid**. Pawns start fixed on row 2 for each player. The back row (row 1) allows **custom piece arrangement** — you can place your non-pawn pieces in any order before the game begins.

### Turn Structure

Each turn allows exactly **one action**. Choose between:

1. **Moving a piece** to a valid square.
2. **Buying and placing a trap** (costs 3 coins — uses your whole turn).
3. **Executing a crown transfer** (once per game — uses your whole turn).

### Movement

Pieces follow **standard chess movement rules** with these simplifications:

- En passant is **not** implemented.
- Castling is **not** implemented.
- Pawn promotion **automatically** promotes to a Queen.
- All other movement (Pawn, Knight, Bishop, Rook, Queen, King) follows standard chess exactly.

A move that places your own crown holder in check is **illegal**.

### Economy System

Earn coins by capturing opponent pieces:

| Captured Piece | Coins Awarded |
|---|---|
| Pawn | 1 coin |
| Knight | 2 coins |
| Bishop | 2 coins |
| Rook | 3 coins |
| Queen | 4 coins |

> Killing a piece with a trap does **not** award coins. Coins come only from direct captures.

### Trap System

- Each trap costs **3 coins**.
- You may own a **maximum of 3 traps** total across the entire game.
- Traps can only be placed on **empty squares in your territory** (see Territory Shrink below).
- A trap **destroys** any opponent piece that steps on it — except the king (who survives but destroys the trap).
- Traps are **visible to you** and **visible to the opponent's king** within a 1-square radius. All other opponent pieces cannot see them.

**Territory Shrink** — placement zones tighten every 15 total turns:

| Phase | Total Turns | Rows Available |
|---|---|---|
| 1 | 1–14 | 4 rows (your half) |
| 2 | 15–29 | 3 rows |
| 3 | 30–44 | 2 rows |
| 4 | 45+ | 1 row (home row only) |

### Crown Transfer

Once per game, you may transfer the "crown" (the win-condition status) from your King to another non-pawn original piece:

- The **target piece** becomes the new King — it moves like a King, is subject to check/checkmate, and is the win target.
- The **original King** becomes a normal piece — still moves like a King, but can be captured like any other piece.
- Cannot be used while **currently in check**.
- Costs your entire turn.

### Win Condition

The game ends when the **current crown holder** is in checkmate.

### Timer

Each player has **7 minutes** on their personal clock. The clock only ticks on your turn. Reach zero and you lose immediately.

---

## 🏗️ Architecture: Why MVC?

This project uses **Model-View-Controller (MVC)** — one of the most widely-used patterns in software engineering. Here is why:

**The problem MVC solves:** Imagine writing your game so that the board-drawing code also contains the checkmate logic, which also talks to the network. When you want to fix a bug in checkmate detection, you are wading through GUI rendering code. This is called **tight coupling** — and it makes projects unmaintainable.

**The MVC solution:**
- The **Model** (engine layer) knows nothing about graphics or networking. It just manages game state.
- The **View** (BoardView) knows nothing about rules. It just draws whatever state it is given.
- The **Controller** (GameController) is the translator — it receives an action, asks the Model to validate it, then tells the View to update.

The result: you can replace the entire GUI with a terminal interface by only changing the View. The engine never needs to change.

---

## 🔌 Tech Stack

| Technology | Why We Chose It |
|---|---|
| **Java 17** | Strong OOP support, widely taught, large standard library |
| **JavaFX 17** | Native Java GUI — no external frameworks needed |
| **Java Sockets (TCP)** | Simple, reliable LAN communication |
| **Manual javac** | Forces understanding of compilation — no build tool magic |

---

## 🚀 Getting Started

### Prerequisites

- Java 17+ installed (`java --version` to check)
- JavaFX 17 SDK downloaded and placed at `lib/javafx/`
- Two players on the same Wi-Fi network (or two terminals on one machine)

### One-Command Build

```bash
chmod +x compile.sh run_server.sh run_client.sh
./compile.sh
```

### Start the Server (Host)

```bash
./run_server.sh
# Prints your local IP — share it with the other player
```

### Join as Client

```bash
./run_client.sh <host-ip-address>
# Use "localhost" if both players are on the same machine
```

---

## 🎵 Music

StrataChess plays **epic orchestral background music** with a GoT-style vibe. To add music:

1. Find a royalty-free epic orchestral `.mp3` (see `assets/music/README.md` for suggestions).
2. Place it at `assets/music/theme.mp3`.
3. The `MusicPlayer.java` will automatically detect and loop it.

---

## 🗺️ Roadmap

| Phase | Days | Focus |
|---|---|---|
| Phase 1 — Engine | 1–8 | Board, pieces, check/checkmate, game state |
| Phase 2 — Networking | 9–11 | LAN sockets, action sync, server authority |
| Phase 3 — GUI | 12–17 | JavaFX board, click-to-move, timer display |
| Phase 4 — Custom Rules | 18–22 | Traps, economy, crown transfer |
| Phase 5 — Polish | 23–25 | Bug fixing, demo prep, cleanup |

---

## 🌿 Branching & Contribution

- Never push directly to `main`.
- Branch naming: `feature/trap-system`, `fix/checkmate-bug`, `engine/rook-movement`
- Open a Pull Request and get one review before merging.
- Write commit messages in present tense: `Add getValidMoves() to Rook`

---

> Built with Java as a college project. Core game logic written by the team — no AI-generated engine code.  
> Comments and architecture guidance assisted by Claude (Anthropic).