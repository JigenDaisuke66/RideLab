"""Run actual migration and previous-ride query against SQLite/exported Room schemas."""
import json, re, sqlite3, unittest
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
BASE=ROOT/'app/src/main/java/com/isaac/ridelab/data/local'
SCHEMAS=ROOT/'app/schemas/com.isaac.ridelab.data.local.RideDatabase'

def create(db, version):
    for entity in json.loads((SCHEMAS/f'{version}.json').read_text())['database']['entities']:
        db.execute(entity['createSql'].replace('${TABLE_NAME}',entity['tableName']))
        for index in entity.get('indices',[]): db.execute(index['createSql'].replace('${TABLE_NAME}',entity['tableName']))

class WeightMigrationTest(unittest.TestCase):
    def test_preserves_old_rides_samples_and_ftp_with_null_weight(self):
        db=sqlite3.connect(':memory:'); create(db,2)
        db.execute("INSERT INTO ride_sessions(id,startTimeEpochMillis,durationMillis,status,analyticsFtpWatts) VALUES(1,1000,60000,'COMPLETED',130)")
        db.execute('INSERT INTO ride_samples(sessionId,timestampEpochMillis,elapsedMillis,trainerPowerWatts) VALUES(1,1000,0,100)')
        sql=re.search(r'db.execSQL\("(ALTER TABLE ride_sessions ADD COLUMN bodyWeightKg REAL)"\)',(BASE/'RideDatabase.kt').read_text())[1]
        db.execute(sql)
        self.assertEqual((130,None,60000),db.execute('SELECT analyticsFtpWatts,bodyWeightKg,durationMillis FROM ride_sessions').fetchone())
        self.assertEqual((100,),db.execute('SELECT trainerPowerWatts FROM ride_samples').fetchone())
        fresh=sqlite3.connect(':memory:'); create(fresh,3)
        for table in ('ride_sessions','ride_samples'):
            self.assertEqual(list(db.execute(f'PRAGMA table_info({table})')),list(fresh.execute(f'PRAGMA table_info({table})')))
        db.close();fresh.close()
    def test_pr_query_excludes_future_self_active_and_aborted(self):
        db=sqlite3.connect(':memory:'); create(db,3)
        for id,time,status in [(1,1000,'COMPLETED'),(2,2000,'COMPLETED'),(3,2000,'COMPLETED'),(4,3000,'COMPLETED'),(5,500,'ABORTED'),(6,500,'ACTIVE')]:
            db.execute('INSERT INTO ride_sessions(id,startTimeEpochMillis,durationMillis,status) VALUES(?,?,0,?)',(id,time,status))
        query=re.search(r'@Query\("(SELECT \* FROM ride_sessions WHERE status = \'COMPLETED\'.*?)"\)',(BASE/'RideDao.kt').read_text())[1]
        self.assertEqual([1,2],[r[0] for r in db.execute(query,dict(timestamp=2000,id=3))])
        db.close()
if __name__=='__main__': unittest.main()
