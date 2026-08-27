import 'package:audio_service/audio_service.dart';
import 'package:flutter/material.dart';
import 'package:flutter_foreground_task/flutter_foreground_task.dart';
import 'package:provider/provider.dart';

import 'core/coinv_theme.dart';
import 'data/models.dart';
import 'services/platform_services.dart';
import 'state/coinv_controller.dart';
import 'ui/about_me_screen.dart';
import 'ui/coin_widgets.dart';
import 'ui/dashboard_screen.dart';
import 'ui/decisions_screen.dart';
import 'ui/memory_screen.dart';
import 'ui/profile_screen.dart';
import 'ui/timeline_screen.dart';
import 'ui/voice_screen.dart';

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();
  FlutterForegroundTask.initCommunicationPort();
  MonitoringService.initialize();
  final audioHandler = await AudioService.init(
    builder: CoinVAudioHandler.new,
    config: const AudioServiceConfig(
      androidNotificationChannelId: 'coinv_media_controls',
      androidNotificationChannelName: 'CoinV headset controls',
      androidNotificationOngoing: false,
      androidStopForegroundOnPause: true,
    ),
  );
  runApp(
    ChangeNotifierProvider(
      create: (_) => CoinVController(audioHandler: audioHandler)..initialize(),
      child: const CoinVApp(),
    ),
  );
}

class CoinVApp extends StatelessWidget {
  const CoinVApp({super.key});

  @override
  Widget build(BuildContext context) {
    final state = context.watch<CoinVController>();
    return MaterialApp(
      title: 'CoinV',
      debugShowCheckedModeBanner: false,
      theme: coinTheme(Brightness.light),
      darkTheme: coinTheme(Brightness.dark),
      themeMode: state.isDark ? ThemeMode.dark : ThemeMode.light,
      home: state.initialized ? const CoinVHome() : const _BootScreen(),
    );
  }
}

class _BootScreen extends StatelessWidget {
  const _BootScreen();

  @override
  Widget build(BuildContext context) => const Scaffold(
    body: Center(
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(Icons.change_history, color: CoinColors.blue, size: 72),
          SizedBox(height: 16),
          Text(
            'CoinV',
            style: TextStyle(fontSize: 24, fontWeight: FontWeight.w600),
          ),
        ],
      ),
    ),
  );
}

class CoinVHome extends StatefulWidget {
  const CoinVHome({super.key});

  @override
  State<CoinVHome> createState() => _CoinVHomeState();
}

class _CoinVHomeState extends State<CoinVHome> with WidgetsBindingObserver {
  int _tab = 0;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed) {
      context.read<CoinVController>().refresh();
    }
  }

  void _openTimeline() {
    Navigator.of(context)
        .push(MaterialPageRoute<void>(builder: (_) => const TimelineScreen()));
  }

  void _openAboutMe() {
    Navigator.of(context)
        .push(MaterialPageRoute<void>(builder: (_) => const AboutMeScreen()));
  }

  @override
  Widget build(BuildContext context) {
    final state = context.watch<CoinVController>();
    final pages = [
      DashboardScreen(onNavigate: _changeTab, onTimeline: _openTimeline),
      const VoiceScreen(),
      const MemoryScreen(),
      const DecisionsScreen(),
      ProfileScreen(onAboutMe: _openAboutMe),
    ];
    return WithForegroundTask(
      child: Scaffold(
        body: SafeArea(
          child: Column(
            children: [
              if (state.mode.mode == AppMode.monitoring)
                InkWell(
                  onTap: () => state.enterIdle('indicator'),
                  child: Container(
                    width: double.infinity,
                    color: CoinColors.blue,
                    padding: const EdgeInsets.symmetric(vertical: 5),
                    child: const Text(
                      'MONITORING ACTIVE · TAP TO STOP',
                      textAlign: TextAlign.center,
                      style: TextStyle(
                        color: Colors.black,
                        fontWeight: FontWeight.bold,
                        fontFamily: 'monospace',
                        fontSize: 10,
                      ),
                    ),
                  ),
                ),
              if (state.visibleError != null)
                ErrorBanner(
                  message: state.visibleError!,
                  onDismiss: state.clearError,
                ),
              Expanded(
                child: IndexedStack(index: _tab, children: pages),
              ),
            ],
          ),
        ),
        bottomNavigationBar: NavigationBar(
          selectedIndex: _tab,
          onDestinationSelected: _changeTab,
          destinations: const [
            NavigationDestination(
              icon: Icon(Icons.home_outlined),
              selectedIcon: Icon(Icons.home),
              label: 'Dashboard',
            ),
            NavigationDestination(
              icon: Icon(Icons.mic_none),
              selectedIcon: Icon(Icons.mic),
              label: 'Voice',
            ),
            NavigationDestination(
              icon: Icon(Icons.psychology_outlined),
              selectedIcon: Icon(Icons.psychology),
              label: 'Memory',
            ),
            NavigationDestination(
              icon: Icon(Icons.lightbulb_outline),
              selectedIcon: Icon(Icons.lightbulb),
              label: 'Decisions',
            ),
            NavigationDestination(
              icon: Icon(Icons.person_outline),
              selectedIcon: Icon(Icons.person),
              label: 'Profile',
            ),
          ],
        ),
      ),
    );
  }

  void _changeTab(int value) {
    setState(() => _tab = value);
  }
}
