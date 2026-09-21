import psycopg
from database import get_connection
from fastapi import FastAPI, HTTPException
from psycopg.rows import dict_row
from pydantic import BaseModel

app = FastAPI(
    title="StrataChess API",
    description="Backend services for managing players, game sessions, and move telemetry.",
    version="1.0.0",
)

# ==============================================================================
# DATA TRANSFER OBJECTS (DTOs / PYDANTIC MODELS)
# ==============================================================================
# Pydantic schemas validate incoming JSON payloads and enforce strict type rules.

class CreatePlayer(BaseModel):
    """Data payload schema for registering a new player."""
    username: str

class CreateGame(BaseModel):
    """Data payload schema for initializing a new game session."""
    white_player_id: int
    black_player_id: int

class CreateMove(BaseModel):
    """Data payload schema for recording a game move."""
    player_id: int
    from_square: str
    to_square: str
    move_number: int


# ==============================================================================
# ROUTE HANDLERS
# ==============================================================================

@app.post("/players", status_code=201)
def create_player(body: CreatePlayer):
    """
    Registers a new player in the system.

    - **username**: Unique handle for the player.
    - **Returns**: Created player record containing system-generated ID and timestamp.
    """
    try:
        # Context Manager (`with`): Automatically closes conn and cur when done.
        # - get_connection(): Opens the raw socket pipe to PostgreSQL.
        # - row_factory=dict_row: Tells the cursor to return a Python dict {"id": 1, ...}
        #   instead of a standard tuple (1, ...).
        with get_connection() as conn, conn.cursor(row_factory=dict_row) as cur:
            
            # NOTE: Always use `%s` for psycopg placeholders regardless of type (str, int).
            # RETURNING yields the inserted row immediately back to cur.
            cur.execute(
                """
                INSERT INTO players (username) 
                VALUES (%s) 
                RETURNING id, username, created_at;
                """,
                (body.username,)  # Parameters must be passed as a tuple!
            )
            
            # Pull the inserted row from the cursor's memory
            row = cur.fetchone()

            if row is None:
                raise HTTPException(status_code=500, detail="Failed to persist player record.")

            # NOTE: PostgreSQL transactions are uncommitted by default.
            # Changes are lost when conn closes if conn.commit() is omitted.
            conn.commit()
            
            return row

    except psycopg.errors.UniqueViolation:
        # Translate DB duplicate key constraint into a standard HTTP 409 Conflict
        raise HTTPException(
            status_code=409, 
            detail=f"Player with username '{body.username}' already exists."
        )


@app.post("/games", status_code=201)
def create_game(body: CreateGame):
    """
    Initializes a new chess game session between two players.

    - **white_player_id**: Foreign key referencing the player playing White.
    - **black_player_id**: Foreign key referencing the player playing Black.
    - **Returns**: Newly created game record initialized with default status.
    """
    try:
        with get_connection() as conn, conn.cursor(row_factory=dict_row) as cur:
            cur.execute(
                """
                INSERT INTO games (white_player_id, black_player_id)
                VALUES (%s, %s) 
                RETURNING id, status, created_at;
                """,
                (body.white_player_id, body.black_player_id)
            )
            row = cur.fetchone()

            if row is None:
                raise HTTPException(status_code=500, detail="Failed to persist game record.")

            conn.commit()
            return row

    except psycopg.errors.ForeignKeyViolation:
        # Thrown when white_player_id or black_player_id does not exist in `players` table
        raise HTTPException(
            status_code=404, 
            detail="One or both referenced player IDs do not exist."
        )


@app.post("/games/{game_id}/moves", status_code=201)
def create_move(game_id: int, body: CreateMove):
    """
    Appends a move event to an active game history log.

    - **game_id**: Path parameter specifying the targeted game session.
    - **body**: Payload containing player action details and move coordinates.
    - **Returns**: Recorded move telemetry including database timestamp.
    """
    try:
        with get_connection() as conn, conn.cursor(row_factory=dict_row) as cur:
            cur.execute(
                """
                INSERT INTO moves (game_id, player_id, from_square, to_square, move_number)
                VALUES (%s, %s, %s, %s, %s) 
                RETURNING id, game_id, player_id, from_square, to_square, move_number, created_at;
                """,
                (game_id, body.player_id, body.from_square, body.to_square, body.move_number)
            )
            row = cur.fetchone()

            if row is None:
                raise HTTPException(status_code=500, detail="Failed to record move entry.")

            conn.commit()
            return row

    except psycopg.errors.ForeignKeyViolation:
        # Thrown when game_id or player_id is missing from their respective parent tables
        raise HTTPException(
            status_code=404, 
            detail="Referenced game or player ID does not exist."
        )
