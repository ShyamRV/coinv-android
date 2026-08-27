import 'package:shared_preferences/shared_preferences.dart';

class AppSettings {
  const AppSettings({
    this.theme = 'dark',
    this.wakeWordEnabled = false,
    this.continuousListening = false,
    this.privacyAnalytics = true,
    this.notificationsEnabled = true,
    this.memoryRetentionDays = 365,
    this.monitoringEnabled = true,
    this.localOnlyProcessing = true,
  });

  final String theme;
  final bool wakeWordEnabled;
  final bool continuousListening;
  final bool privacyAnalytics;
  final bool notificationsEnabled;
  final int memoryRetentionDays;
  final bool monitoringEnabled;
  final bool localOnlyProcessing;

  AppSettings copyWith({
    String? theme,
    bool? wakeWordEnabled,
    bool? continuousListening,
    bool? privacyAnalytics,
    bool? notificationsEnabled,
    int? memoryRetentionDays,
    bool? monitoringEnabled,
    bool? localOnlyProcessing,
  }) => AppSettings(
    theme: theme ?? this.theme,
    wakeWordEnabled: wakeWordEnabled ?? this.wakeWordEnabled,
    continuousListening: continuousListening ?? this.continuousListening,
    privacyAnalytics: privacyAnalytics ?? this.privacyAnalytics,
    notificationsEnabled: notificationsEnabled ?? this.notificationsEnabled,
    memoryRetentionDays: memoryRetentionDays ?? this.memoryRetentionDays,
    monitoringEnabled: monitoringEnabled ?? this.monitoringEnabled,
    localOnlyProcessing: localOnlyProcessing ?? this.localOnlyProcessing,
  );
}

class SettingsStore {
  static const _theme = 'theme';
  static const _wakeWord = 'wake_word';
  static const _continuous = 'continuous_listening';
  static const _privacy = 'privacy_analytics';
  static const _notifications = 'notifications';
  static const _retention = 'memory_retention_days';
  static const _monitoring = 'monitoring_enabled';
  static const _localOnly = 'local_only_processing';

  Future<AppSettings> load() async {
    final prefs = await SharedPreferences.getInstance();
    return AppSettings(
      theme: prefs.getString(_theme) ?? 'dark',
      wakeWordEnabled: prefs.getBool(_wakeWord) ?? false,
      continuousListening: prefs.getBool(_continuous) ?? false,
      privacyAnalytics: prefs.getBool(_privacy) ?? true,
      notificationsEnabled: prefs.getBool(_notifications) ?? true,
      memoryRetentionDays: prefs.getInt(_retention) ?? 365,
      monitoringEnabled: prefs.getBool(_monitoring) ?? true,
      localOnlyProcessing: prefs.getBool(_localOnly) ?? true,
    );
  }

  Future<void> save(AppSettings settings) async {
    final prefs = await SharedPreferences.getInstance();
    await Future.wait([
      prefs.setString(_theme, settings.theme),
      prefs.setBool(_wakeWord, settings.wakeWordEnabled),
      prefs.setBool(_continuous, settings.continuousListening),
      prefs.setBool(_privacy, settings.privacyAnalytics),
      prefs.setBool(_notifications, settings.notificationsEnabled),
      prefs.setInt(_retention, settings.memoryRetentionDays),
      prefs.setBool(_monitoring, settings.monitoringEnabled),
      prefs.setBool(_localOnly, settings.localOnlyProcessing),
    ]);
  }

  Future<void> clear() async => (await SharedPreferences.getInstance()).clear();
}
