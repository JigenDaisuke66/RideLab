"""Host SQLite tests using the actual Room schema and history query (no APK required)."""
import json
from pathlib import Path
import re
import sqlite3
import unittest

ROOT = Path(__file__).resolve().parents[1]
SCHEMA = ROOT / 'app/schemas/com.isaac.ridelab.data.local.RideDatabase/2.json'
DAO = ROOT / 'app/src/main/java/com/isaac/ridelab/data/local/RideDao.kt'

class HistoryQueryTest(unittest.TestCase):
    def setUp(self):
        self.db = sqlite3.connect(':memory:')
        self.db.row_factory = sqlite3.Row
        for entity in json.loads(SCHEMA.read_text())['database']['entities']:
            self.db.execute(entity['createSql'].replace('${TABLE_NAME}', entity['tableName']))
            for index in entity.get('indices', []):
                self.db.execute(index['createSql'].replace('${TABLE_NAME}', entity['tableName']))
        self.query = re.search(r'@Query\("""(SELECT r\..*?)"""\)', DAO.read_text(), re.S)[1]

    def tearDown(self):
        self.db.close()

    def session(self, id, start, status='COMPLETED'):
        self.db.execute('INSERT INTO ride_sessions (id,startTimeEpochMillis,durationMillis,status,activeSlot) VALUES (?,?,0,?,?)',
                        (id, start, status, 1 if status == 'ACTIVE' else None))

    def sample(self, id, elapsed, power=None, hr=None, cadence=None):
        self.db.execute('INSERT INTO ride_samples (sessionId,timestampEpochMillis,elapsedMillis,trainerPowerWatts,heartRateBpm,cadenceRpm) VALUES (?,0,?,?,?,?)',
                        (id, elapsed, power, hr, cadence))

    def test_newest_first_excludes_active_and_includes_interrupted(self):
        self.session(1, 100)
        self.session(2, 200, 'ABORTED')
        self.session(3, 300, 'ACTIVE')
        self.session(4, 200)
        self.assertEqual([4, 2, 1], [r['id'] for r in self.db.execute(self.query)])

    def test_counts_boundaries_and_null_aware_metrics(self):
        self.session(1, 100)
        self.session(2, 200)
        self.sample(1, 0, 0, 120, 60.0)
        self.sample(1, 1000, 200, None, 90.0)
        self.sample(1, 2500, None, 140, None)
        self.sample(2, 0, 999, 999, 999.0)
        row = list(self.db.execute(self.query))[1]
        expected = dict(sampleCount=3, firstElapsedMillis=0, lastElapsedMillis=2500,
                        averagePower=100, maximumPower=200, averageHeartRate=130,
                        maximumHeartRate=140, averageCadence=75, maximumCadence=90)
        for key, value in expected.items():
            self.assertEqual(value, row[key], key)

    def test_empty_history(self):
        self.assertEqual([], list(self.db.execute(self.query)))

    def test_empty_session_and_all_null_samples(self):
        self.session(1, 100)
        row = self.db.execute(self.query).fetchone()
        self.assertEqual(0, row['sampleCount'])
        self.assertIsNone(row['firstElapsedMillis'])
        self.assertIsNone(row['lastElapsedMillis'])
        self.sample(1, 0)
        row = self.db.execute(self.query).fetchone()
        self.assertEqual(1, row['sampleCount'])
        for key in ('averagePower', 'maximumPower', 'averageHeartRate', 'maximumHeartRate', 'averageCadence', 'maximumCadence'):
            self.assertIsNone(row[key], key)

if __name__ == '__main__':
    unittest.main()
