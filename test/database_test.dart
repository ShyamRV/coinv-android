import 'package:flutter_test/flutter_test.dart';
import 'package:path/path.dart' as p;
import 'package:sqflite_common_ffi/sqflite_ffi.dart';

import 'package:coinv_app/data/database.dart';

void main() {
  late CoinVDatabase database;

  setUpAll(() {
    sqfliteFfiInit();
    databaseFactory = databaseFactoryFfi;
  });

  setUp(() async {
    final path = await getDatabasesPath();
    await deleteDatabase(p.join(path, 'coinv_flutter.db'));
    await deleteDatabase(p.join(path, 'coinv_v103.db'));
    database = CoinVDatabase.forTesting();
  });

  tearDown(() async {
    await database.close();
    final path = await getDatabasesPath();
    await deleteDatabase(p.join(path, 'coinv_flutter.db'));
    await deleteDatabase(p.join(path, 'coinv_v103.db'));
  });

  test('creates complete schema and neutral profile', () async {
    final profile = await database.profile();
    final db = await database.database;
    final tables = (await db.rawQuery(
      "SELECT name FROM sqlite_master WHERE type = 'table'",
    )).map((row) => row['name']);

    expect(profile['name'], 'You');
    expect(tables, containsAll(['suggestion_scores', 'analytics', 'promises']));
  });

  test('task completion recalculates goal progress', () async {
    final goal = await database.addGoal('Ship', 'Finish migration');
    final first = await database.addTask(goal, 'Implement');
    await database.addTask(goal, 'Verify');

    await database.setTaskCompleted(first, true);
    expect((await database.goals()).single.progress, 50);

    final second = (await database.tasksForGoal(goal)).last;
    await database.setTaskCompleted(second.id, true);
    final completed = (await database.goals()).single;
    expect(completed.progress, 100);
    expect(completed.status, 'completed');
  });

  test('imports native Room data once without deleting source', () async {
    final root = await getDatabasesPath();
    final legacyPath = p.join(root, 'coinv_v103.db');
    final legacy = await openDatabase(
      legacyPath,
      version: 1,
      onCreate: (db, version) async {
        await db.execute(
          '''CREATE TABLE memories(
          id INTEGER PRIMARY KEY, title TEXT NOT NULL, content TEXT NOT NULL,
          category TEXT NOT NULL, tags TEXT NOT NULL, createdAt INTEGER NOT NULL)''',
        );
        await db.execute('''CREATE TABLE semantic_memories(
          id INTEGER PRIMARY KEY, content TEXT NOT NULL, layer TEXT NOT NULL,
          embedding TEXT NOT NULL, sourceType TEXT NOT NULL, sourceId INTEGER,
          timestamp INTEGER NOT NULL, importance REAL NOT NULL)''');
        await db.execute('''CREATE TABLE decisions(
          id INTEGER PRIMARY KEY, question TEXT NOT NULL, context TEXT NOT NULL,
          pros TEXT NOT NULL, cons TEXT NOT NULL, risks TEXT NOT NULL,
          opportunities TEXT NOT NULL, missingInfo TEXT NOT NULL,
          recommendation TEXT NOT NULL, confidenceScore REAL NOT NULL,
          outcome TEXT, status TEXT NOT NULL, createdAt INTEGER NOT NULL,
          outcomeFollowUpAt INTEGER NOT NULL, outcomeAskedAt INTEGER,
          embedding TEXT)''');
        await db.insert('memories', {
          'id': 42,
          'title': 'Native memory',
          'content': 'Preserve this',
          'category': 'idea',
          'tags': '',
          'createdAt': 1,
        });
        await db.insert('semantic_memories', {
          'id': 7,
          'content': 'Native value',
          'layer': 'value',
          'embedding': '[0.25,0.75]',
          'sourceType': 'user_stated',
          'sourceId': -7,
          'timestamp': 1,
          'importance': 1.0,
        });
        await db.insert('decisions', {
          'id': 9,
          'question': 'Native decision',
          'context': '',
          'pros': '["Fast"]',
          'cons': '["Risky"]',
          'risks': '["Loss"]',
          'opportunities': '["Learning"]',
          'missingInfo': '["Cost"]',
          'recommendation': 'Test first',
          'confidenceScore': .5,
          'status': 'pending_outcome',
          'createdAt': 1,
          'outcomeFollowUpAt': 2,
          'embedding': '[0.25,0.75]',
        });
      },
    );
    await legacy.close();

    expect((await database.memories()).single.id, 42);
    expect((await database.semanticMemories()).single.embedding, [.25, .75]);
    expect((await database.decisions()).single.pros, ['Fast']);
    expect(await database.metadata('legacy_import_status'), 'completed');
    expect(await databaseExists(legacyPath), isTrue);
  });

  test(
    'dismissal gate quiets an intervention after three dismissals',
    () async {
      for (var i = 0; i < 3; i++) {
        await database.logIntervention(
          'bias_spotter',
          'trigger',
          'content',
          outcome: 'dismissed',
        );
      }
      expect(await database.shouldFire('bias_spotter'), isFalse);
    },
  );

  test('promise follow-up is asked and resolved', () async {
    final db = await database.database;
    final intervention = await database.logIntervention(
      'promise_tracker',
      'I will ship',
      'I will ship',
      outcome: 'pending',
    );
    final promise = await db.insert('promises', {
      'text': 'I will ship',
      'captured_at': 1,
      'follow_up_at': 1,
      'status': 'pending',
      'intervention_id': intervention,
    });

    final due = (await database.pendingFollowUps()).single;
    final asked = await database.markFollowUpAsked(due);
    await database.resolveFollowUp(asked, 'Yes, I finished it');

    final row = (await db.query(
      'promises',
      where: 'id = ?',
      whereArgs: [promise],
    )).single;
    expect(row['status'], 'confirmed_done');
  });
}
