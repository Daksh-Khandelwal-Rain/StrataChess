# StrataChess

**A tactical chess variant built around territory, traps, resource management, and strategic risk.**

StrataChess expands traditional chess with a dynamic battlefield. Players must do more than calculate moves: they must manage their coins, control territory, exploit Strata Squares, deploy traps, and protect their Crown Holder.

The project combines a custom Java game engine, a JavaFX client, TCP-based multiplayer networking, and a FastAPI backend backed by PostgreSQL.

> **Development status:** Active development. Core game mechanics, multiplayer networking, and database integration are being developed incrementally. The web frontend and account-related features are planned work, not current capabilities.

---

## Table of Contents

- [Overview](#overview)
- [Game Mechanics](#game-mechanics)
  - [The Crown Holder](#the-crown-holder)
  - [Economy and Tribute](#economy-and-tribute)
  - [Strata Squares](#strata-squares)
  - [Trap System](#trap-system)
  - [Crown Transfer](#crown-transfer)
- [Architecture](#architecture)
- [Technology Stack](#technology-stack)
- [Getting Started](#getting-started)
- [Running the Backend](#running-the-backend)
- [API Overview](#api-overview)
- [Testing](#testing)
- [Project Structure](#project-structure)
- [Roadmap](#roadmap)

---

## Overview

Traditional chess rewards positional understanding, tactical calculation, and long-term planning. StrataChess preserves those foundations while introducing mechanics that create additional strategic decisions.

Players must balance three competing priorities:

- **Tactical superiority:** Develop pieces, capture opponents, and create threats.
- **Economic management:** Earn and spend coins efficiently.
- **Territorial control:** Use Strata Squares and strategically place traps within permitted territory.

Every resource has an opportunity cost. Spending coins on a trap, for example, means those coins cannot be used for another strategic action.

## Game Mechanics

### The Crown Holder

Each player has a designated **Crown Holder**, which is central to the game's king-safety and special-action mechanics.

- The Crown Holder is tracked independently of the underlying piece type.
- Its position is relevant to check detection and legal-action validation.
- The Crown Holder receives a special reward when collecting a Strata Square.
- It can survive an enemy trap that would normally eliminate a piece.

The Crown Holder should not be confused with an ordinary King: Crown Holder status is a separate gameplay property.

### Economy and Tribute

StrataChess has two intended sources of income:

1. **Tribute**
2. **Strata Squares**

There is no capture bounty or passive income in the current economy design.

#### Tribute

When a piece is lost in a normal capture, the player who lost that piece receives Tribute. The capturing player does not receive a capture bounty.

| Piece lost | Standard value | Tribute received |
|---|---:|---:|
| Pawn | 1 | 1 coin |
| Knight | 3 | 2 coins |
| Bishop | 3 | 2 coins |
| Rook | 5 | 3 coins |
| Queen | 9 | 5 coins |
| King | 0 | 0 coins |

Tribute is calculated as the ceiling of half the captured piece's standard value.

Crown Holder status does not change Tribute. The reward is based on the underlying piece type. Trap eliminations do not award Tribute.

#### Spending coins

Coins are used to fund special actions, including:

- **Trap placement:** 3 coins.
- **Crown Transfer:** 5 coins.

Players must weigh immediate tactical advantages against the cost of future opportunities.

### Strata Squares

Strata Squares are limited-use resource locations placed on the board.

A game contains three Strata Squares:

- One private to White.
- One private to Black.
- One shared between both players.

Each square has three available charges. A square disappears after its third successful collection.

#### Visibility

- White can see White's private square and the shared square.
- Black can see Black's private square and the shared square.
- A player's private square is not visible to the opponent through the normal game view.

#### Rewards

| Piece entering the square | Reward |
|---|---:|
| Knight, Bishop, Rook, or Queen | +1 coin |
| Crown Holder, regardless of underlying piece type | +2 coins |
| Pawn or non-Crown Holder King | 0 coins |

A reward is triggered by entering a square, not by remaining on it. Each successful collection consumes one charge. The opponent can collect any charges that remain on a shared square.

A normal capture and a Strata Square collection can both award their respective effects when a move satisfies both conditions.

For multiplayer consistency, the host supplies a random seed so both clients can generate the same Strata Square layout.

### Trap System

Traps are tactical hazards that punish careless movement and can influence how players approach contested territory.

#### Placement rules

- Each trap costs 3 coins.
- Each player has a maximum of three lifetime trap deployments.
- A trap cannot be placed on a square occupied by a piece.
- Placement is restricted to the player's permitted territory.
- An invalid placement should not consume coins, a deployment, or a turn.

Trap territory is designed to expand as a player completes turns. The exact rank-by-rank progression is still being verified against the current implementation.

#### Trap interactions

| Interaction | Intended result |
|---|---|
| An ordinary piece enters an enemy trap | The piece is eliminated and the trap disappears. |
| The Crown Holder enters an enemy trap | The Crown Holder survives and the trap disappears. |
| A player's piece enters its own trap | The piece survives and the trap remains. |
| A player attempts to place a trap on their own trap | The placement is invalid. |
| A player places a trap on an enemy trap | A valid trap-collision action; the existing trap is destroyed and the action consumes its cost and deployment. |

Trap eliminations do not award Tribute.

#### Trap visibility

Trap information is intentionally asymmetric:

- A player can always see their own traps.
- An opponent's trap can be revealed to the Crown Holder when it is within one square in any direction, including diagonally.
- Other enemy pieces do not reveal traps through this rule.

This creates a scouting and information-management component in addition to the direct tactical effect of traps.

### Crown Transfer

Crown Transfer is a special, one-time action that moves Crown Holder status to another eligible friendly piece.

Rules include:

- The action costs 5 coins.
- It can be used only once per player.
- The target must be a friendly piece.
- Pawns and the current Crown Holder are ineligible targets.
- The action cannot be used while the player is in check.

Crown Transfer allows a player to change which piece carries the Crown Holder role, creating an additional layer of tactical and positional planning.

---

## Architecture

StrataChess separates the game rules from the interface, networking, and persistence layers.

### Game engine

The Java engine contains the core game model and rules:

- Board and piece representation.
- Move validation and king-safety checks.
- Trap placement and trap interactions.
- Economy and Tribute calculations.
- Strata Square generation and collection.
- Crown Holder tracking and Crown Transfer.
- Turn and game-state management.

The engine is intended to remain independent of the user interface and database. Game rules should be validated by the engine rather than trusted to client-side presentation code.

### Client and networking

The current client uses JavaFX.

The multiplayer implementation uses TCP networking, with a host/client model for exchanging game actions. The host is responsible for coordinating the match and its associated database writes in the current integration.

A seeded Strata Square setup helps ensure both clients begin with a consistent board configuration.

The planned HTML/CSS/JavaScript frontend will require a browser-compatible networking layer, such as WebSockets, rather than connecting directly to the existing raw TCP server.

### Backend and persistence

The backend uses FastAPI to expose HTTP endpoints for player registration, game creation, and move-history recording.

PostgreSQL stores persistent records, separating match history from the in-memory game state.

The intended responsibility split is:

- **Java engine:** Validates and applies game actions.
- **Client:** Displays the game and sends player actions.
- **Networking layer:** Exchanges actions between players.
- **FastAPI:** Provides HTTP access to persistent game data.
- **PostgreSQL:** Stores player, game, and move records.

The current API is a persistence layer, not a complete authentication or authoritative online-game service.

---

## Technology Stack

| Layer | Technology |
|---|---|
| Language | Java |
| Desktop interface | JavaFX |
| Build system | Gradle |
| Multiplayer transport | TCP sockets |
| Backend framework | FastAPI |
| Backend language | Python |
| Database | PostgreSQL |
| Backend database driver | Psycopg |
| API validation | Pydantic |
| Testing | JUnit 5 |

---

## Getting Started

### Prerequisites

Install the following:

- Git
- JDK 17 or later
- Python compatible with the backend dependencies
- PostgreSQL, if you want to use database-backed features

The Gradle wrapper downloads the required Gradle distribution and the configured JavaFX dependencies. A separate manual JavaFX SDK installation should not be necessary for the Gradle workflow.

### Clone the repository

```bash
git clone https://github.com/Daksh-Khandelwal-Rain/StrataChess.git
cd StrataChess
```

### Run the JavaFX client

**Windows PowerShell:**

```powershell
.\gradlew.bat run
```

**macOS/Linux:**

```bash
./gradlew run
```

### Build the project

**Windows PowerShell:**

```powershell
.\gradlew.bat build
```

**macOS/Linux:**

```bash
./gradlew build
```

The project targets Java 17 bytecode through Gradle and uses JavaFX 17.0.13.

---

## Running the Backend

The FastAPI application is located in `backend/`.

### 1. Configure PostgreSQL

Create and configure a PostgreSQL database for the project. Ensure that the database contains the tables expected by the API:

- `players`
- `games`
- `moves`

The API expects these tables to support player registration, game creation, and move-history insertion.

**Configuration note:** The current database connection settings are defined in the backend source. Before sharing or deploying the backend, move database credentials into environment variables and ensure secrets are not committed to Git.

### 2. Install backend dependencies

From the repository root, enter the backend directory:

```powershell
cd backend
```

Install the dependencies used by the application in your chosen Python environment:

```powershell
python -m pip install fastapi "uvicorn[standard]" psycopg pydantic
```

### 3. Start the API

```powershell
uvicorn main:app --reload
```

The local API should be available at:

```text
http://127.0.0.1:8000
```

Interactive API documentation is available at:

```text
http://127.0.0.1:8000/docs
```

The backend requires a working PostgreSQL connection before database-backed requests can succeed.

---

## API Overview

The current backend exposes the following core operations:

| Method | Endpoint | Purpose |
|---|---|---|
| `POST` | `/players` | Register a player using a username. |
| `POST` | `/games` | Create a game using White and Black player IDs. |
| `POST` | `/games/{game_id}/moves` | Record a move or action event for a game. |

The move-recording payload includes the player ID, source square, destination square, move number, and move type.

For the exact request and response schemas, validation behavior, and error responses, use the interactive documentation at `/docs`.

### Current limitations

The current API should not be treated as a complete online-game backend. Authentication, session management, authorization, and a full game lifecycle API are not yet documented as implemented capabilities. The API also does not replace the Java engine's responsibility for validating game rules.

---

## Testing

Run the automated Java tests with the Gradle wrapper.

**Windows PowerShell:**

```powershell
.\gradlew.bat clean test
```

**macOS/Linux:**

```bash
./gradlew clean test
```

The test suite covers engine behavior and regression-sensitive mechanics, including board operations, move rules, traps, and economy calculations.

When changing game rules, add or update regression tests so the implementation remains consistent with the intended rules.

---

## Project Structure

The repository currently uses a non-standard Java source layout, with package directories directly under `src/`.

```text
StrataChess/
├── src/
│   ├── engine/          # Board, pieces, rules, economy and game state
│   ├── controller/      # Game coordination and client-side orchestration
│   ├── networking/      # TCP networking and API integration
│   ├── view/            # JavaFX interface
│   ├── shared/          # Shared action and position models
│   └── test/            # Regression and engine tests
├── backend/
│   ├── main.py          # FastAPI application and endpoints
│   └── database.py      # PostgreSQL connection helper
├── gradlew
├── gradlew.bat
├── build.gradle
└── README.md
```

Some implementation details may evolve as the engine, backend, and frontend are refactored.

---

## Roadmap

The project is being developed incrementally.

### Game engine

- Verify and stabilize the revised trap-territory progression.
- Expand regression coverage for special actions and edge cases.
- Keep rule validation centralized in the engine.

### Backend and persistence

- Move database configuration and credentials into environment variables.
- Align API payloads, database schema, and client integration.
- Introduce a complete game lifecycle and action-history API.
- Add authentication, sessions, and appropriate authorization controls.

### Frontend and multiplayer

- Develop the planned HTML/CSS/JavaScript frontend.
- Introduce a browser-compatible networking transport.
- Preserve consistent game state between players.
- Build controlled access to match history and replays.

### Quality and maintainability

- Document the architecture and game rules in dedicated documentation.
- Keep tests aligned with the implemented rules.
- Separate gameplay changes from infrastructure and documentation changes.

---

## Contributing

StrataChess is actively evolving. Before contributing:

1. Understand the existing engine and the relevant game rules.
2. Keep unrelated game logic, frontend, and backend changes separate.
3. Add regression tests when changing engine behavior.
4. Run the test suite before submitting changes.
5. Use a feature branch and submit a pull request rather than pushing experimental work directly to `main`.

