#!/usr/bin/env python3
"""Smoke-test SQL extracted from actual generated Room migrations 16->22."""
import argparse
import re
import sqlite3
from pathlib import Path

def extract_migrations(src):
    pattern = re.compile(
        r'val MIGRATION_(\d+)_(\d+) = object : Migration\(\1, \2\) \{\s+'
        r'override fun migrate\(db: SupportSQLiteDatabase\) \{(.*?)\n            \}\n        \}',
        re.S,
    )
    blocks = {}
    for hit in pattern.finditer(src):
        pair = (int(hit.group(1)), int(hit.group(2)))
        sql = []
        for raw, trivial in re.findall(
            r'db\.execSQL\(\s*(?:"""(.*?)"""|"([^"]+)")', hit.group(3), re.S
        ):
            sql.append((raw or trivial).strip())
        if not sql:
            raise AssertionError(f'No SQL in migration {pair}')
        blocks[pair] = sql
    return blocks

def run(src):
    steps = extract_migrations(src)
    assert all((i, i + 1) in steps for i in range(16, 22)), list(steps)
    db = sqlite3.connect(':memory:')
    db.execute('PRAGMA foreign_keys=ON')
    db.executescript("""
        CREATE TABLE companions(id INTEGER PRIMARY KEY, name TEXT NOT NULL);
        CREATE TABLE conversations(id INTEGER PRIMARY KEY, companionId INTEGER NOT NULL, title TEXT NOT NULL);
        CREATE TABLE messages(
            id INTEGER PRIMARY KEY, conversationId INTEGER NOT NULL,
            role TEXT NOT NULL, content TEXT NOT NULL, createdAt INTEGER NOT NULL,
            FOREIGN KEY(conversationId) REFERENCES conversations(id) ON DELETE CASCADE
        );
        INSERT INTO companions VALUES(1,'阿弦'),(2,'小艺');
        INSERT INTO conversations VALUES(5,1,'群聊原始记录');
        INSERT INTO messages VALUES(20,5,'user','这条必须保留',111);
        PRAGMA user_version=16;
    """)
    for a in range(16, 22):
        with db:
            for statement in steps[(a, a + 1)]:
                db.execute(statement)
            db.execute(f'PRAGMA user_version={a + 1}')
        assert db.execute('PRAGMA user_version').fetchone()[0] == a + 1
        assert db.execute('SELECT content FROM messages WHERE id=20').fetchone() == ('这条必须保留',)
        if a == 17:
            db.execute('UPDATE conversations SET isGroup=1 WHERE id=5')
            db.execute('INSERT INTO conversation_members(conversationId,companionId,position) VALUES(5,1,0),(5,2,1)')
            db.execute('UPDATE messages SET senderCompanionId=2 WHERE id=20')
        assert db.execute('PRAGMA foreign_key_check').fetchall() == [], f'FK failed in {a}->{a+1}'
        print(f'SQL upgrade {a}->{a+1}: passed; retained chat message')
    columns = {row[1] for row in db.execute('PRAGMA table_info(conversations)')}
    required = {'isGroup', 'pinned', 'groupMode', 'groupShareOutside',
                'groupAvatarEmoji', 'groupTotalCalls', 'groupTextInputChars', 'groupTextOutputChars'}
    assert required.issubset(columns)
    assert db.execute('SELECT groupAvatarEmoji,groupTotalCalls,groupCallsToday FROM conversations WHERE id=5').fetchone() == ('👥', 0, 0)
    assert db.execute('SELECT companionId,position FROM conversation_members ORDER BY position').fetchall() == [(1,0),(2,1)]
    assert db.execute('SELECT senderCompanionId,mentionedCompanionIds FROM messages WHERE id=20').fetchone() == (2,None)
    assert db.execute('PRAGMA integrity_check').fetchone()[0] == 'ok'
    print('All six historical SQL upgrades passed; group member links and messages preserved.')

if __name__ == '__main__':
    parser=argparse.ArgumentParser()
    parser.add_argument('database_kt')
    options=parser.parse_args()
    run(Path(options.database_kt).read_text(encoding='utf-8'))
