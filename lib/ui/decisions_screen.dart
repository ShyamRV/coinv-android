import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../core/coinv_theme.dart';
import '../data/models.dart';
import '../state/coinv_controller.dart';
import 'coin_widgets.dart';

class DecisionsScreen extends StatelessWidget {
  const DecisionsScreen({super.key});

  @override
  Widget build(BuildContext context) {
    final state = context.watch<CoinVController>();
    return Scaffold(
      body: ListView(
        padding: const EdgeInsets.only(bottom: 90),
        children: [
          const SectionTitle('Decision Engine'),
          if (state.analyzingDecision)
            const Padding(
              padding: EdgeInsets.symmetric(horizontal: 20),
              child: LinearProgressIndicator(),
            ),
          if (state.decisions.isEmpty)
            const Padding(
              padding: EdgeInsets.symmetric(horizontal: 20),
              child: CoinCard(
                child: Text(
                  'No decisions yet. Add one for structured analysis.',
                ),
              ),
            )
          else
            ...state.decisions.map(
              (decision) => Padding(
                padding: const EdgeInsets.symmetric(
                  horizontal: 20,
                  vertical: 5,
                ),
                child: _DecisionCard(decision: decision),
              ),
            ),
          const SectionTitle('Goals'),
          if (state.goals.isEmpty)
            const Padding(
              padding: EdgeInsets.symmetric(horizontal: 20),
              child: CoinCard(child: Text('No active goals yet.')),
            )
          else
            ...state.goals.map(
              (goal) => Padding(
                padding: const EdgeInsets.symmetric(
                  horizontal: 20,
                  vertical: 5,
                ),
                child: CoinCard(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        goal.title,
                        style: Theme.of(context).textTheme.titleMedium,
                      ),
                      Text(goal.description),
                      const SizedBox(height: 8),
                      LinearProgressIndicator(value: goal.progress / 100),
                      const SizedBox(height: 4),
                      Text(
                        '${goal.progress}% · ${goal.status}',
                        style: const TextStyle(
                          color: CoinColors.blue,
                          fontFamily: 'monospace',
                          fontSize: 11,
                        ),
                      ),
                      ...?state.goalTasks[goal.id]?.map(
                        (task) => CheckboxListTile(
                          contentPadding: EdgeInsets.zero,
                          dense: true,
                          value: task.completed,
                          title: Text(task.title),
                          onChanged: (value) =>
                              state.setGoalTaskCompleted(task, value ?? false),
                        ),
                      ),
                      TextButton.icon(
                        onPressed: () => _addTask(context, goal.id),
                        icon: const Icon(Icons.add_task),
                        label: const Text('Add task'),
                      ),
                    ],
                  ),
                ),
              ),
            ),
        ],
      ),
      floatingActionButton: PopupMenuButton<String>(
        onSelected: (choice) => choice == 'decision'
            ? _createDecision(context)
            : _createGoal(context),
        itemBuilder: (context) => const [
          PopupMenuItem(value: 'decision', child: Text('Analyze decision')),
          PopupMenuItem(value: 'goal', child: Text('Create goal')),
        ],
        child: const FloatingActionButton(
          onPressed: null,
          child: Icon(Icons.add),
        ),
      ),
    );
  }

  Future<void> _createDecision(BuildContext context) async {
    final question = TextEditingController();
    final notes = TextEditingController();
    final state = context.read<CoinVController>();
    await showDialog<void>(
      context: context,
      builder: (dialogContext) => AlertDialog(
        title: const Text('Analyze a decision'),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            TextField(
              controller: question,
              decoration: const InputDecoration(labelText: 'Decision question'),
            ),
            const SizedBox(height: 10),
            TextField(
              controller: notes,
              minLines: 3,
              maxLines: 6,
              decoration: const InputDecoration(
                labelText: 'Context and constraints',
              ),
            ),
          ],
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(dialogContext),
            child: const Text('Cancel'),
          ),
          FilledButton(
            onPressed: () async {
              if (question.text.trim().isEmpty) return;
              Navigator.pop(dialogContext);
              await state.createDecision(question.text, notes.text);
            },
            child: const Text('Analyze'),
          ),
        ],
      ),
    );
    question.dispose();
    notes.dispose();
  }

  Future<void> _createGoal(BuildContext context) async {
    final title = TextEditingController();
    final notes = TextEditingController();
    final state = context.read<CoinVController>();
    await showDialog<void>(
      context: context,
      builder: (dialogContext) => AlertDialog(
        title: const Text('Create goal'),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            TextField(
              controller: title,
              decoration: const InputDecoration(labelText: 'Goal'),
            ),
            const SizedBox(height: 10),
            TextField(
              controller: notes,
              decoration: const InputDecoration(labelText: 'Description'),
            ),
          ],
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(dialogContext),
            child: const Text('Cancel'),
          ),
          FilledButton(
            onPressed: () async {
              if (title.text.trim().isEmpty) return;
              await state.createGoal(title.text, notes.text);
              if (dialogContext.mounted) Navigator.pop(dialogContext);
            },
            child: const Text('Create'),
          ),
        ],
      ),
    );
    title.dispose();
    notes.dispose();
  }

  Future<void> _addTask(BuildContext context, int goalId) async {
    final controller = TextEditingController();
    final state = context.read<CoinVController>();
    await showDialog<void>(
      context: context,
      builder: (dialogContext) => AlertDialog(
        title: const Text('Add goal task'),
        content: TextField(
          controller: controller,
          autofocus: true,
          decoration: const InputDecoration(labelText: 'Next action'),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(dialogContext),
            child: const Text('Cancel'),
          ),
          FilledButton(
            onPressed: () async {
              await state.addGoalTask(goalId, controller.text);
              if (dialogContext.mounted) Navigator.pop(dialogContext);
            },
            child: const Text('Add'),
          ),
        ],
      ),
    );
    controller.dispose();
  }
}

class _DecisionCard extends StatelessWidget {
  const _DecisionCard({required this.decision});
  final DecisionItem decision;

  @override
  Widget build(BuildContext context) {
    final state = context.read<CoinVController>();
    Widget list(String title, List<String> values) => Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        const SizedBox(height: 10),
        Text(
          title.toUpperCase(),
          style: const TextStyle(
            color: CoinColors.blue,
            fontFamily: 'monospace',
            fontSize: 10,
          ),
        ),
        ...values.map((value) => Text('• $value')),
      ],
    );
    return CoinCard(
      child: ExpansionTile(
        tilePadding: EdgeInsets.zero,
        childrenPadding: EdgeInsets.zero,
        title: Text(decision.question),
        subtitle: Text(
          '${(decision.confidence * 100).round()}% confidence · '
          '${decision.status.replaceAll('_', ' ')}',
        ),
        children: [
          Align(
            alignment: Alignment.centerLeft,
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                list('Pros', decision.pros),
                list('Cons', decision.cons),
                list('Risks', decision.risks),
                list('Opportunities', decision.opportunities),
                list('Missing information', decision.missingInformation),
                const SizedBox(height: 10),
                Text(
                  'RECOMMENDATION',
                  style: const TextStyle(
                    color: CoinColors.blue,
                    fontFamily: 'monospace',
                    fontSize: 10,
                  ),
                ),
                Text(decision.recommendation),
                if ((state.similarDecisionPatterns[decision.id] ?? [])
                    .isNotEmpty) ...[
                  const SizedBox(height: 12),
                  const Text(
                    'SIMILAR PAST DECISIONS',
                    style: TextStyle(
                      color: CoinColors.blue,
                      fontFamily: 'monospace',
                      fontSize: 10,
                    ),
                  ),
                  ...state.similarDecisionPatterns[decision.id]!.map(
                    (past) => Text(
                      '• ${past.question} — ${past.status.replaceAll('_', ' ')}',
                    ),
                  ),
                ],
                if (decision.status == 'pending_outcome')
                  Wrap(
                    children: [
                      TextButton(
                        onPressed: () => state.recordDecisionOutcome(
                          decision.id,
                          'resolved_good',
                          'Good',
                        ),
                        child: const Text('Good outcome'),
                      ),
                      TextButton(
                        onPressed: () => state.recordDecisionOutcome(
                          decision.id,
                          'resolved_mixed',
                          'Mixed',
                        ),
                        child: const Text('Mixed'),
                      ),
                      TextButton(
                        onPressed: () => state.recordDecisionOutcome(
                          decision.id,
                          'resolved_bad',
                          'Bad',
                        ),
                        child: const Text('Bad outcome'),
                      ),
                      TextButton(
                        onPressed: () => state.recordDecisionOutcome(
                          decision.id,
                          'abandoned',
                          'Dropped',
                        ),
                        child: const Text('Drop'),
                      ),
                    ],
                  ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}
