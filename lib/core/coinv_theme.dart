import 'package:flutter/material.dart';

abstract final class CoinColors {
  static const background = Color(0xFF05070B);
  static const surface = Color(0xFF0D1117);
  static const border = Color(0xFF1A2230);
  static const chrome = Color(0xFFF5F7FA);
  static const muted = Color(0xFF8A94A6);
  static const blue = Color(0xFF3DA9FC);
  static const blueDim = Color(0xFF1E5A8C);
  static const success = Color(0xFF00D084);
  static const warning = Color(0xFFFFB547);
  static const error = Color(0xFFFF5D5D);
}

ThemeData coinTheme(Brightness brightness) {
  final dark = brightness == Brightness.dark;
  final background = dark ? CoinColors.background : const Color(0xFFF4F6FA);
  final surface = dark ? CoinColors.surface : Colors.white;
  final foreground = dark ? CoinColors.chrome : const Color(0xFF0A0F18);
  final muted = dark ? CoinColors.muted : const Color(0xFF5A6478);
  final scheme = ColorScheme(
    brightness: brightness,
    primary: CoinColors.blue,
    onPrimary: dark ? CoinColors.background : Colors.white,
    secondary: CoinColors.blueDim,
    onSecondary: Colors.white,
    error: CoinColors.error,
    onError: Colors.white,
    surface: surface,
    onSurface: foreground,
    outline: dark ? CoinColors.border : const Color(0xFFD0D8E4),
  );
  return ThemeData(
    useMaterial3: true,
    brightness: brightness,
    colorScheme: scheme,
    scaffoldBackgroundColor: background,
    canvasColor: background,
    cardColor: surface,
    dividerColor: scheme.outline,
    fontFamily: 'sans-serif',
    textTheme: ThemeData(brightness: brightness).textTheme
        .apply(bodyColor: foreground, displayColor: foreground),
    navigationBarTheme: NavigationBarThemeData(
      backgroundColor: dark ? const Color(0xFF0C1A27) : Colors.white,
      indicatorColor: surface,
      iconTheme: WidgetStateProperty.resolveWith(
        (states) => IconThemeData(
          color: states.contains(WidgetState.selected)
              ? CoinColors.blue
              : muted,
        ),
      ),
      labelTextStyle: WidgetStateProperty.resolveWith(
        (states) => TextStyle(
          color: states.contains(WidgetState.selected)
              ? CoinColors.blue
              : muted,
          fontWeight: FontWeight.w600,
        ),
      ),
    ),
    inputDecorationTheme: InputDecorationTheme(
      filled: true,
      fillColor: surface,
      border: OutlineInputBorder(
        borderRadius: BorderRadius.circular(10),
        borderSide: BorderSide(color: scheme.outline),
      ),
      focusedBorder: OutlineInputBorder(
        borderRadius: BorderRadius.circular(10),
        borderSide: const BorderSide(color: CoinColors.blue),
      ),
    ),
    snackBarTheme: SnackBarThemeData(
      backgroundColor: surface,
      contentTextStyle: TextStyle(color: foreground),
    ),
  );
}
