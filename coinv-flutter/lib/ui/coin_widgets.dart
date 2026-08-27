import 'dart:math' as math;

import 'package:flutter/material.dart';

import '../core/coinv_theme.dart';
import '../data/models.dart';

class SectionTitle extends StatelessWidget {
  const SectionTitle(this.text, {super.key});
  final String text;

  @override
  Widget build(BuildContext context) => Padding(
    padding: const EdgeInsets.fromLTRB(20, 20, 20, 10),
    child: Text(
      text.toUpperCase(),
      style: TextStyle(
        color: Theme.of(context).colorScheme.onSurfaceVariant,
        fontFamily: 'monospace',
        fontSize: 11,
        letterSpacing: 1.8,
      ),
    ),
  );
}

class CoinCard extends StatelessWidget {
  const CoinCard({required this.child, super.key, this.padding});
  final Widget child;
  final EdgeInsets? padding;

  @override
  Widget build(BuildContext context) => Container(
    width: double.infinity,
    padding: padding ?? const EdgeInsets.all(16),
    decoration: BoxDecoration(
      color: Theme.of(context).colorScheme.surface,
      border: Border.all(color: Theme.of(context).colorScheme.outline),
      borderRadius: BorderRadius.circular(14),
    ),
    child: child,
  );
}

class CoinOrb extends StatefulWidget {
  const CoinOrb({
    required this.mode,
    required this.onTap,
    super.key,
    this.size = 230,
  });
  final ModeState mode;
  final VoidCallback onTap;
  final double size;

  @override
  State<CoinOrb> createState() => _CoinOrbState();
}

class _CoinOrbState extends State<CoinOrb> with SingleTickerProviderStateMixin {
  late final AnimationController _controller = AnimationController(
    vsync: this,
    duration: const Duration(seconds: 2),
  )..repeat(reverse: true);

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final active = widget.mode.mode != AppMode.idle;
    return Semantics(
      button: true,
      label: 'CoinV voice orb. ${widget.mode.label}',
      child: GestureDetector(
        onTap: widget.onTap,
        child: AnimatedBuilder(
          animation: _controller,
          builder: (context, child) => CustomPaint(
            size: Size.square(widget.size),
            painter: _OrbPainter(
              active: active,
              phase: widget.mode.phase,
              pulse: _controller.value,
              dark: Theme.of(context).brightness == Brightness.dark,
            ),
          ),
        ),
      ),
    );
  }
}

class _OrbPainter extends CustomPainter {
  const _OrbPainter({
    required this.active,
    required this.phase,
    required this.pulse,
    required this.dark,
  });
  final bool active;
  final VoicePhase phase;
  final double pulse;
  final bool dark;

  @override
  void paint(Canvas canvas, Size size) {
    final center = Offset(size.width / 2, size.height / 2);
    final radius = size.width / 2;
    final blue = phase == VoicePhase.error ? CoinColors.error : CoinColors.blue;
    canvas.drawCircle(
      center,
      radius - 2,
      Paint()
        ..style = PaintingStyle.stroke
        ..strokeWidth = active ? 2 + pulse * 2 : 1
        ..color = active ? blue.withValues(alpha: .8) : CoinColors.muted,
    );
    if (active) {
      canvas.drawCircle(
        center,
        radius * (.72 + pulse * .05),
        Paint()
          ..shader = RadialGradient(
            colors: [blue.withValues(alpha: .22), blue.withValues(alpha: 0)],
          ).createShader(Rect.fromCircle(center: center, radius: radius)),
      );
    }
    canvas.drawCircle(
      center,
      radius * .55,
      Paint()
        ..shader = RadialGradient(
          center: const Alignment(-.25, -.3),
          colors: [
            dark ? const Color(0xFF27303B) : const Color(0xFFE8EDF4),
            dark ? const Color(0xFF05070B) : const Color(0xFFB9C5D3),
          ],
        ).createShader(Rect.fromCircle(center: center, radius: radius * .55)),
    );
    final path = Path()
      ..moveTo(center.dx - radius * .36, center.dy - radius * .22)
      ..lineTo(center.dx, center.dy + radius * .34)
      ..lineTo(center.dx + radius * .36, center.dy - radius * .22)
      ..lineTo(center.dx, center.dy)
      ..close();
    canvas.drawPath(
      path,
      Paint()
        ..shader = const LinearGradient(
          colors: [Color(0xFFE8EEF6), Color(0xFF3DA9FC)],
          begin: Alignment.topCenter,
          end: Alignment.bottomCenter,
        ).createShader(Rect.fromCircle(center: center, radius: radius * .5)),
    );
    for (var i = 0; i < 3; i++) {
      final angle = pulse * math.pi * 2 + i * math.pi * 2 / 3;
      final point =
          center + Offset(math.cos(angle), math.sin(angle)) * radius * .82;
      canvas.drawCircle(point, active ? 2.5 : 1.5, Paint()..color = blue);
    }
  }

  @override
  bool shouldRepaint(_OrbPainter oldDelegate) =>
      oldDelegate.pulse != pulse ||
      oldDelegate.active != active ||
      oldDelegate.phase != phase ||
      oldDelegate.dark != dark;
}

class ModeCard extends StatelessWidget {
  const ModeCard({
    required this.mode,
    required this.onListening,
    required this.onMonitoring,
    required this.onStop,
    super.key,
  });
  final ModeState mode;
  final VoidCallback onListening;
  final VoidCallback onMonitoring;
  final VoidCallback onStop;

  @override
  Widget build(BuildContext context) => CoinCard(
    child: Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        const Text(
          'Active Mode',
          style: TextStyle(
            color: CoinColors.blue,
            fontFamily: 'monospace',
            fontSize: 11,
          ),
        ),
        const SizedBox(height: 6),
        Text(
          mode.mode.name[0].toUpperCase() + mode.mode.name.substring(1),
          style: Theme.of(context).textTheme.titleLarge,
        ),
        if (mode.mode == AppMode.monitoring)
          const Padding(
            padding: EdgeInsets.only(top: 8),
            child: Text(
              '●  Monitoring active — context collection only',
              style: TextStyle(color: CoinColors.blue, fontSize: 12),
            ),
          ),
        const SizedBox(height: 12),
        Wrap(
          spacing: 8,
          children: [
            OutlinedButton(
              onPressed: onListening,
              child: const Text('Listening'),
            ),
            OutlinedButton(
              onPressed: onMonitoring,
              child: const Text('Monitoring'),
            ),
            if (mode.mode != AppMode.idle)
              TextButton(
                onPressed: onStop,
                child: const Text(
                  'Stop',
                  style: TextStyle(color: CoinColors.warning),
                ),
              ),
          ],
        ),
        Text(
          'Headset: 1× tap = Listening · 2× tap = Monitoring',
          style: TextStyle(
            color: Theme.of(context).colorScheme.onSurfaceVariant,
            fontSize: 11,
          ),
        ),
      ],
    ),
  );
}

class ErrorBanner extends StatelessWidget {
  const ErrorBanner({
    required this.message,
    required this.onDismiss,
    super.key,
  });
  final String message;
  final VoidCallback onDismiss;

  @override
  Widget build(BuildContext context) => MaterialBanner(
    content: Text(message),
    leading: const Icon(Icons.error_outline, color: CoinColors.error),
    actions: [TextButton(onPressed: onDismiss, child: const Text('Dismiss'))],
  );
}
