import 'dart:async';

import 'package:audio_session/audio_session.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter_tts/flutter_tts.dart';
import 'package:permission_handler/permission_handler.dart';
import 'package:share_plus/share_plus.dart';
import 'package:speech_to_text/speech_recognition_result.dart';
import 'package:speech_to_text/speech_to_text.dart';

import '../data/database.dart';
import '../data/models.dart';
import '../data/settings_store.dart';
import '../services/ai_service.dart';
import '../services/platform_services.dart';

class CoinVController extends ChangeNotifier {
  CoinVController({
    required this.audioHandler,
    CoinVDatabase? database,
    SettingsStore? settingsStore,
    AiService? ai,
  }) : _db = database ?? CoinVDatabase.instance,
       _settingsStore = settingsStore ?? SettingsStore(),
       _ai = ai ?? AiService();

  final CoinVDatabase _db;
  final SettingsStore _settingsStore;
  final AiService _ai;
  final CoinVAudioHandler audioHandler;
  final SpeechToText _speech = SpeechToText();
  final FlutterTts _tts = FlutterTts();

  AppSettings settings = const AppSettings();
  ModeState mode = const ModeState();
  List<ChatMessage> messages = [];
  List<MemoryItem> memories = [];
  List<SemanticMemory> valueMemories = [];
  List<DecisionItem> decisions = [];
  List<GoalItem> goals = [];
  List<TimelineItem> timeline = [];
  List<ContextItem> recentContext = [];
  List<BehaviorStat> behaviorStats = [];
  Map<String, Object?> userProfile = const {
    'name': 'Shyam',
    'cognitive_state': 'Ready',
    'active_coach': 'Founder Coach',
  };
  String liveTranscript = '';
  String? visibleError;
  bool initialized = false;
  bool analyzingDecision = false;
  DateTime? _listenStartedAt;
  StreamSubscription<HeadsetGesture>? _headsetSubscription;
  Timer? _monitorRestart;

  bool get isDark => settings.theme == 'dark';
  bool get hasMicPermission => _micPermission;
  bool _micPermission = false;

  Future<void> initialize() async {
    if (initialized) return;
    settings = await _settingsStore.load();
    userProfile = await _db.profile();
    messages = await _db.messages();
    _micPermission = await Permission.microphone.isGranted;
    await _configureAudio();
    await _speech.initialize(
      onError: (error) {
        if (mode.mode == AppMode.monitoring &&
            error.errorMsg != 'error_permission') {
          _scheduleMonitoringRestart();
        } else {
          visibleError = _speechErrorMessage(error.errorMsg);
          mode = mode.copyWith(phase: VoicePhase.error, error: visibleError);
          notifyListeners();
        }
      },
      onStatus: (status) {
        if (status == SpeechToText.notListeningStatus &&
            mode.mode == AppMode.monitoring &&
            mode.phase == VoicePhase.capturing) {
          _scheduleMonitoringRestart();
        }
      },
    );
    _tts.setCompletionHandler(_onSpeechComplete);
    _tts.setErrorHandler((message) {
      visibleError = 'Text-to-speech failed: $message';
      _onSpeechComplete();
    });
    _headsetSubscription = audioHandler.gestures.listen((gesture) {
      if (gesture == HeadsetGesture.singleTap) {
        mode.mode == AppMode.listening
            ? enterIdle('headset_single')
            : enterListening('headset_single');
      } else {
        mode.mode == AppMode.monitoring
            ? enterIdle('headset_double')
            : enterMonitoring('headset_double');
      }
    });
    mode = const ModeState(); // Never silently restore microphone capture.
    await refresh();
    initialized = true;
    notifyListeners();
  }

  Future<void> _configureAudio() async {
    final session = await AudioSession.instance;
    await session.configure(const AudioSessionConfiguration.speech());
  }

  Future<void> refresh() async {
    final values = await Future.wait([
      _db.memories(),
      _db.semanticMemories(layer: 'value'),
      _db.decisions(),
      _db.goals(),
      _db.timeline(),
      _db.recentContext(),
      _db.behaviorStats(),
    ]);
    memories = values[0] as List<MemoryItem>;
    valueMemories = values[1] as List<SemanticMemory>;
    decisions = values[2] as List<DecisionItem>;
    goals = values[3] as List<GoalItem>;
    timeline = values[4] as List<TimelineItem>;
    recentContext = values[5] as List<ContextItem>;
    behaviorStats = values[6] as List<BehaviorStat>;
    notifyListeners();
  }

  Future<bool> requestMicrophone() async {
    final status = await Permission.microphone.request();
    _micPermission = status.isGranted;
    if (!_micPermission) {
      visibleError = status.isPermanentlyDenied
          ? 'Microphone permission denied. Enable it in system settings.'
          : 'Microphone permission is required for voice mode.';
    } else {
      visibleError = null;
    }
    notifyListeners();
    return _micPermission;
  }

  Future<void> toggleOrb() async {
    if (mode.mode == AppMode.idle) {
      await enterListening('orb');
    } else {
      await enterIdle('orb');
    }
  }

  Future<void> enterListening(String source) async {
    if (!_micPermission && !await requestMicrophone()) return;
    await _tts.stop();
    mode = ModeState(
      mode: AppMode.listening,
      phase: VoicePhase.capturing,
      activatedBy: source,
    );
    notifyListeners();
    await MonitoringService.start('listening');
    await _startRecognition();
  }

  Future<void> enterMonitoring(String source) async {
    if (!settings.monitoringEnabled) {
      visibleError = 'Monitoring is disabled in Profile settings.';
      notifyListeners();
      return;
    }
    if (!_micPermission && !await requestMicrophone()) return;
    await _tts.stop();
    mode = ModeState(
      mode: AppMode.monitoring,
      phase: VoicePhase.capturing,
      activatedBy: source,
    );
    notifyListeners();
    await MonitoringService.start('monitoring');
    await _startRecognition();
  }

  Future<void> enterIdle(String source) async {
    _monitorRestart?.cancel();
    await _speech.cancel();
    await _tts.stop();
    await MonitoringService.stop();
    mode = ModeState(activatedBy: source);
    liveTranscript = '';
    notifyListeners();
  }

  Future<void> _startRecognition() async {
    if (_speech.isListening) return;
    _listenStartedAt = DateTime.now();
    liveTranscript = '';
    try {
      await _speech.listen(
        onResult: _onSpeechResult,
        listenOptions: SpeechListenOptions(
          partialResults: true,
          cancelOnError: false,
          listenMode: ListenMode.dictation,
          listenFor: const Duration(minutes: 2),
          pauseFor: const Duration(seconds: 3),
        ),
      );
    } catch (error) {
      visibleError = 'Could not start speech recognition: $error';
      mode = mode.copyWith(phase: VoicePhase.error, error: visibleError);
      notifyListeners();
    }
  }

  void _onSpeechResult(SpeechRecognitionResult result) {
    liveTranscript = result.recognizedWords;
    notifyListeners();
    if (result.finalResult && result.recognizedWords.trim().isNotEmpty) {
      unawaited(_processSpeech(result.recognizedWords.trim()));
    }
  }

  Future<void> _processSpeech(String text) async {
    final speechMode = mode.mode;
    await _db.recordContext('speech_topic', text, 'voice', speechMode);
    await _db.saveVoiceSession(
      text,
      DateTime.now()
          .difference(_listenStartedAt ?? DateTime.now())
          .inMilliseconds,
      speechMode,
    );
    if (await _handleRememberCommand(text)) {
      await _resumeOrIdle(speechMode);
      return;
    }

    final conversational =
        speechMode == AppMode.listening ||
        (speechMode == AppMode.monitoring &&
            settings.wakeWordEnabled &&
            _isExplicitRequest(text));
    final intervention = await _preConversationIntervention(
      text,
      conversational,
    );
    if (intervention != null) {
      await _speak(
        intervention,
        resumeMonitoring: speechMode == AppMode.monitoring,
      );
      return;
    }
    if (!conversational) {
      await refresh();
      _scheduleMonitoringRestart();
      return;
    }

    final user = ChatMessage(role: 'user', text: text);
    messages = [...messages, user];
    await _db.addMessage(user);
    mode = mode.copyWith(phase: VoicePhase.thinking);
    notifyListeners();
    try {
      if (_isDevilsAdvocateCommand(text)) {
        await _runDevilsAdvocate(text, speechMode);
        return;
      }
      final memoryContext = await _assembleContext(text);
      final coach = userProfile['active_coach'] as String? ?? 'Founder Coach';
      final reply = await _ai.chat(
        systemPrompt: _coachPrompt(coach, memoryContext),
        messages: messages.takeLast(20).toList(),
      );
      final assistant = ChatMessage(role: 'assistant', text: reply.trim());
      messages = [...messages, assistant];
      await _db.addMessage(assistant);
      await _db.addTimeline('voice', 'Voice conversation', text);
      await _speak(
        assistant.text,
        resumeMonitoring: speechMode == AppMode.monitoring,
      );
    } catch (error) {
      visibleError = error.toString();
      mode = mode.copyWith(phase: VoicePhase.error, error: visibleError);
      notifyListeners();
    }
  }

  Future<String?> _preConversationIntervention(
    String text,
    bool conversational,
  ) async {
    final isPromise = _containsAny(text, const [
      "i'm going to",
      'i am going to',
      "i'll ",
      'i will ',
      'starting tomorrow',
      'this week i will',
    ]);
    if (isPromise && await _db.shouldFire('promise_tracker')) {
      final count = await _db.recentPromiseCount();
      if (conversational &&
          count >= 4 &&
          await _db.shouldFire('commitment_guard')) {
        final warning =
            "That's the ${count + 1}th thing you've taken on this week — "
            'still want to add it?';
        await _db.logIntervention('commitment_guard', text, warning);
        return warning;
      }
      await _db.capturePromise(text);
    }
    if (!conversational || !_hasDecisionLanguage(text)) return null;
    if (!await _db.shouldFire('bias_spotter')) return null;
    try {
      final result = await _ai.chat(
        systemPrompt: '''
Detect exactly one of: sunk cost fallacy, confirmation bias, recency bias,
or overconfidence from a small sample. If clearly present, name it and explain
in one specific sentence. Otherwise respond exactly NONE.''',
        messages: [ChatMessage(role: 'user', text: text)],
        maxTokens: 120,
      );
      if (result.trim().toUpperCase() == 'NONE') return null;
      await _db.logIntervention('bias_spotter', text, result);
      return result;
    } catch (_) {
      return null;
    }
  }

  Future<void> _runDevilsAdvocate(String text, AppMode speechMode) async {
    if (!await _db.shouldFire('devils_advocate')) {
      await _resumeOrIdle(speechMode);
      return;
    }
    try {
      final context = await _assembleContext(text);
      final reply = await _ai.chat(
        systemPrompt: '''
For this response only, argue the strongest case AGAINST the user's position.
Be substantive, not contrarian for its own sake. Keep it under four sentences.
$context''',
        messages: [ChatMessage(role: 'user', text: text)],
      );
      await _db.logIntervention('devils_advocate', text, reply);
      final assistant = ChatMessage(role: 'assistant', text: reply);
      messages = [...messages, assistant];
      await _db.addMessage(assistant);
      await _speak(reply, resumeMonitoring: speechMode == AppMode.monitoring);
    } catch (error) {
      visibleError = error.toString();
      mode = mode.copyWith(phase: VoicePhase.error, error: visibleError);
      notifyListeners();
    }
  }

  Future<bool> _handleRememberCommand(String text) async {
    final match = RegExp(
      r'^remember that\s+(.+)$',
      caseSensitive: false,
    ).firstMatch(text);
    if (match == null) return false;
    final fact = match.group(1)!.trim();
    final embedding = await _ai.embed(fact);
    await _db.upsertSemantic(
      content: fact,
      layer: 'episodic',
      sourceType: 'user_stated',
      embedding: embedding,
      importance: .9,
    );
    await _db.addMemory(
      title: 'Remembered fact',
      content: fact,
      category: 'fact',
      tags: 'voice, remembered',
    );
    liveTranscript = '';
    await refresh();
    return true;
  }

  Future<String> _assembleContext(String query) async {
    final embedding = await _ai.embed(query);
    final recalled = await _db.recall(embedding);
    final values = await _db.semanticMemories(layer: 'value');
    final all = <SemanticMemory>[
      ...values,
      ...recalled.where((item) => !values.any((value) => value.id == item.id)),
    ];
    if (all.isEmpty) return '';
    return 'Relevant context from what you know about this user:\n'
        '${all.map((item) => '- [${item.layer}] ${item.content}').join('\n')}';
  }

  Future<void> _speak(String text, {required bool resumeMonitoring}) async {
    mode = mode.copyWith(phase: VoicePhase.speaking);
    _resumeMonitoringAfterSpeech = resumeMonitoring;
    notifyListeners();
    await _tts.speak(text);
  }

  bool _resumeMonitoringAfterSpeech = false;
  void _onSpeechComplete() {
    if (_resumeMonitoringAfterSpeech) {
      mode = mode.copyWith(phase: VoicePhase.capturing);
      notifyListeners();
      _scheduleMonitoringRestart();
    } else {
      unawaited(enterIdle('speech_complete'));
    }
  }

  Future<void> _resumeOrIdle(AppMode previous) async {
    if (previous == AppMode.monitoring) {
      mode = mode.copyWith(phase: VoicePhase.capturing);
      _scheduleMonitoringRestart();
      notifyListeners();
    } else {
      await enterIdle('complete');
    }
  }

  void _scheduleMonitoringRestart() {
    _monitorRestart?.cancel();
    _monitorRestart = Timer(const Duration(milliseconds: 500), () {
      if (mode.mode == AppMode.monitoring &&
          mode.phase == VoicePhase.capturing) {
        unawaited(_startRecognition());
      }
    });
  }

  Future<void> sendPrompt(String text) async {
    if (mode.mode == AppMode.idle) {
      mode = const ModeState(mode: AppMode.listening);
    }
    await _processSpeech(text);
  }

  Future<void> addMemory(
    String title,
    String content,
    String category,
    String tags,
  ) async {
    await _db.addMemory(
      title: title.trim().isEmpty ? 'Untitled memory' : title.trim(),
      content: content.trim(),
      category: category.trim().isEmpty ? 'idea' : category.trim(),
      tags: tags.trim(),
    );
    await _db.upsertSemantic(
      content: content.trim(),
      layer: 'episodic',
      sourceType: 'memory',
      embedding: await _ai.embed(content.trim()),
      importance: .7,
    );
    await refresh();
  }

  Future<void> deleteMemory(int id) async {
    await _db.removeMemory(id);
    await refresh();
  }

  Future<void> searchMemories(String query) async {
    memories = await _db.memories(query);
    notifyListeners();
  }

  Future<void> saveAboutMe(int sourceId, String content) async {
    await _db.upsertSemantic(
      content: content,
      layer: 'value',
      sourceType: 'user_stated',
      sourceId: sourceId,
      embedding: await _ai.embed(content),
      importance: 1,
    );
    if (sourceId == -1) {
      await updateName(content.replaceFirst("User's name is ", ''));
    }
    await refresh();
  }

  Future<void> updateName(String name) async {
    await _db.updateProfile({'name': name.trim()});
    userProfile = await _db.profile();
    notifyListeners();
  }

  Future<void> setCoach(String coach) async {
    await _db.updateProfile({'active_coach': coach});
    userProfile = await _db.profile();
    notifyListeners();
  }

  Future<void> updateListeningMode(String listeningMode) async {
    await _db.updateProfile({
      'listening_mode': listeningMode,
      'wake_word_enabled': listeningMode == 'wake_word' ? 1 : 0,
      'continuous_listening': listeningMode == 'always_listening' ? 1 : 0,
    });
    settings = settings.copyWith(
      wakeWordEnabled: listeningMode == 'wake_word',
      continuousListening: listeningMode == 'always_listening',
    );
    await _settingsStore.save(settings);
    userProfile = await _db.profile();
    notifyListeners();
  }

  Future<void> updateSettings(AppSettings value) async {
    settings = value;
    await _settingsStore.save(settings);
    notifyListeners();
  }

  Future<void> createDecision(String question, String context) async {
    analyzingDecision = true;
    visibleError = null;
    notifyListeners();
    try {
      final memoryContext = await _assembleContext(question);
      final analysis = await _ai.analyzeDecision(
        question: question,
        context: context,
        memoryContext: memoryContext,
      );
      final embedding = await _ai.embed(question);
      await _db.addDecision(question, context, analysis, embedding);
      await _db.upsertSemantic(
        content: 'Decision: $question — ${analysis.recommendation}',
        layer: 'episodic',
        sourceType: 'decision',
        embedding: embedding,
        importance: .8,
      );
      await refresh();
    } catch (error) {
      visibleError = 'Analysis could not be completed: $error';
    } finally {
      analyzingDecision = false;
      notifyListeners();
    }
  }

  Future<void> createGoal(String title, String description) async {
    await _db.addGoal(title.trim(), description.trim());
    await refresh();
  }

  Future<void> recordDecisionOutcome(
    int id,
    String status, [
    String? notes,
  ]) async {
    await _db.setDecisionOutcome(id, status, notes);
    await refresh();
  }

  Future<void> clearMemory() async {
    await _db.clearMemory();
    await refresh();
  }

  Future<void> resetApp() async {
    await enterIdle('reset');
    await _db.reset();
    await _settingsStore.clear();
    settings = const AppSettings();
    userProfile = await _db.profile();
    messages = await _db.messages();
    await refresh();
  }

  Future<void> exportData() async {
    final text = StringBuffer('CoinV profile export\n\n')
      ..writeln('Name: ${userProfile['name']}')
      ..writeln('Coach: ${userProfile['active_coach']}')
      ..writeln('\nAbout Me:')
      ..writeln(valueMemories.map((item) => '- ${item.content}').join('\n'))
      ..writeln('\nMemories:')
      ..writeln(
        memories.map((item) => '- ${item.title}: ${item.content}').join('\n'),
      );
    await SharePlus.instance.share(ShareParams(text: text.toString()));
  }

  void clearError() {
    visibleError = null;
    if (mode.phase == VoicePhase.error) {
      mode = ModeState(mode: mode.mode, activatedBy: mode.activatedBy);
    }
    notifyListeners();
  }

  bool _isExplicitRequest(String text) {
    final lower = text.trim().toLowerCase();
    const starts = [
      'what',
      'how',
      'why',
      'when',
      'where',
      'who',
      'can you',
      'could you',
      'help me',
      'tell me',
      'explain',
      'should i',
      'do i',
      'is it',
    ];
    return lower.contains('coinv') ||
        lower.contains('hey coin') ||
        lower.endsWith('?') ||
        starts.any(lower.startsWith);
  }

  bool _isDevilsAdvocateCommand(String text) {
    final lower = text.toLowerCase();
    return lower.contains("devil's advocate") ||
        lower.contains('devils advocate') ||
        lower.contains('argue the other side');
  }

  bool _hasDecisionLanguage(String text) => _containsAny(text, const [
    'already invested',
    'too much to quit',
    'sunk cost',
    'everyone agrees',
    'proof that',
    'always works',
    'decision',
    'should i',
  ]);

  bool _containsAny(String text, List<String> needles) {
    final lower = text.toLowerCase();
    return needles.any(lower.contains);
  }

  String _coachPrompt(String coach, String context) {
    final focus = switch (coach) {
      'Founder Coach' => 'Focus on startups, product-market fit and execution.',
      'Productivity Coach' => 'Focus on attention, time and friction.',
      'Learning Coach' => 'Focus on retention, practice and curiosity.',
      'Career Coach' => 'Focus on growth, networking and strategic moves.',
      'Thinking Coach' => 'Focus on first principles and clarity.',
      'Decision Coach' =>
        'Focus on tradeoffs, reversibility and expected value.',
      _ => 'Be warm, concise and actionable.',
    };
    return 'You are CoinV, a calm voice assistant. Keep replies under three '
        'sentences because they are spoken aloud. $focus\n$context';
  }

  String _speechErrorMessage(String error) => switch (error) {
    'error_permission' => 'Microphone permission required.',
    'error_network' => 'Speech recognition network error.',
    'error_speech_timeout' => 'No speech input detected.',
    'error_no_match' => 'No speech match.',
    'error_busy' => 'Speech recognizer is busy.',
    _ => 'Speech recognition error: $error',
  };

  @override
  void dispose() {
    _headsetSubscription?.cancel();
    _monitorRestart?.cancel();
    _speech.cancel();
    _tts.stop();
    super.dispose();
  }
}

extension _TakeLast<E> on List<E> {
  Iterable<E> takeLast(int count) => skip(length > count ? length - count : 0);
}
