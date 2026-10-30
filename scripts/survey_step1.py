import mysql.connector
import json

conn = mysql.connector.connect(host='localhost', port=3306, user='root', password='123456', database='nihon_db')
cursor = conn.cursor(dictionary=True)

print("=== ALL TABLES ===")
cursor.execute("SHOW TABLES")
tables = [list(r.values())[0] for r in cursor.fetchall()]
for t in tables:
    print(" -", t)

for table in ['vocabulary', 'topic', 'kanji_entry', 'kanji_word']:
    if table in tables:
        cursor.execute(f"DESCRIBE {table}")
        print(f"\n=== TABLE {table} ===")
        for col in cursor.fetchall():
            print(f"  {col['Field']}: {col['Type']}, Null={col['Null']}, Key={col['Key']}, Default={col['Default']}")

# Check foreign keys
cursor.execute("""
    SELECT TABLE_NAME, COLUMN_NAME, CONSTRAINT_NAME, REFERENCED_TABLE_NAME, REFERENCED_COLUMN_NAME
    FROM INFORMATION_SCHEMA.KEY_COLUMN_USAGE
    WHERE TABLE_SCHEMA = 'nihon_db' AND REFERENCED_TABLE_NAME IS NOT NULL
""")
print("\n=== FOREIGN KEYS ===")
for fk in cursor.fetchall():
    print(f"  {fk['TABLE_NAME']}.{fk['COLUMN_NAME']} -> {fk['REFERENCED_TABLE_NAME']}.{fk['REFERENCED_COLUMN_NAME']} ({fk['CONSTRAINT_NAME']})")

# Sample 5 words from topicId=63
print("\n=== 5 SAMPLE WORDS FROM topicId=63 ===")
cursor.execute("SELECT * FROM vocabulary WHERE topic_id = 63 LIMIT 5")
for r in cursor.fetchall():
    print(json.dumps(r, default=str, ensure_ascii=False, indent=2))

# Check topic 63 details
cursor.execute("SELECT * FROM topic WHERE id = 63")
print("\n=== TOPIC 63 ===")
for r in cursor.fetchall():
    print(json.dumps(r, default=str, ensure_ascii=False))

# Check topic 86 and Lesson 12 topic
print("\n=== TOPICS WITH 'Kanji A2-1' ===")
cursor.execute("SELECT * FROM topic WHERE name LIKE '%Kanji A2-1%'")
for r in cursor.fetchall():
    print(json.dumps(r, default=str, ensure_ascii=False))

# Check kanji_word counts for Lesson 11 and 12
print("\n=== KANJI_WORD COUNTS FOR LESSON 11 & 12 ===")
cursor.execute("""
    SELECT t.id AS topic_id, t.name AS topic_name, k.id AS kanji_id, k.kanji_character, COUNT(kw.id) AS word_count
    FROM kanji_entry k
    JOIN topic t ON k.topic_id = t.id
    LEFT JOIN kanji_word kw ON kw.kanji_id = k.id
    WHERE t.name LIKE '%Kanji A2-1%'
    GROUP BY t.id, t.name, k.id, k.kanji_character, k.source_line
    ORDER BY t.id, k.source_line
""")
for r in cursor.fetchall():
    print(f"  Topic {r['topic_id']} ({r['topic_name']}): Kanji {r['kanji_character']} (ID={r['kanji_id']}) -> {r['word_count']} words")

# Check word "店" across topics
print("\n=== VOCABULARY '店' ===")
cursor.execute("SELECT v.*, t.name as topic_name FROM vocabulary v JOIN topic t ON v.topic_id = t.id WHERE v.japanese_text LIKE '%店%' LIMIT 5")
for r in cursor.fetchall():
    print(f"  ID={r['id']}, topic_id={r['topic_id']} ({r['topic_name']}), japanese='{r['japanese_text']}', reading='{r['reading']}', romaji='{r['romaji']}', meaning='{r['meaning_vi']}'")

conn.close()
