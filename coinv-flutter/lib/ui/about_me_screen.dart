import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../core/coinv_theme.dart';
import '../state/coinv_controller.dart';
import 'coin_widgets.dart';

const _fields = [
  (
    id: -1,
    label: "What's your name?",
    hint: 'How should CoinV address you?',
    prefix: "User's name is ",
  ),
  (
    id: -2,
    label: 'What are you working on right now?',
    hint: 'Current role, project, or focus',
    prefix: 'User is currently working on: ',
  ),
  (
    id: -3,
    label: 'What matters most to you right now?',
    hint: 'Values, priorities, what you are optimizing for',
    prefix: 'User has stated this matters most right now: ',
  ),
  (
    id: -4,
    label: 'Anything CoinV should always keep in mind?',
    hint: 'Preferences, constraints, persistent context',
    prefix: 'User wants CoinV to always keep in mind: ',
  ),
];

class AboutMeScreen extends StatefulWidget {
  const AboutMeScreen({super.key});

  @override
  State<AboutMeScreen> createState() => _AboutMeScreenState();
}

class _AboutMeScreenState extends State<AboutMeScreen> {
  final Map<int, TextEditingController> _controllers = {};
  final Map<int, bool> _saved = {};
  bool _seeded = false;

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    if (_seeded) return;
    final state = context.read<CoinVController>();
    for (final field in _fields) {
      final existing = state.valueMemories
          .where((memory) => memory.sourceId == field.id)
          .firstOrNull;
      _controllers[field.id] = TextEditingController(
        text: existing?.content.replaceFirst(field.prefix, '') ?? '',
      );
    }
    _seeded = true;
  }

  @override
  void dispose() {
    for (final controller in _controllers.values) {
      controller.dispose();
    }
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final state = context.watch<CoinVController>();
    return Scaffold(
      appBar: AppBar(title: const Text('About Me')),
      body: ListView(
        padding: const EdgeInsets.only(bottom: 30),
        children: [
          const Padding(
            padding: EdgeInsets.fromLTRB(20, 4, 20, 12),
            child: Text(
              'These facts are value memories injected into every AI response.',
            ),
          ),
          ..._fields.map(
            (field) => Padding(
              padding: const EdgeInsets.fromLTRB(20, 6, 20, 6),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    field.label,
                    style: Theme.of(context).textTheme.titleMedium,
                  ),
                  const SizedBox(height: 6),
                  TextField(
                    controller: _controllers[field.id],
                    minLines: field.id == -4 ? 3 : 1,
                    maxLines: field.id == -4 ? 5 : 1,
                    decoration: InputDecoration(hintText: field.hint),
                    onChanged: (_) => setState(() => _saved[field.id] = false),
                  ),
                  Row(
                    children: [
                      if (_saved[field.id] == true)
                        const Text(
                          'Saved',
                          style: TextStyle(
                            color: CoinColors.success,
                            fontFamily: 'monospace',
                          ),
                        ),
                      const Spacer(),
                      TextButton(
                        onPressed: () async {
                          final value = _controllers[field.id]!.text.trim();
                          if (value.isEmpty) return;
                          await state.saveAboutMe(
                            field.id,
                            '${field.prefix}$value',
                          );
                          if (mounted) setState(() => _saved[field.id] = true);
                        },
                        child: const Text('Save'),
                      ),
                    ],
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
                              padding: const EdgeInsets.symmetric(vertical: 5),
                              child: Text(
                                '${memory.content}\n'
                                'layer=${memory.layer} · '
                                'sourceId=${memory.sourceId}',
                              ),
                            ),
                          )
                          .toList(),
                    ),
            ),
          ),
        ],
      ),
    );
  }
}
