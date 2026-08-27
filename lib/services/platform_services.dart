import 'dart:async';

import 'package:audio_service/audio_service.dart';
import 'package:flutter/services.dart';
import 'package:flutter_foreground_task/flutter_foreground_task.dart';

@pragma('vm:entry-point')
void foregroundCallback() {
  FlutterForegroundTask.setTaskHandler(CoinVTaskHandler());
}

class CoinVTaskHandler extends TaskHandler {
  @override
  Future<void> onStart(DateTime timestamp, TaskStarter starter) async {}

  @override
  void onRepeatEvent(DateTime timestamp) {
    FlutterForegroundTask.sendDataToMain({
      'event': 'heartbeat',
      'timestamp': timestamp.millisecondsSinceEpoch,
    });
  }

  @override
  Future<void> onDestroy(DateTime timestamp, bool isTimeout) async {}
}

class NativePlatform {
  static const _channel = MethodChannel('com.coinv.app/platform');

  static Future<void> haptic({int duration = 35}) =>
      _channel.invokeMethod<void>('haptic', {'duration': duration});

  static Future<double> animatorScale() async =>
      await _channel.invokeMethod<double>('animatorScale') ?? 1;

  static Future<bool> requestAudioFocus() async =>
      await _channel.invokeMethod<bool>('requestAudioFocus') ?? false;

  static Future<void> abandonAudioFocus() =>
      _channel.invokeMethod<void>('abandonAudioFocus');

  static Future<String?> legacyDatabasePath() =>
      _channel.invokeMethod<String>('legacyDatabasePath');
}

class MonitoringService {
  static void initialize() {
    FlutterForegroundTask.init(
      androidNotificationOptions: AndroidNotificationOptions(
        channelId: 'coinv_voice',
        channelName: 'CoinV Voice',
        channelDescription:
            'Visible while CoinV is listening or collecting context.',
        onlyAlertOnce: true,
      ),
      iosNotificationOptions: const IOSNotificationOptions(
        showNotification: false,
        playSound: false,
      ),
      foregroundTaskOptions: ForegroundTaskOptions(
        eventAction: ForegroundTaskEventAction.repeat(15000),
        autoRunOnBoot: false,
        autoRunOnMyPackageReplaced: false,
        allowWakeLock: true,
        allowWifiLock: true,
      ),
    );
  }

  static Future<void> start(String mode) async {
    if (await FlutterForegroundTask.isRunningService) {
      await FlutterForegroundTask.updateService(
        notificationTitle: 'CoinV · ${_title(mode)}',
        notificationText: _description(mode),
      );
      return;
    }
    await FlutterForegroundTask.startService(
      serviceId: 1001,
      serviceTypes: const [ForegroundServiceTypes.microphone],
      notificationTitle: 'CoinV · ${_title(mode)}',
      notificationText: _description(mode),
      callback: foregroundCallback,
    );
  }

  static Future<void> stop() async {
    if (await FlutterForegroundTask.isRunningService) {
      await FlutterForegroundTask.stopService();
    }
  }

  static String _title(String mode) =>
      '${mode[0].toUpperCase()}${mode.substring(1)}';
  static String _description(String mode) => mode == 'monitoring'
      ? 'Monitoring — gathering context silently'
      : 'Listening — tap the headset button to stop';
}

enum HeadsetGesture { singleTap, doubleTap }

class CoinVAudioHandler extends BaseAudioHandler {
  CoinVAudioHandler() {
    mediaItem.add(
      const MediaItem(
        id: 'coinv-voice-control',
        title: 'CoinV voice controls',
        artist: 'CoinV',
      ),
    );
    playbackState.add(
      PlaybackState(
        controls: const [MediaControl.play, MediaControl.pause],
        processingState: AudioProcessingState.ready,
        playing: false,
      ),
    );
  }

  final StreamController<HeadsetGesture> _gestures =
      StreamController<HeadsetGesture>.broadcast();
  Timer? _pendingTap;
  Stream<HeadsetGesture> get gestures => _gestures.stream;

  void _tap() {
    unawaited(NativePlatform.haptic());
    if (_pendingTap?.isActive ?? false) {
      _pendingTap?.cancel();
      _pendingTap = null;
      _gestures.add(HeadsetGesture.doubleTap);
      return;
    }
    _pendingTap = Timer(const Duration(milliseconds: 420), () {
      _pendingTap = null;
      _gestures.add(HeadsetGesture.singleTap);
    });
  }

  @override
  Future<void> play() async => _tap();

  @override
  Future<void> pause() async => _tap();

  @override
  Future<void> click([MediaButton button = MediaButton.media]) async => _tap();
}
