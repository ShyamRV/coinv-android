import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../core/coinv_theme.dart';
import '../data/models.dart';
import '../state/coinv_controller.dart';
import 'coin_widgets.dart';

class VoiceScreen extends StatelessWidget {
  const VoiceScreen({super.key});

  @override
  Widget build(BuildContext context) {
    final state = context.watch<CoinVController>();
    return Column(
      children: [
        Padding(
          padding: const EdgeInsets.all(12),
          child: CoinCard(
            child: Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    const Text(
                      'Mode',
                      style: TextStyle(
                        color: CoinColors.blue,
                        fontFamily: 'monospace',
                        fontSize: 10,
                      ),
                    ),
                    Text(
                      state.mode.mode.name,
                      style: Theme.of(context).textTheme.titleMedium,
                    ),
                  ],
                ),
                if (state.mode.mode == AppMode.monitoring)
                  const Text(
                    'SILENT',
                    style: TextStyle(
                      color: CoinColors.blue,
                      fontFamily: 'monospace',
                    ),
                  ),
              ],
            ),
          ),
        ),
        Expanded(
          child: !state.hasMicPermission
              ? Center(
                  child: Column(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      const Icon(Icons.mic_off_outlined, size: 42),
                      const SizedBox(height: 12),
                      const Text('CoinV needs microphone access.'),
                      TextButton(
                        onPressed: state.requestMicrophone,
                        child: const Text('Grant access'),
                      ),
                    ],
                  ),
                )
              : ListView(
                  padding: const EdgeInsets.symmetric(horizontal: 16),
                  children: [
                    if (state.liveTranscript.isNotEmpty &&
                        state.mode.mode != AppMode.idle)
                      Padding(
                        padding: const EdgeInsets.all(8),
                        child: Text(
                          state.mode.mode == AppMode.monitoring
                              ? '· ${state.liveTranscript}'
                              : state.liveTranscript,
                          style: const TextStyle(
                            color: CoinColors.blue,
                            fontFamily: 'monospace',
                          ),
                        ),
                      ),
                    ...state.messages.map(
                      (message) => _MessageBubble(message: message),
                    ),
                  ],
                ),
        ),
        const SectionTitle('Quick Actions'),
        SizedBox(
          height: 44,
          child: ListView(
            padding: const EdgeInsets.symmetric(horizontal: 12),
            scrollDirection: Axis.horizontal,
            children: [
              _PromptChip(
                'Summarize my day',
                () =>
                    state.sendPrompt('Summarize my cognitive activity today.'),
              ),
              _PromptChip(
                'Analyze a decision',
                () => state.sendPrompt(
                  'Help me analyze an important decision I am facing.',
                ),
              ),
              _PromptChip(
                'Create a goal',
                () => state.sendPrompt(
                  'Help me define a clear goal with actionable steps.',
                ),
              ),
              _PromptChip(
                'Review my notes',
                () => state.sendPrompt(
                  'Review my recent memories and highlight key themes.',
                ),
              ),
            ],
          ),
        ),
        const SizedBox(height: 10),
        Text(
          state.mode.label,
          style: TextStyle(
            color: state.mode.mode == AppMode.idle
                ? Theme.of(context).colorScheme.onSurfaceVariant
                : CoinColors.blue,
            fontFamily: 'monospace',
            letterSpacing: 2,
          ),
        ),
        const SizedBox(height: 8),
        CoinOrb(mode: state.mode, onTap: state.toggleOrb, size: 180),
        const SizedBox(height: 14),
      ],
    );
  }
}

class _MessageBubble extends StatelessWidget {
  const _MessageBubble({required this.message});
  final ChatMessage message;

  @override
  Widget build(BuildContext context) {
    final user = message.role == 'user';
    return Align(
      alignment: user ? Alignment.centerRight : Alignment.centerLeft,
      child: Container(
        constraints: const BoxConstraints(maxWidth: 310),
        margin: const EdgeInsets.symmetric(vertical: 4),
        padding: const EdgeInsets.all(12),
        decoration: BoxDecoration(
          color: user
              ? Theme.of(context).colorScheme.surfaceContainerHighest
              : Theme.of(context).colorScheme.surface,
          border: user
              ? null
              : const Border(
                  left: BorderSide(color: CoinColors.blue, width: 2),
                ),
          borderRadius: BorderRadius.circular(14),
        ),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(message.text),
            if (!user)
              Row(
                mainAxisSize: MainAxisSize.min,
                children: [
                  IconButton(
                    visualDensity: VisualDensity.compact,
                    tooltip: 'Helpful',
                    onPressed: () =>
                        context.read<CoinVController>().recordFeedback(
                          targetType: 'assistant_message',
                          targetId: message.timestamp.millisecondsSinceEpoch
                              .toString(),
                          accepted: true,
                        ),
                    icon: const Icon(Icons.thumb_up_outlined, size: 16),
                  ),
                  IconButton(
                    visualDensity: VisualDensity.compact,
                    tooltip: 'Not helpful',
                    onPressed: () =>
                        context.read<CoinVController>().recordFeedback(
                          targetType: 'assistant_message',
                          targetId: message.timestamp.millisecondsSinceEpoch
                              .toString(),
                          accepted: false,
                        ),
                    icon: const Icon(Icons.thumb_down_outlined, size: 16),
                  ),
                ],
              ),
          ],
        ),
      ),
    );
  }
}

class _PromptChip extends StatelessWidget {
  const _PromptChip(this.label, this.onPressed);
  final String label;
  final VoidCallback onPressed;

  @override
  Widget build(BuildContext context) => Padding(
    padding: const EdgeInsets.only(right: 8),
    child: ActionChip(label: Text(label), onPressed: onPressed),
  );
}
