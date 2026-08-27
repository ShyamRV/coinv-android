import '../data/database.dart';
import '../data/models.dart';

class ContextService {
  ContextService(this._database);

  final CoinVDatabase _database;

  static const _topics = <String, String>{
    'goal': 'goals',
    'decision': 'decisions',
    'business': 'business',
    'startup': 'business',
    'learn': 'learning',
    'memory': 'memory',
    'focus': 'productivity',
    'career': 'career',
    'meeting': 'schedule',
    'plan': 'planning',
  };

  Future<void> recordSpeech(
    String text,
    AppMode mode, {
    String source = 'voice',
  }) async {
    await _database.recordContext('speech_topic', text, source, mode);
    final lower = text.toLowerCase();
    final topics = _topics.entries
        .where((entry) => lower.contains(entry.key))
        .map((entry) => entry.value)
        .toSet();
    for (final topic in topics) {
      await _database.recordContext('intent_pattern', topic, source, mode);
    }
  }

  Future<void> recordNavigation(String route, AppMode mode) =>
      _database.recordContext('navigation', route, 'app', mode);

  Future<void> recordInteraction(String action, String detail, AppMode mode) =>
      _database.recordContext('interaction', '$action:$detail', 'ui', mode);

  bool isExplicitUserRequest(String text) {
    final lower = text.trim().toLowerCase();
    if (lower.isEmpty) return false;
    if (lower.contains('coinv') || lower.contains('hey coin')) return true;
    if (lower.endsWith('?')) return true;
    const starts = [
      'what',
      'how',
      'why',
      'when',
      'where',
      'who',
      'can you',
      'could you',
      'help me',
      'tell me',
      'explain',
      'should i',
      'do i',
      'is it',
    ];
    return starts.any(lower.startsWith);
  }
}
