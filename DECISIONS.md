# StrataChess — Decisions & Architecture Rationale

> Based on the repository's current `main` branch. This explains the design and trade-offs, not just the technology names. Verify behavior against the exact commit you demonstrate.

## 1. System in one sentence

StrataChess is a Java/JavaFX chess variant with custom game rules, TCP host/client networking, and a separate FastAPI service that stores player, game, and move records in PostgreSQL.

## 2. Main boundaries

- **Game rules and live match state:** Java engine.
- **Player interaction:** JavaFX view plus `GameController`.
- **Player-to-player messages:** TCP sockets.
- **Persistent records:** Java HTTP client → FastAPI → Psycopg → PostgreSQL.

The backend is a persistence API. It is not a replacement for the Java rules engine and should not be described as a complete authentication or authoritative online-game service.

## 3. Why these technologies?

| Choice | Reason in this project | Trade-off |
|---|---|---|
| Java | Object-oriented model for pieces, players, actions, board state, and rules. | More explicit classes and coordination. |
| JavaFX | Desktop UI for the lobby, board, controls, and feedback. | UI work must run on the JavaFX Application Thread. |
| Gradle | Build and dependency management through the wrapper. | The project uses `src/` as a custom source root, configured in `build.gradle`. |
| Custom engine | Crown Holder, traps, Tribute, coins, and Strata Squares go beyond standard chess. | Interactions between special rules need regression tests. |
| `Action` objects | One representation for moves, trap placement, and Crown Transfer; can be serialized for networking. | Both peers must agree on the message format. |
| TCP sockets | Small, ordered messages suit a turn-based host/client game. | Reconnection, acceptance, authorization, and resynchronization are application responsibilities. |
| FastAPI + Pydantic | HTTP endpoints with request-body validation and interactive docs. | Validating JSON shape does not validate chess legality. |
| PostgreSQL + Psycopg | Relational player, game, and move records with keys and constraints. | Credentials, schema, transactions, and failure handling require care. |
| JUnit 5 | Repeatable tests for engine behavior and regressions. | Test files do not prove tests currently pass. |

## 4. Decisions worth defending in a viva

### 4.1 Keep rules out of the UI

`BoardView` collects interaction; `GameController` coordinates it; `Game`, `Board`, `RulesEngine`, `Player`, `Economy`, `Trap`, and `StrataManager` implement game behavior.

**Why:** if the UI decides legality, a second UI or network path could implement the rules differently. A central rules path is easier to test.

**Viva answer:** “The view handles interaction, the controller coordinates the request, and the engine decides the rules.”

### 4.2 Represent requests as `Action` objects

`shared.Action` supports `MOVE`, `PLACE_TRAP`, and `CROWN_TRANSFER`. It can serialize an action to a string and reconstruct it on the other machine.

**Why:** the network needs a compact description of the requested action; it does not need the entire Java object graph.

**Trade-off:** this is a simple custom text protocol, not a versioned or authenticated protocol.

### 4.3 Separate live state from stored history

The Java engine keeps the current match in memory. The backend stores players, games, and move/action records.

**Why:** the database provides durable history, but it should not decide whether an action follows the game rules.

**Trade-off:** the current project does not demonstrate a complete replay/recovery system that can reconstruct any live match from stored events.

### 4.4 Use TCP for desktop multiplayer

`Server` listens on port 5000; `Client` connects to the host. The host sends a `SEED:` message so both instances can generate the same Strata Square layout.

**Why:** the game sends small action messages in turns, so TCP is a reasonable simple transport.

**Trade-off:** TCP preserves order while connected; it does not by itself guarantee that the two game states remain equal.

### 4.5 Share the Strata Square seed

The host generates and applies a seed, then sends it before ordinary actions. The guest uses the same seed.

**Why:** separate random generation could place Strata Squares differently on each computer.

**Trade-off:** both peers must use compatible generation logic. A future protocol could version the rules or send the generated layout explicitly.

### 4.6 Use FastAPI as a persistence boundary

`StrataChessAPI.java` sends HTTP requests. FastAPI validates request models, Psycopg communicates with PostgreSQL, and the database stores records.

**Why:** Java gameplay code does not need to contain SQL or manage PostgreSQL connections directly.

**Trade-off:** the API is another process that can fail independently of the game.

### 4.7 Save moves asynchronously

The host's controller starts a background thread to record a move rather than blocking the normal interaction path.

**Why:** slow persistence requests should not freeze the game UI.

**Trade-off:** a move can be accepted in memory but fail to save. Current code logs failures; a retry queue and visible save status would improve reliability.

## 5. Limitations to state honestly

1. The guest processes its action locally before the host's result is known. The current protocol does not show a complete reject/rollback/resynchronization flow.
2. Database writes are asynchronous; a valid move and a successfully persisted move are different outcomes.
3. FastAPI validates payload shape and database relationships, not the legality of chess actions.
4. The custom TCP protocol is not documented as encrypted or authenticated.
5. The database connection configuration currently includes hard-coded credentials. Move them to environment variables, keep secrets out of Git, and rotate any credentials that may have been exposed. Do not reproduce secret values in docs.
6. Verify standard chess-rule coverage against the implementation before claiming every rule is supported.
7. Report test counts only after running the suite on the exact submission commit.

## 6. Whiteboard summary

Draw:

```text
Player → JavaFX View → GameController → Java Game Engine
                              |                  |
                              |                  +-- Board / RulesEngine
                              |                  +-- Player / Economy
                              |                  +-- Trap / StrataManager
                              |
                              +--> TCP Server/Client <--> Opponent
                              |
                              +--> Java HTTP Client
                                       ↓
                                 FastAPI + Pydantic
                                       ↓
                                    Psycopg
                                       ↓
                                  PostgreSQL
```

Explain the main flow first, then the side paths. PostgreSQL stores history; it does not decide whether a move is legal.

**One-line answer:** “StrataChess keeps the rules in a Java engine, uses JavaFX for the desktop UI, TCP for desktop multiplayer, and FastAPI/PostgreSQL for persistence.”
