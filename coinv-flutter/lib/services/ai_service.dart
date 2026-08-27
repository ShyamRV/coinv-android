import 'dart:convert';

import 'package:http/http.dart' as http;

import '../data/models.dart';

class AiService {
  AiService({http.Client? client}) : _client = client ?? http.Client();

  static const asiKey = String.fromEnvironment('ASI_ONE_API_KEY');
  static const geminiKey = String.fromEnvironment('GEMINI_API_KEY');
  final http.Client _client;

  Future<String> chat({
    required String systemPrompt,
    required List<ChatMessage> messages,
    int maxTokens = 300,
  }) async {
    if (asiKey.isEmpty) {
      throw const AiException(
        'ASI:One key missing. Build with '
        '--dart-define=ASI_ONE_API_KEY=your_key.',
      );
    }
    final response = await _client
        .post(
          Uri.parse('https://api.asi1.ai/v1/chat/completions'),
          headers: {
            'Content-Type': 'application/json',
            'Authorization': 'Bearer $asiKey',
          },
          body: jsonEncode({
            'model': 'asi1-mini',
            'messages': [
              {'role': 'system', 'content': systemPrompt},
              ...messages.map(
                (message) => {'role': message.role, 'content': message.text},
              ),
            ],
            'temperature': .7,
            'stream': false,
            'max_tokens': maxTokens,
          }),
        )
        .timeout(const Duration(seconds: 30));
    final payload = response.body;
    if (response.statusCode == 429) {
      throw const AiException('Rate limited — wait a moment and try again.');
    }
    if (response.statusCode < 200 || response.statusCode >= 300) {
      throw AiException('ASI:One error ${response.statusCode}: $payload');
    }
    final json = jsonDecode(payload) as Map<String, dynamic>;
    final choices = json['choices'] as List<dynamic>?;
    if (choices == null || choices.isEmpty) {
      throw const AiException('ASI:One returned no response.');
    }
    return ((choices.first as Map<String, dynamic>)['message']
            as Map<String, dynamic>)['content']
        as String;
  }

  Future<List<double>> embed(String text) async {
    if (geminiKey.isEmpty) {
      // Stable local fallback keeps memory functional without network keys.
      return _localEmbedding(text);
    }
    final response = await _client
        .post(
          Uri.parse(
            'https://generativelanguage.googleapis.com/v1beta/'
            'models/text-embedding-004:embedContent?key=$geminiKey',
          ),
          headers: {'Content-Type': 'application/json'},
          body: jsonEncode({
            'model': 'models/text-embedding-004',
            'content': {
              'parts': [
                {'text': text},
              ],
            },
          }),
        )
        .timeout(const Duration(seconds: 30));
    if (response.statusCode < 200 || response.statusCode >= 300) {
      throw AiException('Gemini embedding error ${response.statusCode}.');
    }
    final json = jsonDecode(response.body) as Map<String, dynamic>;
    final embedding = json['embedding'] as Map<String, dynamic>?;
    final values = embedding?['values'] as List<dynamic>?;
    if (values == null || values.isEmpty) {
      throw const AiException('Gemini returned no embedding.');
    }
    return values.map((value) => (value as num).toDouble()).toList();
  }

  Future<DecisionAnalysis> analyzeDecision({
    required String question,
    required String context,
    required String memoryContext,
  }) async {
    const system = '''
You analyze real decisions. Return ONLY valid JSON with exactly:
{"pros":["specific point"],"cons":["specific point"],"risks":["specific point"],
"opportunities":["specific point"],"missing_information":["specific item"],
"confidence_score":0.0,"recommendation":"direct paragraph"}
Use 2-5 specific items per list. Confidence must be below 0.5 when important
information is missing. Never pad with generic filler.''';
    final response = await chat(
      systemPrompt: system,
      messages: [
        ChatMessage(
          role: 'user',
          text:
              'Decision question: $question\n'
              'User context: $context\n$memoryContext',
        ),
      ],
      maxTokens: 700,
    );
    return parseDecisionAnalysis(response);
  }

  static DecisionAnalysis parseDecisionAnalysis(String raw) {
    var cleaned = raw.trim();
    if (cleaned.startsWith('```')) {
      cleaned = cleaned
          .replaceFirst(RegExp(r'^```(?:json|JSON)?\s*'), '')
          .replaceFirst(RegExp(r'\s*```$'), '');
    }
    final value = jsonDecode(cleaned);
    if (value is! Map<String, dynamic>) {
      throw const FormatException('Decision response must be a JSON object.');
    }
    List<String> list(String key) {
      final items = value[key];
      if (items is! List) throw FormatException('Missing JSON list: $key');
      return items
          .whereType<String>()
          .map((item) => item.trim())
          .where((item) => item.isNotEmpty)
          .toList();
    }

    final recommendation = value['recommendation'];
    final confidence = value['confidence_score'];
    if (recommendation is! String || recommendation.trim().isEmpty) {
      throw const FormatException('Missing decision recommendation.');
    }
    if (confidence is! num) {
      throw const FormatException('Missing decision confidence.');
    }
    return DecisionAnalysis(
      pros: list('pros'),
      cons: list('cons'),
      risks: list('risks'),
      opportunities: list('opportunities'),
      missingInformation: list('missing_information'),
      confidence: confidence.toDouble().clamp(0, 1),
      recommendation: recommendation.trim(),
    );
  }

  static List<double> _localEmbedding(String text) {
    final vector = List<double>.filled(128, 0);
    final words = text
        .toLowerCase()
        .split(RegExp(r'[^a-z0-9]+'))
        .where((word) => word.isNotEmpty);
    for (final word in words) {
      var hash = 0;
      for (final code in word.codeUnits) {
        hash = ((hash * 31) + code) & 0x7fffffff;
      }
      vector[hash % vector.length] += 1;
    }
    return vector;
  }
}

class AiException implements Exception {
  const AiException(this.message);
  final String message;
  @override
  String toString() => message;
}
