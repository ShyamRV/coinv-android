import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../core/coinv_theme.dart';
import '../state/coinv_controller.dart';
import 'coin_widgets.dart';

const _coaches = [
  'Founder Coach',
  'Productivity Coach',
  'Learning Coach',
  'Career Coach',
  'Thinking Coach',
  'Decision Coach',
];

class ProfileScreen extends StatelessWidget {
  const ProfileScreen({required this.onAboutMe, super.key});
  final VoidCallback onAboutMe;

  @override
  Widget build(BuildContext context) {
    final state = context.watch<CoinVController>();
    final settings = state.settings;
    final name = state.userProfile['name'] as String? ?? 'You';
    return ListView(
      padding: const EdgeInsets.only(bottom: 30),
      children: [
        const SectionTitle('Profile'),
        Padding(
          padding: const EdgeInsets.symmetric(horizontal: 20),
          child: CoinCard(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(name, style: Theme.of(context).textTheme.headlineSmall),
                TextButton(
                  onPressed: () => _editName(context, name),
                  child: const Text('Edit name'),
                ),
                Text(
                  'Cognitive State: '
                  '${state.userProfile['cognitive_state'] ?? 'Ready'}',
                  style: const TextStyle(
                    color: CoinColors.blue,
                    fontFamily: 'monospace',
                  ),
                ),
              ],
            ),
          ),
        ),
        const SectionTitle('AI Coaches'),
        Padding(
          padding: const EdgeInsets.symmetric(horizontal: 20),
          child: Wrap(
            spacing: 8,
            runSpacing: 6,
            children: _coaches
                .map(
                  (coach) => ChoiceChip(
                    label: Text(coach),
                    selected: state.userProfile['active_coach'] == coach,
                    onSelected: (_) => state.setCoach(coach),
                  ),
                )
                .toList(),
          ),
        ),
        const SectionTitle('Privacy & Monitoring'),
        Padding(
          padding: const EdgeInsets.symmetric(horizontal: 20),
          child: CoinCard(
            child: Column(
              children: [
                _SwitchRow(
                  label: 'Allow monitoring mode',
                  value: settings.monitoringEnabled,
                  onChanged: (value) => state.updateSettings(
                    settings.copyWith(monitoringEnabled: value),
                  ),
                ),
                _SwitchRow(
                  label: 'Local-only processing',
                  value: settings.localOnlyProcessing,
                  onChanged: (value) => state.updateSettings(
                    settings.copyWith(localOnlyProcessing: value),
                  ),
                ),
                ListTile(
                  contentPadding: EdgeInsets.zero,
                  title: const Text('Stop monitoring now'),
                  trailing: TextButton(
                    onPressed: () => state.enterIdle('profile'),
                    child: const Text(
                      'Disable',
                      style: TextStyle(color: CoinColors.warning),
                    ),
                  ),
                ),
              ],
            ),
          ),
        ),
        const SectionTitle('Listening'),
        Padding(
          padding: const EdgeInsets.symmetric(horizontal: 20),
          child: Wrap(
            spacing: 8,
            children:
                const [
                  ('off', 'Off'),
                  ('push_to_talk', 'Push To Talk'),
                  ('wake_word', 'Wake Word'),
                  ('always_listening', 'Always On'),
                ].map((item) {
                  final selected =
                      state.userProfile['listening_mode'] == item.$1;
                  return ChoiceChip(
                    label: Text(item.$2),
                    selected: selected,
                    onSelected: (_) => state.updateListeningMode(item.$1),
                  );
                }).toList(),
          ),
        ),
        const SectionTitle('About Me'),
        Padding(
          padding: const EdgeInsets.symmetric(horizontal: 20),
          child: CoinCard(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                const Text(
                  'Saved as value memories and injected into every AI call.',
                ),
                TextButton(
                  onPressed: onAboutMe,
                  child: const Text('Edit About Me'),
                ),
              ],
            ),
          ),
        ),
        const SectionTitle('What CoinV knows about you'),
        Padding(
          padding: const EdgeInsets.symmetric(horizontal: 20),
          child: CoinCard(
            child: state.valueMemories.isEmpty
                ? const Text('No value memories yet.')
                : Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: state.valueMemories
                        .map(
                          (memory) => Padding(
                            padding: const EdgeInsets.symmetric(vertical: 4),
                            child: Text(memory.content),
                          ),
                        )
                        .toList(),
                  ),
          ),
        ),
        const SectionTitle('Active Behaviors'),
        Padding(
          padding: const EdgeInsets.symmetric(horizontal: 20),
          child: CoinCard(
            child: Column(
              children: state.behaviorStats
                  .map(
                    (stat) => ListTile(
                      contentPadding: EdgeInsets.zero,
                      title: Text(_behaviorLabel(stat.type)),
                      subtitle: Text(
                        '${stat.shown} shown · ${stat.dismissed} dismissed',
                      ),
                      trailing: Text(
                        stat.active ? 'active' : 'quiet for you',
                        style: TextStyle(
                          color: stat.active
                              ? CoinColors.blue
                              : CoinColors.muted,
                          fontFamily: 'monospace',
                          fontSize: 11,
                        ),
                      ),
                    ),
                  )
                  .toList(),
            ),
          ),
        ),
        const SectionTitle('Settings'),
        Padding(
          padding: const EdgeInsets.symmetric(horizontal: 20),
          child: CoinCard(
            child: Column(
              children: [
                ListTile(
                  contentPadding: EdgeInsets.zero,
                  title: const Text('Theme'),
                  trailing: SegmentedButton<String>(
                    segments: const [
                      ButtonSegment(value: 'dark', label: Text('Dark')),
                      ButtonSegment(value: 'light', label: Text('Light')),
                      ButtonSegment(value: 'system', label: Text('System')),
                    ],
                    selected: {settings.theme},
                    onSelectionChanged: (selection) => state.updateSettings(
                      settings.copyWith(theme: selection.first),
                    ),
                  ),
                ),
                _SwitchRow(
                  label: 'Privacy analytics',
                  value: settings.privacyAnalytics,
                  onChanged: (value) => state.updateSettings(
                    settings.copyWith(privacyAnalytics: value),
                  ),
                ),
                _SwitchRow(
                  label: 'Notifications',
                  value: settings.notificationsEnabled,
                  onChanged: (value) => state.updateSettings(
                    settings.copyWith(notificationsEnabled: value),
                  ),
                ),
                ListTile(
                  contentPadding: EdgeInsets.zero,
                  title: const Text('Memory retention'),
                  subtitle: const Text('Value memories are always retained'),
                  trailing: DropdownButton<int>(
                    value: settings.memoryRetentionDays,
                    items: const [
                      DropdownMenuItem(value: 30, child: Text('30 days')),
                      DropdownMenuItem(value: 90, child: Text('90 days')),
                      DropdownMenuItem(value: 365, child: Text('1 year')),
                      DropdownMenuItem(value: 3650, child: Text('Keep')),
                    ],
                    onChanged: (value) {
                      if (value != null) {
                        state.updateSettings(
                          settings.copyWith(memoryRetentionDays: value),
                        );
                      }
                    },
                  ),
                ),
                ListTile(
                  contentPadding: EdgeInsets.zero,
                  title: const Text('Data export'),
                  trailing: TextButton(
                    onPressed: state.exportData,
                    child: const Text('Export'),
                  ),
                ),
                ListTile(
                  contentPadding: EdgeInsets.zero,
                  title: const Text('Clear memory'),
                  trailing: TextButton(
                    onPressed: () => _confirm(
                      context,
                      'Clear all memories and About Me facts?',
                      state.clearMemory,
                    ),
                    child: const Text(
                      'Clear all',
                      style: TextStyle(color: CoinColors.warning),
                    ),
                  ),
                ),
                ListTile(
                  contentPadding: EdgeInsets.zero,
                  title: const Text('Reset app'),
                  trailing: TextButton(
                    onPressed: () => _confirm(
                      context,
                      'Reset all CoinV data and settings?',
                      state.resetApp,
                    ),
                    child: const Text(
                      'Reset',
                      style: TextStyle(color: CoinColors.error),
                    ),
                  ),
                ),
              ],
            ),
          ),
        ),
      ],
    );
  }

  Future<void> _editName(BuildContext context, String existing) async {
    final controller = TextEditingController(text: existing);
    final state = context.read<CoinVController>();
    await showDialog<void>(
      context: context,
      builder: (dialogContext) => AlertDialog(
        title: const Text('Your name'),
        content: TextField(controller: controller, autofocus: true),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(dialogContext),
            child: const Text('Cancel'),
          ),
          FilledButton(
            onPressed: () async {
              await state.updateName(controller.text);
              if (dialogContext.mounted) Navigator.pop(dialogContext);
            },
            child: const Text('Save'),
          ),
        ],
      ),
    );
    controller.dispose();
  }

  Future<void> _confirm(
    BuildContext context,
    String prompt,
    Future<void> Function() action,
  ) async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (dialogContext) => AlertDialog(
        title: Text(prompt),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(dialogContext, false),
            child: const Text('Cancel'),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(dialogContext, true),
            child: const Text('Confirm'),
          ),
        ],
      ),
    );
    if (confirmed == true) await action();
  }
}

class _SwitchRow extends StatelessWidget {
  const _SwitchRow({
    required this.label,
    required this.value,
    required this.onChanged,
  });
  final String label;
  final bool value;
  final ValueChanged<bool> onChanged;

  @override
  Widget build(BuildContext context) => SwitchListTile(
    contentPadding: EdgeInsets.zero,
    title: Text(label),
    value: value,
    onChanged: onChanged,
  );
}

String _behaviorLabel(String type) => switch (type) {
  'devils_advocate' => "Devil's advocate",
  'bias_spotter' => 'Bias spotter',
  'promise_tracker' => 'Promise tracker',
  'commitment_guard' => 'Commitment guard',
  'decision_followup' => 'Decision follow-up',
  _ => type,
};
