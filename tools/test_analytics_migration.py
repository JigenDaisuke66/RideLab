"""Check actual migration SQL, row preservation and nullable historical FTP using host SQLite."""
import json, re, sqlite3, unittest
from pathlib import Path
ROOT = Path(__file__).resolve().parents[1]
BASE = ROOT / 'app/src/main/java/com/isaac/ridelab/data/local'
SCHEMAS = ROOT / 'app/schemas/com.isaac.ridelab.data.local.RideDatabase'

class AnalyticsMigrationTest(unittest.TestCase):
    def test_migration_and_historical_ftp(self):
        db = sqlite3.connect(':memory:')
        db.execute('PRAGMA foreign_keys=ON')
        old = json.loads((SCHEMAS / '1.json').read_text())['database']
        for entity in old['entities']:
            db.execute(entity['createSql'].replace('${TABLE_NAME}', entity['tableName']))
            for index in entity.get('indices', []):
                db.execute(index['createSql'].replace('${TABLE_NAME}', entity['tableName']))
        db.execute("INSERT INTO ride_sessions(id,startTimeEpochMillis,durationMillis,status) VALUES(1,1000,60000,'COMPLETED')")
        db.execute('INSERT INTO ride_samples(sessionId,timestampEpochMillis,elapsedMillis,trainerPowerWatts) VALUES(1,1000,0,100)')
        sql = re.search(r'db.execSQL\("(ALTER TABLE ride_sessions ADD COLUMN analyticsFtpWatts INTEGER)"\)', (BASE / 'RideDatabase.kt').read_text())[1]
        db.execute(sql)
        self.assertEqual((1,60000,None), db.execute('SELECT id,durationMillis,analyticsFtpWatts FROM ride_sessions').fetchone())
        self.assertEqual((100,), db.execute('SELECT trainerPowerWatts FROM ride_samples').fetchone())
        fresh = sqlite3.connect(':memory:')
        for entity in json.loads((SCHEMAS / '2.json').read_text())['database']['entities']:
            fresh.execute(entity['createSql'].replace('${TABLE_NAME}', entity['tableName']))
            name = entity['tableName']
            self.assertEqual(list(db.execute(f'PRAGMA table_info({name})')),list(fresh.execute(f'PRAGMA table_info({name})')))
        fresh.close(); db.close()

if __name__ == '__main__': unittest.main()
