import '../data/database.dart';
import '../data/models.dart';
import 'ai_service.dart';

class IntelligenceService {
  IntelligenceService(this._database, this._ai);

  final CoinVDatabase _database;
  final AiService _ai;

  Future<String> dailySummary(
    Map<String, Object?> profile,
    CognitiveMetrics metrics,
  ) async {
    final name = (profile['name'] as String?)?.trim();
    final user = name == null || name.isEmpty || name == 'You'
        ? 'Your'
        : '$name, your';
    if (metrics.focus == 45 && metrics.energy == 50) {
      return '$user cognitive loop is quiet today. Start a voice session to activate CoinV.';
    }
    if (metrics.dailyProgress > 0) {
      return '$user focus is ${metrics.focus}% and goal progress is '
          '${metrics.dailyProgress}%. Keep the next action small and visible.';
    }
    return '$user cognitive loop is active. Memory activity is '
        '${metrics.memoryActivity}% and decision readiness is ${metrics.decisionReadiness}%.';
  }

  Future<List<DashboardRecommendation>> recommendations(
    CognitiveMetrics metrics,
    List<GoalItem> goals,
    List<DecisionItem> decisions,
    List<MemoryItem> memories,
  ) async {
    final candidates = <DashboardRecommendation>[
      if (metrics.energy <= 50)
        const DashboardRecommendation(
          text: 'Start Listening mode to activate your cognitive loop.',
          priority: 1,
          category: 'productivity',
          route: 'voice',
        ),
      if (goals.where((goal) => goal.status == 'active').isEmpty)
        const DashboardRecommendation(
          text: 'Create a goal and define its first task.',
          priority: 2,
          category: 'goals',
          route: 'decisions',
        ),
      if (decisions.any((decision) => decision.status == 'pending_outcome'))
        const DashboardRecommendation(
          text: 'Review a pending decision and record its outcome.',
          priority: 1,
          category: 'decisions',
          route: 'decisions',
        ),
      if (memories.isEmpty)
        const DashboardRecommendation(
          text: 'Capture your first idea in Memory Vault.',
          priority: 3,
          category: 'learning',
          route: 'memory',
        ),
      if (metrics.focus >= 60)
        const DashboardRecommendation(
          text: 'Focus is high — protect a block for deep work.',
          priority: 2,
          category: 'productivity',
        ),
    ];
    final profile = await _database.preferenceProfile();
    final hour = DateTime.now().hour;
    double topicWeight(String category) => switch (category) {
      'goals' => (profile['goal_topic_weight'] as num).toDouble(),
      'decisions' => (profile['decision_topic_weight'] as num).toDouble(),
      'learning' => (profile['learning_topic_weight'] as num).toDouble(),
      _ => (profile['business_topic_weight'] as num).toDouble(),
    };

    final ranked = candidates.map((candidate) {
      final timeWeight = hour >= 21 || hour <= 5
          ? (profile['night_activity_weight'] as num).toDouble() * .15
          : hour >= 5 && hour <= 11
          ? (profile['morning_activity_weight'] as num).toDouble() * .15
          : 0.0;
      final score =
          (topicWeight(candidate.category) +
                  timeWeight +
                  (6 - candidate.priority.clamp(1, 5)) * .1)
              .clamp(0.0, 1.0);
      return DashboardRecommendation(
        text: candidate.text,
        priority: candidate.priority,
        category: candidate.category,
        route: candidate.route,
        score: score,
      );
    }).toList()..sort((a, b) => b.score.compareTo(a.score));
    await _database.saveSuggestions(ranked);
    return ranked.take(5).toList();
  }

  Future<PersonalizationStatus> personalizationStatus() async {
    final profile = await _database.preferenceProfile();
    final interactions = (profile['total_interactions'] as num).toInt();
    final accepted = (profile['accepted_suggestions'] as num).toInt();
    final ignored = (profile['ignored_suggestions'] as num).toInt();
    final label = interactions < 10
        ? 'Calibrating'
        : accepted > ignored
        ? 'Adapting well'
        : ignored > accepted * 2
        ? 'Conservative'
        : 'Learning your patterns';
    return PersonalizationStatus(
      progress: interactions.clamp(0, 100),
      confidenceLabel: label,
    );
  }

  Future<void> maybeGenerateDailyInsight({
    required bool allowNetwork,
    required CognitiveMetrics metrics,
  }) async {
    if (!allowNetwork || AiService.asiKey.isEmpty) return;
    final existing = await _database.insights(limit: 1);
    final today = DateTime.now();
    if (existing.isNotEmpty) {
      final created = DateTime.fromMillisecondsSinceEpoch(
        existing.first.createdAt,
      );
      if (created.year == today.year &&
          created.month == today.month &&
          created.day == today.day &&
          existing.first.category == 'daily') {
        return;
      }
    }
    final summary =
        'Focus ${metrics.focus}. Energy ${metrics.energy}. Learning '
        '${metrics.learningVelocity}. Goal progress ${metrics.dailyProgress}.';
    final insight = await _ai.generateDailyInsight(summary);
    await _database.addInsight(insight.trim(), 'daily');
    await _database.addTimeline('insight', 'Daily insight', insight.trim());
  }
}
