import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../core/coinv_theme.dart';
import '../data/models.dart';
import '../state/coinv_controller.dart';
import 'coin_widgets.dart';

class DashboardScreen extends StatelessWidget {
  const DashboardScreen({
    required this.onNavigate,
    required this.onTimeline,
    super.key,
  });
  final ValueChanged<int> onNavigate;
  final VoidCallback onTimeline;

  @override
  Widget build(BuildContext context) {
    final state = context.watch<CoinVController>();
    final name = state.userProfile['name'] as String? ?? 'Shyam';
    final greeting = switch (DateTime.now().hour) {
      >= 5 && <= 11 => 'Good Morning',
      >= 12 && <= 16 => 'Good Afternoon',
      >= 17 && <= 20 => 'Good Evening',
      _ => 'Good Night',
    };
    final sessions = state.timeline
        .where((item) => item.type == 'voice')
        .length;
    return ListView(
      padding: const EdgeInsets.fromLTRB(20, 12, 20, 24),
      children: [
        Text(
          '$greeting, $name',
          textAlign: TextAlign.center,
          style: Theme.of(context).textTheme.headlineSmall,
        ),
        const SizedBox(height: 4),
        Text(
          'C O G N I T I V E   O P E R A T I N G   S Y S T E M',
          textAlign: TextAlign.center,
          style: TextStyle(
            fontFamily: 'monospace',
            fontSize: 10,
            color: Theme.of(context).colorScheme.onSurfaceVariant,
          ),
        ),
        const SizedBox(height: 22),
        ModeCard(
          mode: state.mode,
          onListening: () => state.enterListening('dashboard'),
          onMonitoring: () => state.enterMonitoring('dashboard'),
          onStop: () => state.enterIdle('dashboard'),
        ),
        const SizedBox(height: 12),
        Center(
          child: CoinOrb(mode: state.mode, onTap: state.toggleOrb),
        ),
        const SizedBox(height: 8),
        Text(
          state.mode.label,
          textAlign: TextAlign.center,
          style: TextStyle(
            color: state.mode.mode == AppMode.idle
                ? Theme.of(context).colorScheme.onSurfaceVariant
                : CoinColors.blue,
            fontFamily: 'monospace',
            letterSpacing: 2,
          ),
        ),
        const SizedBox(height: 18),
        CoinCard(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              const Text(
                'Daily Summary',
                style: TextStyle(
                  color: CoinColors.blue,
                  fontFamily: 'monospace',
                  fontSize: 11,
                ),
              ),
              const SizedBox(height: 8),
              Text(
                sessions == 0 && state.memories.isEmpty
                    ? '$name, your cognitive loop is quiet. Single-tap the '
                          'headset for Listening, double-tap for Monitoring.'
                    : 'Good progress, $name — $sessions voice sessions and '
                          '${state.memories.length} memories recorded.',
              ),
            ],
          ),
        ),
        const SectionTitle('Recent Context'),
        if (state.recentContext.isEmpty)
          const CoinCard(
            child: Text('Context builds during monitoring and conversations.'),
          )
        else
          ...state.recentContext
              .take(5)
              .map(
                (item) => Padding(
                  padding: const EdgeInsets.only(bottom: 8),
                  child: CoinCard(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          item.type.replaceAll('_', ' ').toUpperCase(),
                          style: const TextStyle(
                            color: CoinColors.blue,
                            fontFamily: 'monospace',
                            fontSize: 10,
                          ),
                        ),
                        const SizedBox(height: 4),
                        Text(
                          item.payload,
                          maxLines: 3,
                          overflow: TextOverflow.ellipsis,
                        ),
                      ],
                    ),
                  ),
                ),
              ),
        const SectionTitle('Cognitive Metrics'),
        SizedBox(
          height: 112,
          child: ListView(
            scrollDirection: Axis.horizontal,
            children: [
              _Metric('Focus', '${minOf(100, 50 + sessions * 5)}%'),
              _Metric('Energy', '${minOf(100, 55 + sessions * 3)}%'),
              _Metric('Goals', '${state.goals.length}'),
              _Metric('Memory', '${state.memories.length}'),
            ],
          ),
        ),
        if (state.memories.isNotEmpty) ...[
          const SectionTitle('Memory Highlights'),
          ...state.memories
              .take(3)
              .map(
                (memory) => Padding(
                  padding: const EdgeInsets.only(bottom: 8),
                  child: CoinCard(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          memory.title,
                          style: Theme.of(context).textTheme.titleMedium,
                        ),
                        Text(
                          memory.content,
                          maxLines: 2,
                          overflow: TextOverflow.ellipsis,
                        ),
                      ],
                    ),
                  ),
                ),
              ),
        ],
        const SectionTitle('Timeline'),
        if (state.timeline.isEmpty)
          const CoinCard(child: Text('Your timeline builds as you interact.'))
        else
          ...state.timeline
              .take(5)
              .map(
                (item) => ListTile(
                  leading: const Icon(
                    Icons.circle,
                    size: 10,
                    color: CoinColors.blue,
                  ),
                  title: Text(item.title),
                  subtitle: Text(item.description),
                ),
              ),
        Align(
          alignment: Alignment.centerRight,
          child: TextButton(
            onPressed: onTimeline,
            child: const Text('View full timeline'),
          ),
        ),
        const SectionTitle('Quick Actions'),
        Wrap(
          spacing: 8,
          runSpacing: 8,
          children: [
            ActionChip(
              label: const Text('Start Listening'),
              onPressed: () => onNavigate(1),
            ),
            ActionChip(
              label: const Text('Capture idea'),
              onPressed: () => onNavigate(2),
            ),
            ActionChip(
              label: const Text('Analyze decision'),
              onPressed: () => onNavigate(3),
            ),
            ActionChip(
              label: const Text('Create goal'),
              onPressed: () => onNavigate(3),
            ),
          ],
        ),
      ],
    );
  }
}

class _Metric extends StatelessWidget {
  const _Metric(this.label, this.value);
  final String label;
  final String value;

  @override
  Widget build(BuildContext context) => Container(
    width: 130,
    margin: const EdgeInsets.only(right: 10),
    child: CoinCard(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(label),
          const Spacer(),
          Text(
            value,
            style: const TextStyle(
              color: CoinColors.blue,
              fontFamily: 'monospace',
              fontSize: 22,
            ),
          ),
        ],
      ),
    ),
  );
}

int minOf(int a, int b) => a < b ? a : b;
