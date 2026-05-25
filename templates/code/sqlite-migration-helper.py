"""
SQLite progressive migration helper.

Usage:
    def migrate():
        conn = get_db()
        ensure_columns(conn, "users", {
            "avatar": "TEXT DEFAULT ''",
            "display_name": "TEXT DEFAULT ''",
        })
        conn.close()
"""

def ensure_columns(conn, table: str, columns: dict[str, str]):
    """Add columns to table if they do not exist. Idempotent."""
    cur = conn.execute(f"PRAGMA table_info({table})")
    existing = {row[1] for row in cur.fetchall()}
    for col_name, col_def in columns.items():
        if col_name not in existing:
            conn.execute(f"ALTER TABLE {table} ADD COLUMN {col_name} {col_def}")
    conn.commit()
