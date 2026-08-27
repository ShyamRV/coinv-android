import 'dart:math';

import 'package:path/path.dart' as p;
import 'package:sqflite/sqflite.dart';

import 'models.dart';

class CoinVDatabase {
  CoinVDatabase._();
  static final CoinVDatabase instance = CoinVDatabase._();

  Database? _database;
  Future<Database> get database async => _database ??= await openDatabase(
    p.join(await getDatabasesPath(), 'coinv_flutter.db'),
    version: 1,
    onCreate: _create,
  );

  static Future<void> _create(Database db, int version) async {
    final statements = <String>[
      '''CREATE TABLE profile(
        id INTEGER PRIMARY KEY, name TEXT NOT NULL, cognitive_state TEXT NOT NULL,
        active_coach TEXT NOT NULL, listening_mode TEXT NOT NULL,
        wake_word_enabled INTEGER NOT NULL, continuous_listening INTEGER NOT NULL)''',
      '''CREATE TABLE conversations(
        id INTEGER PRIMARY KEY AUTOINCREMENT, title TEXT NOT NULL,
        created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL)''',
      '''CREATE TABLE messages(
        id INTEGER PRIMARY KEY AUTOINCREMENT, conversation_id INTEGER NOT NULL,
        role TEXT NOT NULL, text TEXT NOT NULL, timestamp INTEGER NOT NULL)''',
      '''CREATE TABLE memories(
        id INTEGER PRIMARY KEY AUTOINCREMENT, title TEXT NOT NULL,
        content TEXT NOT NULL, category TEXT NOT NULL, tags TEXT NOT NULL,
        created_at INTEGER NOT NULL)''',
      '''CREATE TABLE semantic_memories(
        id INTEGER PRIMARY KEY AUTOINCREMENT, content TEXT NOT NULL,
        layer_name TEXT NOT NULL, embedding TEXT NOT NULL,
        source_type TEXT NOT NULL, source_id INTEGER, timestamp INTEGER NOT NULL,
        importance REAL NOT NULL)''',
      '''CREATE UNIQUE INDEX semantic_source_id
         ON semantic_memories(source_id) WHERE source_id IS NOT NULL''',
      '''CREATE TABLE goals(
        id INTEGER PRIMARY KEY AUTOINCREMENT, title TEXT NOT NULL,
        description TEXT NOT NULL, progress INTEGER NOT NULL,
        status TEXT NOT NULL, created_at INTEGER NOT NULL)''',
      '''CREATE TABLE tasks(
        id INTEGER PRIMARY KEY AUTOINCREMENT, goal_id INTEGER NOT NULL,
        title TEXT NOT NULL, completed INTEGER NOT NULL, due_date INTEGER)''',
      '''CREATE TABLE decisions(
        id INTEGER PRIMARY KEY AUTOINCREMENT, question TEXT NOT NULL,
        context_notes TEXT NOT NULL, pros TEXT NOT NULL, cons TEXT NOT NULL,
        risks TEXT NOT NULL, opportunities TEXT NOT NULL,
        missing_info TEXT NOT NULL, recommendation TEXT NOT NULL,
        confidence REAL NOT NULL, outcome TEXT, status TEXT NOT NULL,
        created_at INTEGER NOT NULL, follow_up_at INTEGER NOT NULL,
        outcome_asked_at INTEGER, embedding TEXT)''',
      '''CREATE TABLE insights(
        id INTEGER PRIMARY KEY AUTOINCREMENT, text TEXT NOT NULL,
        category TEXT NOT NULL, created_at INTEGER NOT NULL)''',
      '''CREATE TABLE learning_items(
        id INTEGER PRIMARY KEY AUTOINCREMENT, topic TEXT NOT NULL,
        summary TEXT NOT NULL, progress INTEGER NOT NULL,
        flashcards_count INTEGER NOT NULL, created_at INTEGER NOT NULL)''',
      '''CREATE TABLE voice_sessions(
        id INTEGER PRIMARY KEY AUTOINCREMENT, transcript TEXT NOT NULL,
        duration_ms INTEGER NOT NULL, mode TEXT NOT NULL,
        created_at INTEGER NOT NULL)''',
      '''CREATE TABLE timeline_events(
        id INTEGER PRIMARY KEY AUTOINCREMENT, type TEXT NOT NULL,
        title TEXT NOT NULL, description TEXT NOT NULL,
        timestamp INTEGER NOT NULL)''',
      '''CREATE TABLE context_events(
        id INTEGER PRIMARY KEY AUTOINCREMENT, type TEXT NOT NULL,
        payload TEXT NOT NULL, source TEXT NOT NULL, app_mode TEXT NOT NULL,
        hour_of_day INTEGER NOT NULL, created_at INTEGER NOT NULL)''',
      '''CREATE TABLE feedback_events(
        id INTEGER PRIMARY KEY AUTOINCREMENT, target_type TEXT NOT NULL,
        target_id TEXT NOT NULL, accepted INTEGER NOT NULL,
        topic TEXT NOT NULL, response_style TEXT NOT NULL,
        created_at INTEGER NOT NULL)''',
      '''CREATE TABLE preference_profile(
        id INTEGER PRIMARY KEY, short_response_weight REAL NOT NULL,
        business_topic_weight REAL NOT NULL, goal_topic_weight REAL NOT NULL,
        decision_topic_weight REAL NOT NULL, learning_topic_weight REAL NOT NULL,
        interruption_tolerance REAL NOT NULL, total_interactions INTEGER NOT NULL,
        accepted_suggestions INTEGER NOT NULL, ignored_suggestions INTEGER NOT NULL,
        updated_at INTEGER NOT NULL)''',
      '''CREATE TABLE mode_history(
        id INTEGER PRIMARY KEY AUTOINCREMENT, mode TEXT NOT NULL,
        source TEXT NOT NULL, timestamp INTEGER NOT NULL)''',
      '''CREATE TABLE interventions(
        id INTEGER PRIMARY KEY AUTOINCREMENT, type TEXT NOT NULL,
        trigger_context TEXT NOT NULL, content TEXT NOT NULL,
        timestamp INTEGER NOT NULL, outcome TEXT NOT NULL,
        outcome_timestamp INTEGER)''',
      '''CREATE TABLE promises(
        id INTEGER PRIMARY KEY AUTOINCREMENT, text TEXT NOT NULL,
        captured_at INTEGER NOT NULL, follow_up_at INTEGER NOT NULL,
        status TEXT NOT NULL, intervention_id INTEGER)''',
    ];
    for (final statement in statements) {
      await db.execute(statement);
    }
    await db.insert('profile', {
      'id': 1,
      'name': 'Shyam',
      'cognitive_state': 'Ready',
      'active_coach': 'Founder Coach',
      'listening_mode': 'push_to_talk',
      'wake_word_enabled': 0,
      'continuous_listening': 0,
    });
    await db.insert('preference_profile', {
      'id': 1,
      'short_response_weight': .5,
      'business_topic_weight': .5,
      'goal_topic_weight': .5,
      'decision_topic_weight': .5,
      'learning_topic_weight': .5,
      'interruption_tolerance': .5,
      'total_interactions': 0,
      'accepted_suggestions': 0,
      'ignored_suggestions': 0,
      'updated_at': DateTime.now().millisecondsSinceEpoch,
    });
  }

  Future<Map<String, Object?>> profile() async {
    final rows = await (await database).query('profile', where: 'id = 1');
    return rows.first;
  }

  Future<void> updateProfile(Map<String, Object?> values) async {
    await (await database).update('profile', values, where: 'id = 1');
  }

  Future<int> ensureConversation() async {
    final db = await database;
    final existing = await db.query(
      'conversations',
      orderBy: 'updated_at DESC',
      limit: 1,
    );
    if (existing.isNotEmpty) return existing.first['id'] as int;
    final now = DateTime.now().millisecondsSinceEpoch;
    return db.insert('conversations', {
      'title': 'Voice Session',
      'created_at': now,
      'updated_at': now,
    });
  }

  Future<List<ChatMessage>> messages() async {
    final conversation = await ensureConversation();
    final rows = await (await database).query(
      'messages',
      where: 'conversation_id = ?',
      whereArgs: [conversation],
      orderBy: 'timestamp ASC',
    );
    return rows
        .map(
          (row) => ChatMessage(
            role: row['role'] as String,
            text: row['text'] as String,
            timestamp: DateTime.fromMillisecondsSinceEpoch(
              row['timestamp'] as int,
            ),
          ),
        )
        .toList();
  }

  Future<void> addMessage(ChatMessage message) async {
    final db = await database;
    final conversation = await ensureConversation();
    await db.insert('messages', message.toMap(conversation));
    await db.update(
      'conversations',
      {'updated_at': DateTime.now().millisecondsSinceEpoch},
      where: 'id = ?',
      whereArgs: [conversation],
    );
  }

  Future<List<MemoryItem>> memories([String query = '']) async {
    final q = query.trim();
    final rows = await (await database).query(
      'memories',
      where: q.isEmpty ? null : 'title LIKE ? OR content LIKE ? OR tags LIKE ?',
      whereArgs: q.isEmpty ? null : List.filled(3, '%$q%'),
      orderBy: 'created_at DESC',
    );
    return rows.map(MemoryItem.fromMap).toList();
  }

  Future<int> addMemory({
    required String title,
    required String content,
    required String category,
    String tags = '',
  }) async {
    final db = await database;
    final now = DateTime.now().millisecondsSinceEpoch;
    final id = await db.insert('memories', {
      'title': title,
      'content': content,
      'category': category,
      'tags': tags,
      'created_at': now,
    });
    await addTimeline('memory', title, content);
    return id;
  }

  Future<void> removeMemory(int id) async {
    await (await database).delete('memories', where: 'id = ?', whereArgs: [id]);
  }

  Future<List<SemanticMemory>> semanticMemories({String? layer}) async {
    final rows = await (await database).query(
      'semantic_memories',
      where: layer == null ? null : 'layer_name = ?',
      whereArgs: layer == null ? null : [layer],
      orderBy: 'timestamp DESC',
    );
    return rows.map(SemanticMemory.fromMap).toList();
  }

  Future<void> upsertSemantic({
    required String content,
    required String layer,
    required String sourceType,
    required List<double> embedding,
    int? sourceId,
    double importance = .5,
  }) async {
    final db = await database;
    final values = {
      'content': content,
      'layer_name': layer,
      'embedding': embedding.join(','),
      'source_type': sourceType,
      'source_id': sourceId,
      'timestamp': DateTime.now().millisecondsSinceEpoch,
      'importance': importance.clamp(0, 1),
    };
    if (sourceId != null) {
      final changed = await db.update(
        'semantic_memories',
        values,
        where: 'source_id = ?',
        whereArgs: [sourceId],
      );
      if (changed > 0) return;
    }
    await db.insert('semantic_memories', values);
  }

  Future<List<SemanticMemory>> recall(
    List<double> queryEmbedding, {
    int limit = 6,
  }) async {
    final memories = await semanticMemories();
    final scored =
        memories
            .map(
              (memory) => (
                memory: memory,
                score: _cosine(queryEmbedding, memory.embedding),
              ),
            )
            .where((item) => item.score >= .4)
            .toList()
          ..sort((a, b) {
            if ((a.score - b.score).abs() <= .05) {
              final recent = b.memory.timestamp.compareTo(a.memory.timestamp);
              return recent != 0
                  ? recent
                  : b.memory.importance.compareTo(a.memory.importance);
            }
            return b.score.compareTo(a.score);
          });
    return scored.take(limit).map((item) => item.memory).toList();
  }

  Future<void> recordContext(
    String type,
    String payload,
    String source,
    AppMode mode,
  ) async {
    final now = DateTime.now();
    await (await database).insert('context_events', {
      'type': type,
      'payload': payload.substring(0, min(payload.length, 500)),
      'source': source,
      'app_mode': mode.name,
      'hour_of_day': now.hour,
      'created_at': now.millisecondsSinceEpoch,
    });
  }

  Future<List<ContextItem>> recentContext() async {
    final rows = await (await database).query(
      'context_events',
      orderBy: 'created_at DESC',
      limit: 12,
    );
    return rows.map(ContextItem.fromMap).toList();
  }

  Future<int> addDecision(
    String question,
    String context,
    DecisionAnalysis analysis,
    List<double>? embedding,
  ) async {
    final now = DateTime.now().millisecondsSinceEpoch;
    final id = await (await database).insert('decisions', {
      'question': question,
      'context_notes': context,
      'pros': encodeList(analysis.pros),
      'cons': encodeList(analysis.cons),
      'risks': encodeList(analysis.risks),
      'opportunities': encodeList(analysis.opportunities),
      'missing_info': encodeList(analysis.missingInformation),
      'recommendation': analysis.recommendation,
      'confidence': analysis.confidence.clamp(0, 1),
      'status': 'pending_outcome',
      'created_at': now,
      'follow_up_at': now + const Duration(days: 21).inMilliseconds,
      'embedding': embedding?.join(','),
    });
    await addTimeline('decision', question, analysis.recommendation);
    return id;
  }

  Future<List<DecisionItem>> decisions() async {
    final rows = await (await database).query(
      'decisions',
      orderBy: 'created_at DESC',
    );
    return rows.map(DecisionItem.fromMap).toList();
  }

  Future<void> setDecisionOutcome(int id, String status, String? notes) async {
    await (await database).update(
      'decisions',
      {
        'status': status,
        'outcome': notes,
        'outcome_asked_at': DateTime.now().millisecondsSinceEpoch,
      },
      where: 'id = ?',
      whereArgs: [id],
    );
  }

  Future<int> addGoal(String title, String description) async {
    final id = await (await database).insert('goals', {
      'title': title,
      'description': description,
      'progress': 0,
      'status': 'active',
      'created_at': DateTime.now().millisecondsSinceEpoch,
    });
    await addTimeline('goal', title, description);
    return id;
  }

  Future<List<GoalItem>> goals() async {
    final rows = await (await database).query(
      'goals',
      orderBy: 'created_at DESC',
    );
    return rows.map(GoalItem.fromMap).toList();
  }

  Future<void> addTimeline(
    String type,
    String title,
    String description,
  ) async {
    await (await database).insert('timeline_events', {
      'type': type,
      'title': title,
      'description': description,
      'timestamp': DateTime.now().millisecondsSinceEpoch,
    });
  }

  Future<List<TimelineItem>> timeline() async {
    final rows = await (await database).query(
      'timeline_events',
      orderBy: 'timestamp DESC',
    );
    return rows.map(TimelineItem.fromMap).toList();
  }

  Future<int> logIntervention(
    String type,
    String trigger,
    String content, {
    String outcome = 'shown',
  }) async {
    return (await database).insert('interventions', {
      'type': type,
      'trigger_context': trigger,
      'content': content,
      'timestamp': DateTime.now().millisecondsSinceEpoch,
      'outcome': outcome,
    });
  }

  Future<bool> shouldFire(String type) async {
    final rows = await (await database).query(
      'interventions',
      where: 'type = ?',
      whereArgs: [type],
      orderBy: 'timestamp DESC',
      limit: 5,
    );
    if (rows.length < 3) return true;
    return rows.where((row) => row['outcome'] == 'dismissed').length < 3;
  }

  Future<List<BehaviorStat>> behaviorStats() async {
    const types = [
      'devils_advocate',
      'bias_spotter',
      'promise_tracker',
      'commitment_guard',
      'decision_followup',
    ];
    final db = await database;
    return Future.wait(
      types.map((type) async {
        final shown =
            Sqflite.firstIntValue(
              await db.rawQuery(
                'SELECT COUNT(*) FROM interventions WHERE type = ?',
                [type],
              ),
            ) ??
            0;
        final dismissed =
            Sqflite.firstIntValue(
              await db.rawQuery(
                "SELECT COUNT(*) FROM interventions WHERE type = ? AND outcome = 'dismissed'",
                [type],
              ),
            ) ??
            0;
        return BehaviorStat(type: type, shown: shown, dismissed: dismissed);
      }),
    );
  }

  Future<void> capturePromise(String text) async {
    final db = await database;
    final now = DateTime.now().millisecondsSinceEpoch;
    final intervention = await logIntervention(
      'promise_tracker',
      text,
      text,
      outcome: 'pending',
    );
    await db.insert('promises', {
      'text': text,
      'captured_at': now,
      'follow_up_at': now + const Duration(days: 14).inMilliseconds,
      'status': 'pending',
      'intervention_id': intervention,
    });
  }

  Future<int> recentPromiseCount() async {
    final since = DateTime.now()
        .subtract(const Duration(days: 7))
        .millisecondsSinceEpoch;
    return Sqflite.firstIntValue(
          await (await database).rawQuery(
            'SELECT COUNT(*) FROM promises WHERE captured_at >= ?',
            [since],
          ),
        ) ??
        0;
  }

  Future<void> saveVoiceSession(String text, int duration, AppMode mode) async {
    await (await database).insert('voice_sessions', {
      'transcript': text,
      'duration_ms': duration,
      'mode': mode.name,
      'created_at': DateTime.now().millisecondsSinceEpoch,
    });
  }

  Future<void> clearMemory() async {
    final db = await database;
    await db.transaction((txn) async {
      await txn.delete('memories');
      await txn.delete('semantic_memories');
    });
  }

  Future<void> reset() async {
    final db = await database;
    const tables = [
      'messages',
      'conversations',
      'memories',
      'semantic_memories',
      'goals',
      'tasks',
      'decisions',
      'insights',
      'learning_items',
      'voice_sessions',
      'timeline_events',
      'context_events',
      'feedback_events',
      'mode_history',
      'interventions',
      'promises',
    ];
    await db.transaction((txn) async {
      for (final table in tables) {
        await txn.delete(table);
      }
      await txn.update('profile', {
        'name': 'Shyam',
        'cognitive_state': 'Ready',
        'active_coach': 'Founder Coach',
        'listening_mode': 'push_to_talk',
        'wake_word_enabled': 0,
        'continuous_listening': 0,
      }, where: 'id = 1');
    });
  }
}

double _cosine(List<double> a, List<double> b) {
  if (a.isEmpty || a.length != b.length) return 0;
  var dot = 0.0;
  var aNorm = 0.0;
  var bNorm = 0.0;
  for (var i = 0; i < a.length; i++) {
    dot += a[i] * b[i];
    aNorm += a[i] * a[i];
    bNorm += b[i] * b[i];
  }
  if (aNorm == 0 || bNorm == 0) return 0;
  return dot / (sqrt(aNorm) * sqrt(bNorm));
}
