import 'dart:convert';
import 'dart:math';

import 'package:path/path.dart' as p;
import 'package:sqflite/sqflite.dart';

import 'models.dart';

class CoinVDatabase {
  CoinVDatabase._();
  CoinVDatabase.forTesting();
  static final CoinVDatabase instance = CoinVDatabase._();
  static const schemaVersion = 3;

  Database? _database;
  Future<Database> get database async => _database ??= await _open();

  Future<Database> _open() async {
    final databasesPath = await getDatabasesPath();
    final db = await openDatabase(
      p.join(databasesPath, 'coinv_flutter.db'),
      version: schemaVersion,
      onConfigure: (database) => database.execute('PRAGMA foreign_keys = ON'),
      onCreate: _create,
      onUpgrade: _upgrade,
    );
    await _importLegacyDatabase(db, p.join(databasesPath, 'coinv_v103.db'));
    return db;
  }

  Future<void> close() async {
    await _database?.close();
    _database = null;
  }

  static Future<void> _upgrade(
    Database db,
    int oldVersion,
    int newVersion,
  ) async {
    if (oldVersion < 2) {
      await db.execute(
        'ALTER TABLE semantic_memories ADD COLUMN embedding_model TEXT',
      );
      await db.execute(
        'ALTER TABLE semantic_memories ADD COLUMN embedding_dimensions INTEGER',
      );
      await db.execute('ALTER TABLE decisions ADD COLUMN embedding_model TEXT');
      await db.execute(
        'ALTER TABLE decisions ADD COLUMN embedding_dimensions INTEGER',
      );
      await db.execute(
        'ALTER TABLE profile ADD COLUMN updated_at INTEGER NOT NULL DEFAULT 0',
      );
      await db.execute(
        'ALTER TABLE preference_profile ADD COLUMN night_activity_weight REAL NOT NULL DEFAULT 0.5',
      );
      await db.execute(
        'ALTER TABLE preference_profile ADD COLUMN morning_activity_weight REAL NOT NULL DEFAULT 0.5',
      );
      await db.execute(
        'ALTER TABLE preference_profile ADD COLUMN proactive_insight_threshold REAL NOT NULL DEFAULT 0.55',
      );
      await _createParityTables(db);
    }
    if (oldVersion < 3) {
      await db.execute(
        'CREATE TABLE IF NOT EXISTS app_metadata(key TEXT PRIMARY KEY, value TEXT NOT NULL)',
      );
      await db.execute(
        'CREATE INDEX IF NOT EXISTS context_created_at ON context_events(created_at DESC)',
      );
      await db.execute(
        'CREATE INDEX IF NOT EXISTS decisions_follow_up ON decisions(status, follow_up_at)',
      );
      await db.execute(
        'CREATE INDEX IF NOT EXISTS promises_follow_up ON promises(status, follow_up_at)',
      );
    }
  }

  static Future<void> _create(Database db, int version) async {
    final statements = <String>[
      '''CREATE TABLE profile(
        id INTEGER PRIMARY KEY, name TEXT NOT NULL, cognitive_state TEXT NOT NULL,
        active_coach TEXT NOT NULL, listening_mode TEXT NOT NULL,
        wake_word_enabled INTEGER NOT NULL, continuous_listening INTEGER NOT NULL,
        updated_at INTEGER NOT NULL)''',
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
        embedding_model TEXT, embedding_dimensions INTEGER,
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
        outcome_asked_at INTEGER, embedding TEXT, embedding_model TEXT,
        embedding_dimensions INTEGER)''',
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
        interruption_tolerance REAL NOT NULL, night_activity_weight REAL NOT NULL,
        morning_activity_weight REAL NOT NULL,
        proactive_insight_threshold REAL NOT NULL,
        total_interactions INTEGER NOT NULL,
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
      '''CREATE TABLE app_metadata(
        key TEXT PRIMARY KEY, value TEXT NOT NULL)''',
    ];
    for (final statement in statements) {
      await db.execute(statement);
    }
    await db.insert('profile', {
      'id': 1,
      'name': 'You',
      'cognitive_state': 'Ready',
      'active_coach': 'Founder Coach',
      'listening_mode': 'push_to_talk',
      'wake_word_enabled': 0,
      'continuous_listening': 0,
      'updated_at': DateTime.now().millisecondsSinceEpoch,
    });
    await db.insert('preference_profile', {
      'id': 1,
      'short_response_weight': .5,
      'business_topic_weight': .5,
      'goal_topic_weight': .5,
      'decision_topic_weight': .5,
      'learning_topic_weight': .5,
      'interruption_tolerance': .5,
      'night_activity_weight': .5,
      'morning_activity_weight': .5,
      'proactive_insight_threshold': .55,
      'total_interactions': 0,
      'accepted_suggestions': 0,
      'ignored_suggestions': 0,
      'updated_at': DateTime.now().millisecondsSinceEpoch,
    });
    await _createParityTables(db);
    await db.execute(
      'CREATE INDEX context_created_at ON context_events(created_at DESC)',
    );
    await db.execute(
      'CREATE INDEX decisions_follow_up ON decisions(status, follow_up_at)',
    );
    await db.execute(
      'CREATE INDEX promises_follow_up ON promises(status, follow_up_at)',
    );
  }

  static Future<void> _createParityTables(Database db) async {
    await db.execute('''CREATE TABLE IF NOT EXISTS analytics(
      id INTEGER PRIMARY KEY, focus_score INTEGER NOT NULL,
      mental_energy INTEGER NOT NULL, learning_velocity INTEGER NOT NULL,
      decision_readiness INTEGER NOT NULL, memory_activity INTEGER NOT NULL,
      ai_confidence INTEGER NOT NULL, daily_progress INTEGER NOT NULL,
      peak_hours TEXT NOT NULL, updated_at INTEGER NOT NULL)''');
    await db.execute('''CREATE TABLE IF NOT EXISTS recommendations(
      id INTEGER PRIMARY KEY AUTOINCREMENT, text TEXT NOT NULL,
      priority INTEGER NOT NULL, created_at INTEGER NOT NULL)''');
    await db.execute(
      '''CREATE TABLE IF NOT EXISTS suggestion_scores(
      id INTEGER PRIMARY KEY AUTOINCREMENT, text TEXT NOT NULL,
      category TEXT NOT NULL, score REAL NOT NULL, confidence REAL NOT NULL,
      surfaced INTEGER NOT NULL, accepted INTEGER, created_at INTEGER NOT NULL)''',
    );
  }

  static Future<void> _importLegacyDatabase(
    Database target,
    String legacyPath,
  ) async {
    final marker = await target.query(
      'app_metadata',
      columns: ['value'],
      where: 'key = ?',
      whereArgs: ['legacy_import_status'],
      limit: 1,
    );
    if (marker.isNotEmpty && marker.first['value'] == 'completed') return;
    if (!await databaseExists(legacyPath)) return;

    Database? legacy;
    try {
      legacy = await openReadOnlyDatabase(legacyPath);
      final source = legacy;
      final available = (await source.rawQuery(
        "SELECT name FROM sqlite_master WHERE type = 'table'",
      )).map((row) => row['name'] as String).toSet();
      await target.transaction((txn) async {
        for (final spec in _legacyTables) {
          if (!available.contains(spec.source)) continue;
          final rows = await source.query(spec.source);
          for (final row in rows) {
            final converted = <String, Object?>{};
            for (final entry in spec.columns.entries) {
              if (row.containsKey(entry.key)) {
                converted[entry.value] = _convertLegacyValue(
                  spec.source,
                  entry.key,
                  row[entry.key],
                );
              }
            }
            if (converted.isNotEmpty) {
              if (spec.target == 'profile' ||
                  spec.target == 'preference_profile') {
                final id = converted.remove('id') ?? 1;
                await txn.update(
                  spec.target,
                  converted,
                  where: 'id = ?',
                  whereArgs: [id],
                );
              } else {
                await txn.insert(
                  spec.target,
                  converted,
                  conflictAlgorithm: ConflictAlgorithm.ignore,
                );
              }
            }
          }
        }
        await txn.insert('app_metadata', {
          'key': 'legacy_import_status',
          'value': 'completed',
        }, conflictAlgorithm: ConflictAlgorithm.replace);
      });
    } catch (error) {
      await target.insert('app_metadata', {
        'key': 'legacy_import_status',
        'value': 'failed:${error.runtimeType}',
      }, conflictAlgorithm: ConflictAlgorithm.replace);
    } finally {
      await legacy?.close();
    }
  }

  static const _legacyTables = <_LegacyTable>[
    _LegacyTable('conversations', 'conversations', {
      'id': 'id',
      'title': 'title',
      'createdAt': 'created_at',
      'updatedAt': 'updated_at',
    }),
    _LegacyTable('messages', 'messages', {
      'id': 'id',
      'conversationId': 'conversation_id',
      'role': 'role',
      'text': 'text',
      'timestamp': 'timestamp',
    }),
    _LegacyTable('memories', 'memories', {
      'id': 'id',
      'title': 'title',
      'content': 'content',
      'category': 'category',
      'tags': 'tags',
      'createdAt': 'created_at',
    }),
    _LegacyTable('semantic_memories', 'semantic_memories', {
      'id': 'id',
      'content': 'content',
      'layer': 'layer_name',
      'embedding': 'embedding',
      'sourceType': 'source_type',
      'sourceId': 'source_id',
      'timestamp': 'timestamp',
      'importance': 'importance',
    }),
    _LegacyTable('goals', 'goals', {
      'id': 'id',
      'title': 'title',
      'description': 'description',
      'progress': 'progress',
      'status': 'status',
      'createdAt': 'created_at',
    }),
    _LegacyTable('tasks', 'tasks', {
      'id': 'id',
      'goalId': 'goal_id',
      'title': 'title',
      'completed': 'completed',
      'dueDate': 'due_date',
    }),
    _LegacyTable('decisions', 'decisions', {
      'id': 'id',
      'question': 'question',
      'context': 'context_notes',
      'pros': 'pros',
      'cons': 'cons',
      'risks': 'risks',
      'opportunities': 'opportunities',
      'missingInfo': 'missing_info',
      'recommendation': 'recommendation',
      'confidenceScore': 'confidence',
      'outcome': 'outcome',
      'status': 'status',
      'createdAt': 'created_at',
      'outcomeFollowUpAt': 'follow_up_at',
      'outcomeAskedAt': 'outcome_asked_at',
      'embedding': 'embedding',
    }),
    _LegacyTable('insights', 'insights', {
      'id': 'id',
      'text': 'text',
      'category': 'category',
      'createdAt': 'created_at',
    }),
    _LegacyTable('learning_items', 'learning_items', {
      'id': 'id',
      'topic': 'topic',
      'summary': 'summary',
      'progress': 'progress',
      'flashcardsCount': 'flashcards_count',
      'createdAt': 'created_at',
    }),
    _LegacyTable('voice_sessions', 'voice_sessions', {
      'id': 'id',
      'transcript': 'transcript',
      'durationMs': 'duration_ms',
      'mode': 'mode',
      'createdAt': 'created_at',
    }),
    _LegacyTable('user_profile', 'profile', {
      'id': 'id',
      'name': 'name',
      'cognitiveState': 'cognitive_state',
      'activeCoach': 'active_coach',
      'listeningMode': 'listening_mode',
      'wakeWordEnabled': 'wake_word_enabled',
      'continuousListening': 'continuous_listening',
    }),
    _LegacyTable('timeline_events', 'timeline_events', {
      'id': 'id',
      'type': 'type',
      'title': 'title',
      'description': 'description',
      'timestamp': 'timestamp',
    }),
    _LegacyTable('context_events', 'context_events', {
      'id': 'id',
      'type': 'type',
      'payload': 'payload',
      'source': 'source',
      'appMode': 'app_mode',
      'hourOfDay': 'hour_of_day',
      'createdAt': 'created_at',
    }),
    _LegacyTable('feedback_events', 'feedback_events', {
      'id': 'id',
      'targetType': 'target_type',
      'targetId': 'target_id',
      'accepted': 'accepted',
      'topic': 'topic',
      'responseStyle': 'response_style',
      'createdAt': 'created_at',
    }),
    _LegacyTable('preference_profile', 'preference_profile', {
      'id': 'id',
      'shortResponseWeight': 'short_response_weight',
      'businessTopicWeight': 'business_topic_weight',
      'goalTopicWeight': 'goal_topic_weight',
      'decisionTopicWeight': 'decision_topic_weight',
      'learningTopicWeight': 'learning_topic_weight',
      'interruptionTolerance': 'interruption_tolerance',
      'nightActivityWeight': 'night_activity_weight',
      'morningActivityWeight': 'morning_activity_weight',
      'proactiveInsightThreshold': 'proactive_insight_threshold',
      'totalInteractions': 'total_interactions',
      'acceptedSuggestions': 'accepted_suggestions',
      'ignoredSuggestions': 'ignored_suggestions',
      'updatedAt': 'updated_at',
    }),
    _LegacyTable('mode_history', 'mode_history', {
      'id': 'id',
      'mode': 'mode',
      'source': 'source',
      'timestamp': 'timestamp',
    }),
    _LegacyTable('suggestion_scores', 'suggestion_scores', {
      'id': 'id',
      'text': 'text',
      'category': 'category',
      'score': 'score',
      'confidence': 'confidence',
      'surfaced': 'surfaced',
      'accepted': 'accepted',
      'createdAt': 'created_at',
    }),
    _LegacyTable('interventions', 'interventions', {
      'id': 'id',
      'type': 'type',
      'triggerContext': 'trigger_context',
      'content': 'content',
      'timestamp': 'timestamp',
      'outcome': 'outcome',
      'outcomeTimestamp': 'outcome_timestamp',
    }),
    _LegacyTable('promises', 'promises', {
      'id': 'id',
      'text': 'text',
      'capturedAt': 'captured_at',
      'followUpAt': 'follow_up_at',
      'status': 'status',
      'interventionId': 'intervention_id',
    }),
  ];

  Future<String?> metadata(String key) async {
    final rows = await (await database).query(
      'app_metadata',
      columns: ['value'],
      where: 'key = ?',
      whereArgs: [key],
      limit: 1,
    );
    return rows.isEmpty ? null : rows.first['value'] as String?;
  }

  Future<Map<String, Object?>> profile() async {
    final rows = await (await database).query('profile', where: 'id = 1');
    return rows.first;
  }

  Future<void> updateProfile(Map<String, Object?> values) async {
    await (await database).update('profile', {
      ...values,
      'updated_at': DateTime.now().millisecondsSinceEpoch,
    }, where: 'id = 1');
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
    String embeddingModel = 'local-v1',
    int? sourceId,
    double importance = .5,
  }) async {
    final db = await database;
    final values = {
      'content': content,
      'layer_name': layer,
      'embedding': embedding.join(','),
      'embedding_model': embeddingModel,
      'embedding_dimensions': embedding.length,
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

  Future<List<SemanticMemory>> incompatibleEmbeddings(
    String model,
    int dimensions,
  ) async {
    final rows = await (await database).query(
      'semantic_memories',
      where:
          'embedding_model IS NULL OR embedding_model != ? '
          'OR embedding_dimensions IS NULL OR embedding_dimensions != ?',
      whereArgs: [model, dimensions],
    );
    return rows.map(SemanticMemory.fromMap).toList();
  }

  Future<void> updateSemanticEmbedding(
    int id,
    List<double> embedding,
    String model,
  ) async {
    await (await database).update(
      'semantic_memories',
      {
        'embedding': embedding.join(','),
        'embedding_model': model,
        'embedding_dimensions': embedding.length,
      },
      where: 'id = ?',
      whereArgs: [id],
    );
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
    List<double>? embedding, {
    String? embeddingModel,
  }) async {
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
      'embedding_model': embeddingModel,
      'embedding_dimensions': embedding?.length,
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

  Future<List<DecisionItem>> similarDecisions(
    List<double> embedding, {
    int limit = 3,
  }) async {
    final rows = await (await database).query(
      'decisions',
      where: "embedding IS NOT NULL AND status != 'pending_outcome'",
    );
    final scored =
        rows
            .map((row) {
              final stored = ((row['embedding'] as String?) ?? '')
                  .split(',')
                  .where((value) => value.isNotEmpty)
                  .map(double.parse)
                  .toList();
              return (
                decision: DecisionItem.fromMap(row),
                score: _cosine(embedding, stored),
              );
            })
            .where((entry) => entry.score >= .4)
            .toList()
          ..sort((a, b) => b.score.compareTo(a.score));
    return scored.take(limit).map((entry) => entry.decision).toList();
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

  Future<int> addTask(int goalId, String title, {DateTime? dueDate}) async {
    final db = await database;
    final id = await db.insert('tasks', {
      'goal_id': goalId,
      'title': title,
      'completed': 0,
      'due_date': dueDate?.millisecondsSinceEpoch,
    });
    await _refreshGoalProgress(db, goalId);
    return id;
  }

  Future<List<TaskItem>> tasksForGoal(int goalId) async {
    final rows = await (await database).query(
      'tasks',
      where: 'goal_id = ?',
      whereArgs: [goalId],
      orderBy: 'id ASC',
    );
    return rows.map(TaskItem.fromMap).toList();
  }

  Future<void> setTaskCompleted(int taskId, bool completed) async {
    final db = await database;
    final rows = await db.query(
      'tasks',
      columns: ['goal_id'],
      where: 'id = ?',
      whereArgs: [taskId],
      limit: 1,
    );
    if (rows.isEmpty) return;
    final goalId = rows.first['goal_id'] as int;
    await db.update(
      'tasks',
      {'completed': completed ? 1 : 0},
      where: 'id = ?',
      whereArgs: [taskId],
    );
    await _refreshGoalProgress(db, goalId);
  }

  Future<void> _refreshGoalProgress(DatabaseExecutor db, int goalId) async {
    final counts = await db.rawQuery(
      '''SELECT COUNT(*) AS total,
         SUM(CASE WHEN completed = 1 THEN 1 ELSE 0 END) AS completed
         FROM tasks WHERE goal_id = ?''',
      [goalId],
    );
    final total = (counts.first['total'] as num?)?.toInt() ?? 0;
    final completed = (counts.first['completed'] as num?)?.toInt() ?? 0;
    final progress = total == 0 ? 0 : ((completed / total) * 100).round();
    await db.update(
      'goals',
      {
        'progress': progress,
        'status': progress == 100 ? 'completed' : 'active',
      },
      where: 'id = ?',
      whereArgs: [goalId],
    );
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

  Future<void> resolveIntervention(int id, String outcome, {int? at}) async {
    await (await database).update(
      'interventions',
      {
        'outcome': outcome,
        'outcome_timestamp': at ?? DateTime.now().millisecondsSinceEpoch,
      },
      where: 'id = ?',
      whereArgs: [id],
    );
  }

  Future<void> dismissUnresolvedInterventions({Duration? olderThan}) async {
    final cutoff = DateTime.now()
        .subtract(olderThan ?? const Duration(seconds: 120))
        .millisecondsSinceEpoch;
    await (await database).update(
      'interventions',
      {
        'outcome': 'dismissed',
        'outcome_timestamp': DateTime.now().millisecondsSinceEpoch,
      },
      where: "outcome = 'shown' AND timestamp <= ?",
      whereArgs: [cutoff],
    );
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

  Future<List<PendingFollowUp>> pendingFollowUps() async {
    final db = await database;
    final now = DateTime.now().millisecondsSinceEpoch;
    final promises = await db.query(
      'promises',
      where: "status = 'pending' AND follow_up_at <= ?",
      whereArgs: [now],
      orderBy: 'follow_up_at ASC',
    );
    final decisions = await db.query(
      'decisions',
      where: "status = 'pending_outcome' AND follow_up_at <= ?",
      whereArgs: [now],
      orderBy: 'follow_up_at ASC',
    );
    return [
      ...promises.map(
        (row) => PendingFollowUp(
          id: row['id'] as int,
          kind: 'promise',
          text: row['text'] as String,
          interventionId: row['intervention_id'] as int?,
        ),
      ),
      ...decisions.map(
        (row) => PendingFollowUp(
          id: row['id'] as int,
          kind: 'decision',
          text: row['question'] as String,
        ),
      ),
    ];
  }

  Future<PendingFollowUp> markFollowUpAsked(PendingFollowUp followUp) async {
    final db = await database;
    final now = DateTime.now().millisecondsSinceEpoch;
    if (followUp.kind == 'promise') {
      await db.update(
        'promises',
        {'status': 'asked'},
        where: 'id = ?',
        whereArgs: [followUp.id],
      );
      if (followUp.interventionId != null) {
        await db.update(
          'interventions',
          {'outcome': 'shown', 'outcome_timestamp': null},
          where: 'id = ?',
          whereArgs: [followUp.interventionId],
        );
      }
      return followUp;
    } else {
      await db.update(
        'decisions',
        {'outcome_asked_at': now},
        where: 'id = ?',
        whereArgs: [followUp.id],
      );
      final interventionId = await logIntervention(
        'decision_followup',
        followUp.text,
        followUp.text,
      );
      return PendingFollowUp(
        id: followUp.id,
        kind: followUp.kind,
        text: followUp.text,
        interventionId: interventionId,
      );
    }
  }

  Future<void> resolveFollowUp(PendingFollowUp followUp, String answer) async {
    final db = await database;
    final lower = answer.trim().toLowerCase();
    final stopAsking =
        lower.contains('stop asking me') ||
        lower.contains('never mind') ||
        lower == 'stop';
    if (followUp.kind == 'promise') {
      final status = stopAsking
          ? 'dismissed'
          : _isAffirmative(lower)
          ? 'confirmed_done'
          : _isNegative(lower)
          ? 'confirmed_not_done'
          : 'asked';
      await db.update(
        'promises',
        {'status': status},
        where: 'id = ?',
        whereArgs: [followUp.id],
      );
    } else if (!stopAsking) {
      final status =
          lower.contains('abandon') ||
              lower.contains('never did') ||
              lower.contains('dropped')
          ? 'abandoned'
          : lower.contains('good') ||
                lower.contains('well') ||
                lower.contains('great') ||
                lower.contains('worked')
          ? 'resolved_good'
          : lower.contains('bad') ||
                lower.contains('poorly') ||
                lower.contains('regret') ||
                lower.contains('failed')
          ? 'resolved_bad'
          : 'resolved_mixed';
      await setDecisionOutcome(followUp.id, status, answer.trim());
    }
    if (followUp.interventionId != null) {
      await resolveIntervention(
        followUp.interventionId!,
        stopAsking ? 'dismissed' : 'acted_on',
      );
    }
  }

  Future<void> expireStalePromises({
    Duration age = const Duration(days: 45),
  }) async {
    final cutoff = DateTime.now().subtract(age).millisecondsSinceEpoch;
    await (await database).update(
      'promises',
      {'status': 'expired'},
      where: "status = 'pending' AND captured_at <= ?",
      whereArgs: [cutoff],
    );
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

  Future<void> recordMode(AppMode mode, String source) async {
    await (await database).insert('mode_history', {
      'mode': mode.name,
      'source': source,
      'timestamp': DateTime.now().millisecondsSinceEpoch,
    });
  }

  Future<void> recordFeedback({
    required String targetType,
    required String targetId,
    required bool accepted,
    String topic = '',
    String responseStyle = '',
  }) async {
    final db = await database;
    await db.transaction((txn) async {
      await txn.insert('feedback_events', {
        'target_type': targetType,
        'target_id': targetId,
        'accepted': accepted ? 1 : 0,
        'topic': topic,
        'response_style': responseStyle,
        'created_at': DateTime.now().millisecondsSinceEpoch,
      });
      final column = accepted ? 'accepted_suggestions' : 'ignored_suggestions';
      await txn.rawUpdate(
        '''UPDATE preference_profile SET
           total_interactions = total_interactions + 1,
           $column = $column + 1,
           updated_at = ? WHERE id = 1''',
        [DateTime.now().millisecondsSinceEpoch],
      );
    });
  }

  Future<Map<String, Object?>> preferenceProfile() async {
    final rows = await (await database).query(
      'preference_profile',
      where: 'id = 1',
      limit: 1,
    );
    return rows.first;
  }

  Future<void> saveSuggestions(
    Iterable<DashboardRecommendation> suggestions,
  ) async {
    final db = await database;
    final now = DateTime.now().millisecondsSinceEpoch;
    await db.transaction((txn) async {
      await txn.delete('suggestion_scores');
      for (final suggestion in suggestions) {
        await txn.insert('suggestion_scores', {
          'text': suggestion.text,
          'category': suggestion.category,
          'score': suggestion.score,
          'confidence': .4,
          'surfaced': 1,
          'accepted': null,
          'created_at': now,
        });
      }
    });
  }

  Future<int> addInsight(String text, String category) async {
    return (await database).insert('insights', {
      'text': text,
      'category': category,
      'created_at': DateTime.now().millisecondsSinceEpoch,
    });
  }

  Future<List<InsightItem>> insights({int limit = 20}) async {
    final rows = await (await database).query(
      'insights',
      orderBy: 'created_at DESC',
      limit: limit,
    );
    return rows.map(InsightItem.fromMap).toList();
  }

  Future<CognitiveMetrics> computeMetrics() async {
    final db = await database;
    final since = DateTime.now()
        .subtract(const Duration(days: 7))
        .millisecondsSinceEpoch;
    Future<int> count(
      String table, [
      String? where,
      List<Object?>? args,
    ]) async {
      return Sqflite.firstIntValue(
            await db.rawQuery(
              'SELECT COUNT(*) FROM $table${where == null ? '' : ' WHERE $where'}',
              args,
            ),
          ) ??
          0;
    }

    final sessions = await count('voice_sessions', 'created_at >= ?', [since]);
    final contexts = await count('context_events', 'created_at >= ?', [since]);
    final memoriesCount = await count('semantic_memories', 'timestamp >= ?', [
      since,
    ]);
    final decisionsCount = await count('decisions', 'created_at >= ?', [since]);
    final resolved = await count(
      'decisions',
      "created_at >= ? AND status != 'pending_outcome'",
      [since],
    );
    final tasksTotal = await count('tasks');
    final tasksDone = await count('tasks', 'completed = 1');
    int score(int value) => value.clamp(0, 100);
    return CognitiveMetrics(
      focus: score(45 + contexts * 2),
      energy: score(50 + sessions * 4),
      learningVelocity: score(35 + memoriesCount * 6),
      decisionReadiness: score(
        45 +
            decisionsCount * 4 +
            (decisionsCount == 0 ? 0 : resolved * 20 ~/ decisionsCount),
      ),
      memoryActivity: score(30 + memoriesCount * 7),
      aiConfidence: score(55 + resolved * 5),
      dailyProgress: tasksTotal == 0 ? 0 : score(tasksDone * 100 ~/ tasksTotal),
    );
  }

  Future<void> purgeExpiredMemory(int retentionDays) async {
    if (retentionDays <= 0) return;
    final cutoff = DateTime.now()
        .subtract(Duration(days: retentionDays))
        .millisecondsSinceEpoch;
    final db = await database;
    await db.transaction((txn) async {
      await txn.delete(
        'memories',
        where: 'created_at < ?',
        whereArgs: [cutoff],
      );
      await txn.delete(
        'semantic_memories',
        where: "timestamp < ? AND layer_name != 'values'",
        whereArgs: [cutoff],
      );
      await txn.delete(
        'context_events',
        where: 'created_at < ?',
        whereArgs: [cutoff],
      );
    });
  }

  Future<Map<String, List<Map<String, Object?>>>> exportAll() async {
    const tables = [
      'profile',
      'conversations',
      'messages',
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
      'preference_profile',
      'mode_history',
      'suggestion_scores',
      'interventions',
      'promises',
    ];
    final db = await database;
    return {for (final table in tables) table: await db.query(table)};
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
      'analytics',
      'recommendations',
      'suggestion_scores',
    ];
    await db.transaction((txn) async {
      for (final table in tables) {
        await txn.delete(table);
      }
      await txn.update('profile', {
        'name': 'You',
        'cognitive_state': 'Ready',
        'active_coach': 'Founder Coach',
        'listening_mode': 'push_to_talk',
        'wake_word_enabled': 0,
        'continuous_listening': 0,
        'updated_at': DateTime.now().millisecondsSinceEpoch,
      }, where: 'id = 1');
      await txn.update('preference_profile', {
        'short_response_weight': .5,
        'business_topic_weight': .5,
        'goal_topic_weight': .5,
        'decision_topic_weight': .5,
        'learning_topic_weight': .5,
        'interruption_tolerance': .5,
        'night_activity_weight': .5,
        'morning_activity_weight': .5,
        'proactive_insight_threshold': .55,
        'total_interactions': 0,
        'accepted_suggestions': 0,
        'ignored_suggestions': 0,
        'updated_at': DateTime.now().millisecondsSinceEpoch,
      }, where: 'id = 1');
    });
  }
}

class _LegacyTable {
  const _LegacyTable(this.source, this.target, this.columns);

  final String source;
  final String target;
  final Map<String, String> columns;
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

bool _isAffirmative(String text) =>
    text.contains('yes') ||
    text.contains('done') ||
    text.contains('did it') ||
    text.contains('i did') ||
    text.contains('finished');

bool _isNegative(String text) =>
    text.contains('no') ||
    text.contains("didn't") ||
    text.contains('did not') ||
    text.contains('not yet') ||
    text.contains("haven't");

Object? _convertLegacyValue(String table, String column, Object? value) {
  if (value is! String) return value;
  if (table == 'decisions' &&
      const {
        'pros',
        'cons',
        'risks',
        'opportunities',
        'missingInfo',
      }.contains(column)) {
    try {
      final decoded = jsonDecode(value);
      if (decoded is List) {
        return decoded.whereType<String>().join('\u001f');
      }
    } catch (_) {
      return value;
    }
  }
  if ((table == 'decisions' || table == 'semantic_memories') &&
      column == 'embedding') {
    try {
      final decoded = jsonDecode(value);
      if (decoded is List) {
        return decoded.whereType<num>().join(',');
      }
    } catch (_) {
      return value.replaceAll('[', '').replaceAll(']', '');
    }
  }
  return value;
}
