import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:provider/provider.dart';

import 'package:coinv_app/core/coinv_theme.dart';
import 'package:coinv_app/services/platform_services.dart';
import 'package:coinv_app/state/coinv_controller.dart';
import 'package:coinv_app/ui/dashboard_screen.dart';
import 'package:coinv_app/ui/decisions_screen.dart';
import 'package:coinv_app/ui/memory_screen.dart';
import 'package:coinv_app/ui/profile_screen.dart';
import 'package:coinv_app/ui/timeline_screen.dart';
import 'package:coinv_app/ui/voice_screen.dart';

void main() {
  late CoinVController controller;

  setUp(() {
    controller = CoinVController(audioHandler: CoinVAudioHandler())
      ..initialized = true;
  });

  Future<void> pumpScreen(WidgetTester tester, Widget child) async {
    tester.view.physicalSize = const Size(1080, 2400);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    await tester.pumpWidget(
      ChangeNotifierProvider.value(
        value: controller,
        child: MaterialApp(
          theme: coinTheme(Brightness.light),
          darkTheme: coinTheme(Brightness.dark),
          home: Scaffold(body: child),
        ),
      ),
    );
    await tester.pump();
  }

  testWidgets('dashboard renders cognitive controls and real metrics', (
    tester,
  ) async {
    await pumpScreen(
      tester,
      DashboardScreen(onNavigate: (_) {}, onTimeline: () {}),
    );
    expect(find.text('Daily Summary'), findsOneWidget);
    expect(find.text('COGNITIVE METRICS'), findsOneWidget);
    expect(find.text('PERSONALIZATION'), findsOneWidget);
  });

  testWidgets('all primary Flutter screens render', (tester) async {
    final screens = <Widget>[
      const VoiceScreen(),
      const MemoryScreen(),
      const DecisionsScreen(),
      ProfileScreen(onAboutMe: () {}),
      const TimelineScreen(),
    ];
    for (final screen in screens) {
      await pumpScreen(tester, screen);
      expect(tester.takeException(), isNull);
    }
  });
}
