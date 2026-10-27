# StrataChess — Whiteboard & Viva Guide

Use this to learn the project, not to memorise a script. Be ready to explain the arrows and open the file responsible for each behavior.

## 1. A 30-second introduction

“StrataChess is a two-player chess variant. We kept the familiar board and piece movement, then added mechanics such as the Crown Holder, a coin economy, traps, and Strata Squares. The desktop application is written in Java with JavaFX. The Java engine handles game state and rules, TCP sockets connect host and guest, and a FastAPI service backed by PostgreSQL stores player, game, and move records.”

## 2. The one-minute architecture explanation

“The player interacts with the JavaFX board. The view passes the requested move or special action to `GameController`, which creates an `Action` object. `Game.processAction()` checks the match state and whose turn it is, then uses `RulesEngine` to validate the action. If accepted, the game applies the effects, updates the turn and checks end conditions.

For multiplayer, the action is serialized to text and exchanged over TCP. The host processes the received action on its own game instance. For persistence, Java's `StrataChessAPI` sends HTTP requests to FastAPI. Pydantic checks the request body, Psycopg talks to PostgreSQL, and the database stores the records.”

## 3. Whiteboard: what to draw

Draw these boxes left to right:

1. **Player**
2. **JavaFX UI** — `Main`, `BoardView`
3. **GameController**
4. **Game**
5. Under Game: **Board**, **RulesEngine**, **Player**, **Economy**, **Trap**, **StrataManager**
6. From the controller, a side path to **TCP Server/Client** and the other player's app
7. A persistence path: **StrataChessAPI → FastAPI/Pydantic → Psycopg → PostgreSQL**

Use the main vertical arrow for the action path. Explain that PostgreSQL stores records; it does not decide whether a chess move is legal.

## 4. File map: where to look

| Topic | File |
|---|---|
| Application startup, lobby, object wiring | `src/view/Main.java` |
| Board display and clicks | `src/view/BoardView.java` |
| Coordination across layers | `src/controller/GameController.java` |
| Game lifecycle and action handling | `src/engine/Game.java` |
| Legal moves, check, traps, Crown Transfer | `src/engine/RulesEngine.java` |
| Board representation | `src/engine/Board.java` |
| Piece base and movement classes | `src/engine/Piece.java`, `src/engine/pieces/*.java` |
| Player state | `src/engine/Player.java` |
| Coin costs and Tribute | `src/engine/Economy.java` |
| Strata Square logic | `src/engine/StrataManager.java`, `StrataSquare.java` |
| Trap behavior | `src/engine/Trap.java` |
| Action serialization and positions | `src/shared/Action.java`, `Position.java` |
| Host and guest networking | `src/networking/Server.java`, `Client.java` |
| Java HTTP calls | `src/networking/StrataChessAPI.java` |
| HTTP routes and request models | `backend/main.py` |
| Database connection helper | `backend/database.py` |
| Build and dependencies | `build.gradle` |
| Regression tests | `src/test/java/engine/` |

## 5. Common viva questions

### Why Java?
The game's domain has clear objects—pieces, players, actions, board state, and rules—and Java's object-oriented model fits that structure. It also integrates with JavaFX for the desktop interface.

### Why JavaFX?
It provides the desktop window, board display, and controls while allowing the rules to remain outside the UI.

### What does the controller do?
It receives UI actions, constructs an `Action`, asks the game engine to process it, and coordinates networking and persistence. It should not duplicate the game rules.

### Why separate `Game` and `RulesEngine`?
`Game` coordinates the match and applies accepted effects. `RulesEngine` concentrates on checking whether moves and special actions satisfy the rules. This separation makes rule checks easier to reason about and test.

### What is `Action`?
It represents a requested operation such as moving a piece, placing a trap, or transferring the Crown. It can be serialized for network communication and reconstructed on the other machine.

### Why TCP?
The current host/client game sends small, ordered action messages. TCP gives an ordered byte stream while the connection is active, but application logic still needs to handle validation and state recovery.

### Why send a seed?
Strata Squares are generated randomly. The host sends the seed so both machines can generate the same setup instead of producing different layouts independently.

### Why FastAPI?
It exposes HTTP endpoints for player creation, game creation, and move recording. It also provides interactive API documentation.

### What does Pydantic do?
It checks that incoming request bodies match declared fields and types. It does not decide whether a chess move is legal.

### Why PostgreSQL?
Players, games, and moves are related data. A relational database supports those relationships and can enforce primary-key and foreign-key constraints.

### Is FastAPI the game engine?
No. The Java engine checks the game rules. FastAPI currently provides persistence endpoints.

### What happens if the database is down?
A move may already be accepted in memory while the asynchronous save fails. The current implementation logs the failure; retrying failed writes and showing save status would be improvements.

### Is multiplayer production-ready?
Do not claim that. The guest applies an action locally before the host's result is known, and the current protocol does not show a complete acknowledgement, rollback, and resynchronization mechanism.

### How do you test?
JUnit 5 regression tests cover engine behavior such as board operations, rules, traps, and economy. Only state a passing test count after running the tests on the exact commit being demonstrated.

## 6. Difficult questions — answer honestly

**“Which game state is authoritative?”**  
The host coordinates the match and is the only side that writes move records in the current integration. However, the guest also processes its own action locally before the host's result is known. Without a full acknowledgement and resynchronization flow, the implementation should not be described as a production-grade authoritative system.

**“What happens if the host rejects a guest action?”**  
The current protocol does not demonstrate a complete rejection message that rolls back the guest's local prediction. This is a consistency weakness to improve.

**“What if the move succeeds but the database insert fails?”**  
The game state and persistence request are separate. Since saving is asynchronous, the match can advance while the insert fails. A retry queue and visible persistence status would improve reliability.

**“Can the API be sent an illegal move?”**  
The API validates the request shape and relies on database constraints for record relationships, but it does not run the Java rules engine. Therefore it is not an independent move-validation authority.

**“What about database credentials?”**  
The current connection helper has a hard-coded database connection string. Move credentials to environment variables, keep secrets out of Git, and rotate credentials if they have been exposed.

## 7. The change-review rule: understand before accepting

Before making a code change, use this loop:

1. **State the goal:** what behavior should change and what must stay the same?
2. **Trace the flow:** identify the UI caller, controller method, action type, engine handler, and downstream network/database effects.
3. **Explain the proposal:** list the files to change and why.
4. **Quiz me first:** ask 2–4 questions about the current behavior and proposed design. Wait for my answers before implementing.
5. **Make a focused patch:** avoid unrelated refactoring.
6. **Verify:** run relevant tests/build when possible and report exactly what passed or could not be run.
7. **Review the diff:** explain what changed, what could break, and what remains untested.

If I cannot answer a question, explain the missing concept and ask again. The goal is understanding, not blocking progress.

## 8. Practice round — answer without looking

1. Trace a move from a board click through the controller to the engine.
2. Why should the controller not implement every game rule itself?
3. What exactly is serialized and sent over TCP?
4. How do both clients get the same Strata Square layout?
5. Which component communicates with PostgreSQL?
6. How is validating a JSON body different from validating a legal chess move?
7. Name one state-consistency weakness in the current multiplayer flow.
8. What can go wrong when database writes are asynchronous?
9. Explain the responsibility of three engine classes.
10. If you had one week to improve reliability, what would you fix first and why?

## 9. Final rule

If you cannot explain a class, trace its caller and read its main method before accepting changes to it. Documentation should help you build a mental model; it cannot replace that model.
