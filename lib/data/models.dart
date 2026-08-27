enum AppMode { idle, listening, monitoring }

enum VoicePhase { none, capturing, thinking, speaking, error }

class ModeState {
  const ModeState({
    this.mode = AppMode.idle,
    this.phase = VoicePhase.none,
    this.error,
    this.activatedBy = 'system',
  });

  final AppMode mode;
  final VoicePhase phase;
  final String? error;
  final String activatedBy;

  ModeState copyWith({
    AppMode? mode,
    VoicePhase? phase,
    String? error,
    bool clearError = false,
    String? activatedBy,
  }) => ModeState(
    mode: mode ?? this.mode,
    phase: phase ?? this.phase,
    error: clearError ? null : error ?? this.error,
    activatedBy: activatedBy ?? this.activatedBy,
  );

  String get label => switch (phase) {
    VoicePhase.thinking => 'THINKING',
    VoicePhase.speaking => 'SPEAKING',
    _ => mode.name.toUpperCase(),
  };
}

class ChatMessage {
  ChatMessage({required this.role, required this.text, DateTime? timestamp})
    : timestamp = timestamp ?? DateTime.now();

  final String role;
  final String text;
  final DateTime timestamp;

  Map<String, Object?> toMap(int conversationId) => {
    'conversation_id': conversationId,
    'role': role,
    'text': text,
    'timestamp': timestamp.millisecondsSinceEpoch,
  };
}

class MemoryItem {
  const MemoryItem({
    required this.id,
    required this.title,
    required this.content,
    required this.category,
    required this.tags,
    required this.createdAt,
  });

  final int id;
  final String title;
  final String content;
  final String category;
  final String tags;
  final int createdAt;

  factory MemoryItem.fromMap(Map<String, Object?> row) => MemoryItem(
    id: row['id'] as int,
    title: row['title'] as String,
    content: row['content'] as String,
    category: row['category'] as String,
    tags: row['tags'] as String,
    createdAt: row['created_at'] as int,
  );
}

class SemanticMemory {
  const SemanticMemory({
    required this.id,
    required this.content,
    required this.layer,
    required this.embedding,
    required this.sourceType,
    required this.timestamp,
    this.sourceId,
    this.importance = .5,
  });

  final int id;
  final String content;
  final String layer;
  final List<double> embedding;
  final String sourceType;
  final int? sourceId;
  final int timestamp;
  final double importance;

  factory SemanticMemory.fromMap(Map<String, Object?> row) => SemanticMemory(
    id: row['id'] as int,
    content: row['content'] as String,
    layer: row['layer_name'] as String,
    embedding: ((row['embedding'] as String?) ?? '')
        .split(',')
        .where((value) => value.isNotEmpty)
        .map(double.parse)
        .toList(),
    sourceType: row['source_type'] as String,
    sourceId: row['source_id'] as int?,
    timestamp: row['timestamp'] as int,
    importance: (row['importance'] as num).toDouble(),
  );
}

class DecisionItem {
  const DecisionItem({
    required this.id,
    required this.question,
    required this.context,
    required this.pros,
    required this.cons,
    required this.risks,
    required this.opportunities,
    required this.missingInformation,
    required this.recommendation,
    required this.confidence,
    required this.status,
    required this.createdAt,
    this.outcome,
    this.embedding = const [],
  });

  final int id;
  final String question;
  final String context;
  final List<String> pros;
  final List<String> cons;
  final List<String> risks;
  final List<String> opportunities;
  final List<String> missingInformation;
  final String recommendation;
  final double confidence;
  final String status;
  final String? outcome;
  final int createdAt;
  final List<double> embedding;

  factory DecisionItem.fromMap(Map<String, Object?> row) => DecisionItem(
    id: row['id'] as int,
    question: row['question'] as String,
    context: row['context_notes'] as String,
    pros: _decodeList(row['pros']),
    cons: _decodeList(row['cons']),
    risks: _decodeList(row['risks']),
    opportunities: _decodeList(row['opportunities']),
    missingInformation: _decodeList(row['missing_info']),
    recommendation: row['recommendation'] as String,
    confidence: (row['confidence'] as num).toDouble(),
    status: row['status'] as String,
    outcome: row['outcome'] as String?,
    createdAt: row['created_at'] as int,
    embedding: ((row['embedding'] as String?) ?? '')
        .split(',')
        .where((value) => value.isNotEmpty)
        .map(double.parse)
        .toList(),
  );
}

List<String> _decodeList(Object? value) => ((value as String?) ?? '')
    .split('\u001f')
    .where((item) => item.trim().isNotEmpty)
    .toList();

String encodeList(List<String> value) => value.join('\u001f');

class DecisionAnalysis {
  const DecisionAnalysis({
    required this.pros,
    required this.cons,
    required this.risks,
    required this.opportunities,
    required this.missingInformation,
    required this.confidence,
    required this.recommendation,
  });

  final List<String> pros;
  final List<String> cons;
  final List<String> risks;
  final List<String> opportunities;
  final List<String> missingInformation;
  final double confidence;
  final String recommendation;
}

class GoalItem {
  const GoalItem({
    required this.id,
    required this.title,
    required this.description,
    required this.progress,
    required this.status,
  });

  final int id;
  final String title;
  final String description;
  final int progress;
  final String status;

  factory GoalItem.fromMap(Map<String, Object?> row) => GoalItem(
    id: row['id'] as int,
    title: row['title'] as String,
    description: row['description'] as String,
    progress: row['progress'] as int,
    status: row['status'] as String,
  );
}

class TaskItem {
  const TaskItem({
    required this.id,
    required this.goalId,
    required this.title,
    required this.completed,
    this.dueDate,
  });

  final int id;
  final int goalId;
  final String title;
  final bool completed;
  final int? dueDate;

  factory TaskItem.fromMap(Map<String, Object?> row) => TaskItem(
    id: row['id'] as int,
    goalId: row['goal_id'] as int,
    title: row['title'] as String,
    completed: (row['completed'] as int) == 1,
    dueDate: row['due_date'] as int?,
  );
}

class InsightItem {
  const InsightItem({
    required this.id,
    required this.text,
    required this.category,
    required this.createdAt,
  });

  final int id;
  final String text;
  final String category;
  final int createdAt;

  factory InsightItem.fromMap(Map<String, Object?> row) => InsightItem(
    id: row['id'] as int,
    text: row['text'] as String,
    category: row['category'] as String,
    createdAt: row['created_at'] as int,
  );
}

class SuggestionItem {
  const SuggestionItem({
    required this.id,
    required this.text,
    required this.category,
    required this.score,
    required this.confidence,
    required this.surfaced,
    this.accepted,
  });

  final int id;
  final String text;
  final String category;
  final double score;
  final double confidence;
  final bool surfaced;
  final bool? accepted;

  factory SuggestionItem.fromMap(Map<String, Object?> row) => SuggestionItem(
    id: row['id'] as int,
    text: row['text'] as String,
    category: row['category'] as String,
    score: (row['score'] as num).toDouble(),
    confidence: (row['confidence'] as num).toDouble(),
    surfaced: (row['surfaced'] as int) == 1,
    accepted: row['accepted'] == null ? null : row['accepted'] == 1,
  );
}

class PendingFollowUp {
  const PendingFollowUp({
    required this.id,
    required this.kind,
    required this.text,
    this.interventionId,
  });

  final int id;
  final String kind;
  final String text;
  final int? interventionId;
}

class CognitiveMetrics {
  const CognitiveMetrics({
    required this.focus,
    required this.energy,
    required this.learningVelocity,
    required this.decisionReadiness,
    required this.memoryActivity,
    required this.aiConfidence,
    required this.dailyProgress,
  });

  final int focus;
  final int energy;
  final int learningVelocity;
  final int decisionReadiness;
  final int memoryActivity;
  final int aiConfidence;
  final int dailyProgress;
}

class DashboardRecommendation {
  const DashboardRecommendation({
    required this.text,
    required this.priority,
    required this.category,
    this.route,
    this.score = .5,
  });

  final String text;
  final int priority;
  final String category;
  final String? route;
  final double score;
}

class PersonalizationStatus {
  const PersonalizationStatus({
    required this.progress,
    required this.confidenceLabel,
  });

  final int progress;
  final String confidenceLabel;
}

class TimelineItem {
  const TimelineItem({
    required this.id,
    required this.type,
    required this.title,
    required this.description,
    required this.timestamp,
  });

  final int id;
  final String type;
  final String title;
  final String description;
  final int timestamp;

  factory TimelineItem.fromMap(Map<String, Object?> row) => TimelineItem(
    id: row['id'] as int,
    type: row['type'] as String,
    title: row['title'] as String,
    description: row['description'] as String,
    timestamp: row['timestamp'] as int,
  );
}

class ContextItem {
  const ContextItem({
    required this.type,
    required this.payload,
    required this.mode,
    required this.createdAt,
  });

  final String type;
  final String payload;
  final String mode;
  final int createdAt;

  factory ContextItem.fromMap(Map<String, Object?> row) => ContextItem(
    type: row['type'] as String,
    payload: row['payload'] as String,
    mode: row['app_mode'] as String,
    createdAt: row['created_at'] as int,
  );
}

class BehaviorStat {
  const BehaviorStat({
    required this.type,
    required this.shown,
    required this.dismissed,
  });

  final String type;
  final int shown;
  final int dismissed;
  bool get active => dismissed < 3;
}
