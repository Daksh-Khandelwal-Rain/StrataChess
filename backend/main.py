from fastapi import FastAPI, HTTPException
from pydantic import BaseModel
from database import get_connection

app = FastAPI()

# ── Request body shapes ───────────────────────────────────────────

class CreatePlayer(BaseModel):
    username:str

class CreateGame(BaseModel):
    white_player_id:int
    black_player_id:int

# ── POST /players ─────────────────────────────────────────────────
@app.post("/players")
def create_player(body:CreatePlayer):
    with get_connection() as conn, conn.cursor() as cur:
        cur.execute(
            "INSERT INTO players (username) VALUES (%s) RETURNING id, username, created_at",
            (body.username,)
        )
        row = cur.fetchone()

        # The crucial fix: check if row is None before indexing
        if row is None:
            raise HTTPException(status_code=500, detail="Database insert failed")

        conn.commit()
        return {"id": row[0], "username": row[1], "created_at": row[2]}

@app.post("/games")
def create_games(body:CreateGame):
    with get_connection() as conn, conn.cursor() as cur:
        cur.execute(
            """INSERT INTO games (white_player_id, black_player_id)
                   VALUES (%s, %s) RETURNING id, status, created_at""",
            (body.white_player_id, body.black_player_id)
        )
        row = cur.fetchone()

        if row is None:
            raise HTTPException(status_code=500, detail="Database insert failed")
        
        conn.commit()
        return {"id: row[0]"}