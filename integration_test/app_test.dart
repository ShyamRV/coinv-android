import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:integration_test/integration_test.dart';

import 'package:coinv_app/main.dart' as app;

void main() {
  IntegrationTestWidgetsFlutterBinding.ensureInitialized();

  testWidgets('cold launch, navigation, and theme switching work', (
    tester,
  ) async {
    await app.main();
    await tester.pump(const Duration(seconds: 3));

    expect(find.text('Dashboard'), findsOneWidget);
    expect(find.text('Daily Summary'), findsOneWidget);

    await tester.tap(find.byIcon(Icons.person_outline));
    await tester.pump(const Duration(seconds: 1));
    expect(find.text('AI COACHES'), findsOneWidget);

    await tester.tap(find.text('Light'));
    await tester.pump(const Duration(seconds: 1));
    expect(
      tester.widget<MaterialApp>(find.byType(MaterialApp)).themeMode,
      ThemeMode.light,
    );
  });
}
