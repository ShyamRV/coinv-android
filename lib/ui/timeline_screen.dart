import 'package:flutter/material.dart';
import 'package:intl/intl.dart';
import 'package:provider/provider.dart';

import '../core/coinv_theme.dart';
import '../state/coinv_controller.dart';
import 'coin_widgets.dart';

class TimelineScreen extends StatelessWidget {
  const TimelineScreen({super.key});

  @override
  Widget build(BuildContext context) {
    final events = context.watch<CoinVController>().timeline;
    return Scaffold(
      appBar: AppBar(title: const Text('Cognitive Timeline')),
      body: events.isEmpty
          ? const Center(
              child: Text(
                'Your timeline populates as you use voice, memory, goals, '
                'and decisions.',
                textAlign: TextAlign.center,
              ),
            )
          : ListView.builder(
              padding: const EdgeInsets.all(20),
              itemCount: events.length,
              itemBuilder: (context, index) {
                final item = events[index];
                return Padding(
                  padding: const EdgeInsets.only(bottom: 10),
                  child: CoinCard(
                    child: Row(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        const Padding(
                          padding: EdgeInsets.only(top: 5),
                          child: Icon(
                            Icons.circle,
                            size: 10,
                            color: CoinColors.blue,
                          ),
                        ),
                        const SizedBox(width: 12),
                        Expanded(
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              Text(
                                item.type.toUpperCase(),
                                style: const TextStyle(
                                  color: CoinColors.blue,
                                  fontFamily: 'monospace',
                                  fontSize: 10,
                                ),
                              ),
                              Text(
                                item.title,
                                style: Theme.of(context).textTheme.titleMedium,
                              ),
                              Text(item.description),
                              const SizedBox(height: 4),
                              Text(
                                DateFormat('MMM d · HH:mm').format(
                                  DateTime.fromMillisecondsSinceEpoch(
                                    item.timestamp,
                                  ),
                                ),
                                style: TextStyle(
                                  color: Theme.of(context)
                                      .colorScheme
                                      .onSurfaceVariant,
                                  fontSize: 11,
                                ),
                              ),
                            ],
                          ),
                        ),
                      ],
                    ),
                  ),
                );
              },
            ),
    );
  }
}
