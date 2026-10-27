# StrataChess — Decisions & Architecture Rationale

> Based on the current `main` branch. This records the reasoning behind the current design and identifies limitations honestly.

## System summary

StrataChess is a Java desktop chess variant with a JavaFX UI, custom game engine, TCP host/client networking, and a separate FastAPI service that stores player, game, and move records in PostgreSQL.

- **Java engine:** game state, legality checks, special rules, turn progression, end conditions.
- **JavaFX:** board display and player input.
- **GameController:** converts input into actions and coordinates engine, network, and persistence.
- **TCP:** exchanges serialized actions between host and guest.
- **FastAPI:** HTTP endpoints for player/game/move records.
- **PostgreSQL:** persistent records, not the game-rules authority.

## Why these technologies?

| Choice | Reason | Trade-off |
|---|---|---|
| Java | Object-oriented model suits pieces, players, actions, board state and rules. | Requires explicit class design. |
| JavaFX | Desktop interface with controls and board rendering. | UI work must use the JavaFX Application Thread. |
| Gradle | Build and dependency management; wrapper makes commands repeatable. | `build.gradle` sets `src/` as source root because this repo does not use `src/main/java`. |
| Custom engine | Crown Holder, traps, Tribute, coins and Strata Squares go beyond standard chess. | Interactions need regression tests. |
| `Action` objects | Represent moves/abilities as data and serialize them for networking. | Both ends must agree on the message format. |
| TCP sockets | Simple ordered transport for small turn-based messages. | Reconnection, authorization, acknowledgements and resync are application responsibilities. |
| FastAPI + Pydantic | HTTP routes and request-body validation. | Payload validation is not chess-rule validation. |
| PostgreSQL + Psycopg | Relational records and foreign-key relationships. | Schema, credentials, transactions and errors require care. |
| JUnit 5 | Repeatable engine regression tests. | Test files do not prove that tests pass. |

## Decisions that matter

### Keep UI and rules separate
`BoardView` displays the board and collects clicks. `GameController` coordinates requests. `Game` manages match progression and applies effects; `RulesEngine` evaluates legality. `Board`, `Piece`, `Player`, `Economy`, `Trap`, and `StrataManager` model the game domain.

**Reason:** putting legality in the UI risks different rules in other interfaces or network paths. Central rule checks are easier to test.

### Represent requests as `Action`
`shared.Action` supports `MOVE`, `PLACE_TRAP`, and `CROWN_TRANSFER`. It serializes an action to a delimited string and reconstructs it on the receiver.

**Reason:** networking needs a compact description of the requested operation, not a copy of the whole board/object graph. **Trade-off:** this is a small custom text protocol, not a versioned or authenticated protocol.

### Keep live state in memory; persist history separately
Java's `Game` represents the live match. FastAPI/PostgreSQL store player, game and move/action records.

**Reason:** the database provides durable history; it should not decide whether a move is legal. The current repo does not demonstrate complete match replay/recovery from stored events.

### Use TCP for current desktop multiplayer
`Server` listens on port `5000`; `Client` connects to the host. The host sends a `SEED:` message before action messages.

**Reason:** the game exchanges small turn-based actions. A shared seed makes both machines generate the same Strata Square setup. TCP does not itself solve application-level acceptance, reconnection, authentication or resynchronization.

### Keep persistence behind HTTP
`src/networking/StrataChessAPI.java` calls FastAPI. `backend/main.py` defines routes and Pydantic models; `backend/database.py` provides the Psycopg connection helper.

**Reason:** Java gameplay code does not need SQL or direct PostgreSQL connections. Move recording is asynchronous, so the match can accept an action even if the later save fails; current behavior logs failures rather than retrying durably.

## Limitations to state honestly

1. **Guest consistency:** the guest processes its own action locally before sending it. The host processes the received action too, but the current protocol does not demonstrate complete accept/reject acknowledgement and rollback/resynchronization. Do not call this production-grade authoritative multiplayer.
2. **Persistence is separate from move acceptance:** asynchronous DB recording can fail after the action is accepted.
3. **FastAPI is not the rules engine:** it validates payload shape and database references, not move legality.
4. **Security needs work:** `backend/database.py` contains a hard-coded connection string. Move secrets to environment variables and rotate any credentials that were exposed. Do not copy secrets into docs.
5. **TCP protocol is simple:** it is not documented as encrypted or authenticated.
6. Verify standard rules such as en passant against the code before claiming full chess-rule support.
7. Only report test pass counts after running the current branch's suite.

## Whiteboard explanation

Draw:

```text
Player → JavaFX View → GameController → Java Game Engine
                           |                 |
                           |                 +-- Board / RulesEngine / Player
                           |                     Economy / Trap / StrataManager
                           +→ TCP Server/Client ↔ Opponent app
                           +→ StrataChessAPI → FastAPI/Pydantic → Psycopg → PostgreSQL
```

Narrate: player requests action → controller builds `Action` → engine validates/applies → TCP exchanges action → host attempts persistence → PostgreSQL stores history, not game legality.

**One sentence:** “StrataChess keeps rules in a Java engine, uses JavaFX for the desktop UI, TCP for desktop multiplayer, and FastAPI/PostgreSQL for persistence.”
