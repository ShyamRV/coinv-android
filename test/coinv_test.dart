import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:coinv_app/core/coinv_theme.dart';
import 'package:coinv_app/services/ai_service.dart';

void main() {
  test('decision parser accepts the required schema', () {
    final result = AiService.parseDecisionAnalysis('''
      {
        "pros": ["specific upside"],
        "cons": ["specific downside"],
        "risks": ["specific risk"],
        "opportunities": ["specific opportunity"],
        "missing_information": ["specific unknown"],
        "confidence_score": 0.35,
        "recommendation": "Gather the missing evidence first."
      }
    ''');
    expect(result.confidence, .35);
    expect(result.pros, ['specific upside']);
  });

  test('decision parser rejects malformed responses', () {
    expect(
      () => AiService.parseDecisionAnalysis('not json'),
      throwsFormatException,
    );
    expect(
      () =>
          AiService.parseDecisionAnalysis('{"pros":[],"confidence_score":0.5}'),
      throwsFormatException,
    );
  });

  test(
    'local-only AI keeps embeddings compatible and decisions actionable',
    () async {
      final ai = AiService();
      final embedding = await ai.embed(
        'A reversible product decision',
        allowNetwork: false,
      );
      final analysis = ai.analyzeDecisionLocally(
        question: 'Should I launch?',
        context: '',
      );

      expect(embedding, hasLength(ai.embeddingDimensions));
      expect(ai.embeddingModel(allowNetwork: false), 'local-hash-v2');
      expect(analysis.confidence, lessThan(.5));
      expect(analysis.recommendation, contains('reversible'));
    },
  );

  test('dark and light themes use distinct palettes', () {
    final dark = coinTheme(Brightness.dark);
    final light = coinTheme(Brightness.light);
    expect(dark.brightness, Brightness.dark);
    expect(light.brightness, Brightness.light);
    expect(dark.scaffoldBackgroundColor, isNot(light.scaffoldBackgroundColor));
  });
}
