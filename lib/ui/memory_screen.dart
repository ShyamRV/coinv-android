import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../core/coinv_theme.dart';
import '../state/coinv_controller.dart';
import 'coin_widgets.dart';

class MemoryScreen extends StatelessWidget {
  const MemoryScreen({super.key});

  @override
  Widget build(BuildContext context) {
    final state = context.watch<CoinVController>();
    return Scaffold(
      body: Column(
        children: [
          const SectionTitle('Memory Vault'),
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 20),
            child: TextField(
              onChanged: state.searchMemories,
              decoration: const InputDecoration(
                hintText: 'Search memories, ideas, tags…',
                prefixIcon: Icon(Icons.search),
              ),
            ),
          ),
          const SizedBox(height: 8),
          Expanded(
            child: state.memories.isEmpty
                ? const Center(
                    child: Text('No memories yet. Capture your first idea.'),
                  )
                : ListView.builder(
                    padding: const EdgeInsets.fromLTRB(20, 8, 20, 80),
                    itemCount: state.memories.length,
                    itemBuilder: (context, index) {
                      final memory = state.memories[index];
                      return Padding(
                        padding: const EdgeInsets.only(bottom: 10),
                        child: CoinCard(
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              Row(
                                children: [
                                  Expanded(
                                    child: Text(
                                      memory.category.toUpperCase(),
                                      style: const TextStyle(
                                        color: CoinColors.blue,
                                        fontFamily: 'monospace',
                                        fontSize: 10,
                                      ),
                                    ),
                                  ),
                                  IconButton(
                                    tooltip: 'Delete memory',
                                    onPressed: () =>
                                        state.deleteMemory(memory.id),
                                    icon: const Icon(
                                      Icons.delete_outline,
                                      size: 20,
                                    ),
                                  ),
                                ],
                              ),
                              Text(
                                memory.title,
                                style: Theme.of(context).textTheme.titleMedium,
                              ),
                              const SizedBox(height: 5),
                              Text(memory.content),
                              if (memory.tags.isNotEmpty) ...[
                                const SizedBox(height: 6),
                                Text(
                                  memory.tags,
                                  style: TextStyle(
                                    color: Theme.of(context)
                                        .colorScheme
                                        .onSurfaceVariant,
                                    fontFamily: 'monospace',
                                    fontSize: 10,
                                  ),
                                ),
                              ],
                            ],
                          ),
                        ),
                      );
                    },
                  ),
          ),
        ],
      ),
      floatingActionButton: FloatingActionButton(
        onPressed: () => _capture(context),
        child: const Icon(Icons.add),
      ),
    );
  }

  Future<void> _capture(BuildContext context) async {
    final title = TextEditingController();
    final content = TextEditingController();
    final category = TextEditingController(text: 'idea');
    final tags = TextEditingController();
    final state = context.read<CoinVController>();
    await showDialog<void>(
      context: context,
      builder: (dialogContext) => AlertDialog(
        title: const Text('Capture Memory'),
        content: SingleChildScrollView(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              TextField(
                controller: title,
                decoration: const InputDecoration(labelText: 'Title'),
              ),
              const SizedBox(height: 10),
              TextField(
                controller: content,
                minLines: 3,
                maxLines: 6,
                decoration: const InputDecoration(labelText: 'Content'),
              ),
              const SizedBox(height: 10),
              TextField(
                controller: category,
                decoration: const InputDecoration(labelText: 'Category'),
              ),
              const SizedBox(height: 10),
              TextField(
                controller: tags,
                decoration: const InputDecoration(labelText: 'Tags'),
              ),
            ],
          ),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(dialogContext),
            child: const Text('Cancel'),
          ),
          FilledButton(
            onPressed: () async {
              if (content.text.trim().isEmpty) return;
              await state.addMemory(
                title.text,
                content.text,
                category.text,
                tags.text,
              );
              if (dialogContext.mounted) Navigator.pop(dialogContext);
            },
            child: const Text('Save'),
          ),
        ],
      ),
    );
    title.dispose();
    content.dispose();
    category.dispose();
    tags.dispose();
  }
}
