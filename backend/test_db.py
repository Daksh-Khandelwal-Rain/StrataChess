from database import get_connection

with get_connection() as conn, conn.cursor() as cur:
        cur.execute("SELECT current_user, current_database();")
        print(cur.fetchone())