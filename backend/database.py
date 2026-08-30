import psycopg

DB_URL = (
    "host=localhost "
    "port=5432 "
    "dbname=strata_lab "
    "user=daksh "
    "password=Helis"
)

def get_connection():
    return psycopg.connect(DB_URL)