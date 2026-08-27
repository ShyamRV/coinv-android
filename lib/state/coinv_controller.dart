import 'dart:async';
import 'dart:convert';

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
import '../services/context_service.dart';
import '../services/intelligence_service.dart';
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
  late final ContextService _context = ContextService(_db);
  late final IntelligenceService _intelligence = IntelligenceService(_db, _ai);
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
  List<InsightItem> insights = [];
  List<DashboardRecommendation> recommendations = [];
  Map<int, List<TaskItem>> goalTasks = {};
  Map<int, List<DecisionItem>> similarDecisionPatterns = {};
  CognitiveMetrics metrics = const CognitiveMetrics(
    focus: 0,
    energy: 0,
    learningVelocity: 0,
    decisionReadiness: 0,
    memoryActivity: 0,
    aiConfidence: 0,
    dailyProgress: 0,
  );
  PersonalizationStatus personalization = const PersonalizationStatus(
    progress: 0,
    confidenceLabel: 'Calibrating',
  );
  String dailySummary = 'Preparing your cognitive summary…';
  Map<String, Object?> userProfile = const {
    'name': 'You',
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
  Timer? _interventionTimeout;
  PendingFollowUp? _pendingFollowUp;
  int? _pendingInterventionId;

  bool get isDark => settings.theme == 'dark';
  bool get hasMicPermission => _micPermission;
  bool _micPermission = false;

  Future<void> initialize() async {
    if (initialized) return;
    settings = await _settingsStore.load();
    userProfile = await _db.profile();
    settings = settings.copyWith(
      wakeWordEnabled: userProfile['wake_word_enabled'] == 1,
      continuousListening: userProfile['continuous_listening'] == 1,
    );
    await _settingsStore.save(settings);
    messages = await _db.messages();
    _micPermission = await Permission.microphone.isGranted;
    await _db.purgeExpiredMemory(settings.memoryRetentionDays);
    await _db.expireStalePromises();
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
    await handleAppResume();
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
    insights = await _db.insights();
    metrics = await _db.computeMetrics();
    personalization = await _intelligence.personalizationStatus();
    dailySummary = await _intelligence.dailySummary(userProfile, metrics);
    recommendations = await _intelligence.recommendations(
      metrics,
      goals,
      decisions,
      memories,
    );
    goalTasks = {
      for (final goal in goals) goal.id: await _db.tasksForGoal(goal.id),
    };
    similarDecisionPatterns = {
      for (final decision in decisions)
        if (decision.embedding.isNotEmpty)
          decision.id: (await _db.similarDecisions(decision.embedding))
              .where((past) => past.id != decision.id)
              .toList(),
    };
    notifyListeners();
  }

  Future<void> handleAppResume() async {
    await _db.dismissUnresolvedInterventions();
    await _db.expireStalePromises();
    final importStatus = await _db.metadata('legacy_import_status');
    if (importStatus?.startsWith('failed:') ?? false) {
      visibleError =
          'Existing CoinV data could not be imported safely. Your original '
          'database was left unchanged.';
    }
    await _normalizeEmbeddings();
    metrics = await _db.computeMetrics();
    try {
      await _intelligence.maybeGenerateDailyInsight(
        allowNetwork: !settings.localOnlyProcessing,
        metrics: metrics,
      );
    } catch (_) {
      // Daily insight is optional and must never block launch.
    }
    if (_pendingFollowUp != null || mode.mode != AppMode.idle) return;
    final due = await _db.pendingFollowUps();
    for (final item in due) {
      final type = item.kind == 'promise'
          ? 'promise_tracker'
          : 'decision_followup';
      if (!await _db.shouldFire(type)) continue;
      _pendingFollowUp = await _db.markFollowUpAsked(item);
      _pendingInterventionId = _pendingFollowUp!.interventionId;
      final prompt = item.kind == 'promise'
          ? "Two weeks ago you mentioned: '${item.text}'. Did that happen?"
          : "Three weeks ago you were deciding: '${item.text}'. "
                'How did that turn out — good, bad, or mixed?';
      final assistant = ChatMessage(role: 'assistant', text: prompt);
      messages = [...messages, assistant];
      await _db.addMessage(assistant);
      _scheduleInterventionTimeout();
      await _speak(prompt, resumeMonitoring: false);
      break;
    }
    await refresh();
  }

  Future<void> _normalizeEmbeddings() async {
    final model = _ai.embeddingModel(
      allowNetwork: !settings.localOnlyProcessing,
    );
    final stale = await _db.incompatibleEmbeddings(
      model,
      _ai.embeddingDimensions,
    );
    for (final memory in stale.take(20)) {
      final embedding = await _ai.embed(
        memory.content,
        allowNetwork: !settings.localOnlyProcessing,
      );
      await _db.updateSemanticEmbedding(memory.id, embedding, model);
    }
  }

  void _scheduleInterventionTimeout() {
    _interventionTimeout?.cancel();
    final interventionId = _pendingInterventionId;
    if (interventionId == null) return;
    _interventionTimeout = Timer(const Duration(seconds: 120), () {
      unawaited(_db.resolveIntervention(interventionId, 'dismissed'));
      _pendingInterventionId = null;
      _pendingFollowUp = null;
    });
  }

  void _trackIntervention(int id) {
    _pendingInterventionId = id;
    _scheduleInterventionTimeout();
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
    if (userProfile['listening_mode'] == 'off') {
      visibleError = 'Voice listening is disabled in Profile settings.';
      notifyListeners();
      return;
    }
    if (!_micPermission && !await requestMicrophone()) return;
    if (!await _ensureNotificationPermission()) return;
    await NativePlatform.requestAudioFocus();
    await _tts.stop();
    mode = ModeState(
      mode: AppMode.listening,
      phase: VoicePhase.capturing,
      activatedBy: source,
    );
    notifyListeners();
    await _db.recordMode(AppMode.listening, source);
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
    if (!await _ensureNotificationPermission()) return;
    await NativePlatform.requestAudioFocus();
    await _tts.stop();
    mode = ModeState(
      mode: AppMode.monitoring,
      phase: VoicePhase.capturing,
      activatedBy: source,
    );
    notifyListeners();
    await _db.recordMode(AppMode.monitoring, source);
    await MonitoringService.start('monitoring');
    await _startRecognition();
  }

  Future<void> enterIdle(String source) async {
    _monitorRestart?.cancel();
    await _speech.cancel();
    await _tts.stop();
    await MonitoringService.stop();
    await NativePlatform.abandonAudioFocus();
    mode = ModeState(activatedBy: source);
    liveTranscript = '';
    await _db.recordMode(AppMode.idle, source);
    notifyListeners();
  }

  Future<bool> _ensureNotificationPermission() async {
    if (!settings.notificationsEnabled) {
      visibleError =
          'Notifications must be enabled while CoinV uses the microphone in '
          'the background.';
      notifyListeners();
      return false;
    }
    final status = await Permission.notification.request();
    if (status.isDenied || status.isPermanentlyDenied) {
      visibleError =
          'Notification permission is required for listening and monitoring.';
      notifyListeners();
      return false;
    }
    return true;
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
    await _context.recordSpeech(text, speechMode);
    await _db.saveVoiceSession(
      text,
      DateTime.now()
          .difference(_listenStartedAt ?? DateTime.now())
          .inMilliseconds,
      speechMode,
    );
    final followUp = _pendingFollowUp;
    if (followUp != null) {
      await _db.resolveFollowUp(followUp, text);
      _pendingFollowUp = null;
      _pendingInterventionId = null;
      _interventionTimeout?.cancel();
      await refresh();
      await _resumeOrIdle(speechMode);
      return;
    }
    final priorIntervention = _pendingInterventionId;
    if (priorIntervention != null) {
      final lower = text.trim().toLowerCase();
      final dismissed =
          lower.contains('never mind') ||
          lower.contains('stop asking me') ||
          lower == 'stop';
      await _db.resolveIntervention(
        priorIntervention,
        dismissed ? 'dismissed' : 'acted_on',
      );
      _pendingInterventionId = null;
      _interventionTimeout?.cancel();
    }
    if (await _handleRememberCommand(text)) {
      await _resumeOrIdle(speechMode);
      return;
    }

    final conversational =
        speechMode == AppMode.listening ||
        (speechMode == AppMode.monitoring &&
            settings.wakeWordEnabled &&
            _context.isExplicitUserRequest(text));
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
      final reply = settings.localOnlyProcessing
          ? _ai.localCoachReply(text, memoryContext)
          : await _ai.chat(
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
        _trackIntervention(
          await _db.logIntervention('commitment_guard', text, warning),
        );
        return warning;
      }
      await _db.capturePromise(text);
    }
    if (!conversational || !_hasDecisionLanguage(text)) return null;
    if (!await _db.shouldFire('bias_spotter')) return null;
    if (settings.localOnlyProcessing) return null;
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
      _trackIntervention(
        await _db.logIntervention('bias_spotter', text, result),
      );
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
      final reply = settings.localOnlyProcessing
          ? 'The strongest case against this is that your current evidence may '
                'be incomplete and the downside could exceed the expected gain.'
          : await _ai.chat(
              systemPrompt: '''
For this response only, argue the strongest case AGAINST the user's position.
Be substantive, not contrarian for its own sake. Keep it under four sentences.
$context''',
              messages: [ChatMessage(role: 'user', text: text)],
            );
      _trackIntervention(
        await _db.logIntervention('devils_advocate', text, reply),
      );
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
    final embedding = await _embed(fact);
    await _db.upsertSemantic(
      content: fact,
      layer: 'episodic',
      sourceType: 'user_stated',
      embedding: embedding,
      embeddingModel: _embeddingModel,
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
    final embedding = await _embed(query);
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
      embedding: await _embed(content.trim()),
      embeddingModel: _embeddingModel,
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

  Future<void> recordNavigation(String route) =>
      _context.recordNavigation(route, mode.mode);

  Future<void> saveAboutMe(int sourceId, String content) async {
    await _db.upsertSemantic(
      content: content,
      layer: 'value',
      sourceType: 'user_stated',
      sourceId: sourceId,
      embedding: await _embed(content),
      embeddingModel: _embeddingModel,
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
    if (listeningMode == 'off') {
      await enterIdle('listening_mode_off');
    } else if (listeningMode == 'always_listening' && initialized) {
      await enterMonitoring('always_listening');
    }
  }

  Future<void> updateSettings(AppSettings value) async {
    final localOnlyChanged =
        settings.localOnlyProcessing != value.localOnlyProcessing;
    settings = value;
    await _settingsStore.save(settings);
    await _db.updateProfile({
      'wake_word_enabled': settings.wakeWordEnabled ? 1 : 0,
      'continuous_listening': settings.continuousListening ? 1 : 0,
    });
    if (!settings.monitoringEnabled && mode.mode == AppMode.monitoring) {
      await enterIdle('monitoring_disabled');
    }
    if (!settings.notificationsEnabled && mode.mode != AppMode.idle) {
      await enterIdle('notifications_disabled');
    }
    await _db.purgeExpiredMemory(settings.memoryRetentionDays);
    if (localOnlyChanged) await _normalizeEmbeddings();
    userProfile = await _db.profile();
    notifyListeners();
  }

  Future<void> createDecision(String question, String context) async {
    analyzingDecision = true;
    visibleError = null;
    notifyListeners();
    try {
      final memoryContext = await _assembleContext(question);
      final analysis = settings.localOnlyProcessing
          ? _ai.analyzeDecisionLocally(question: question, context: context)
          : await _ai.analyzeDecision(
              question: question,
              context: context,
              memoryContext: memoryContext,
            );
      final embedding = await _embed(question);
      final decisionId = await _db.addDecision(
        question,
        context,
        analysis,
        embedding,
        embeddingModel: _embeddingModel,
      );
      similarDecisionPatterns = {
        ...similarDecisionPatterns,
        decisionId: await _db.similarDecisions(embedding),
      };
      await _db.upsertSemantic(
        content: 'Decision: $question — ${analysis.recommendation}',
        layer: 'episodic',
        sourceType: 'decision',
        embedding: embedding,
        embeddingModel: _embeddingModel,
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

  Future<void> addGoalTask(int goalId, String title) async {
    if (title.trim().isEmpty) return;
    await _db.addTask(goalId, title.trim());
    await refresh();
  }

  Future<void> setGoalTaskCompleted(TaskItem task, bool completed) async {
    await _db.setTaskCompleted(task.id, completed);
    await refresh();
  }

  Future<void> recordFeedback({
    required String targetType,
    required String targetId,
    required bool accepted,
    String topic = '',
  }) async {
    await _db.recordFeedback(
      targetType: targetType,
      targetId: targetId,
      accepted: accepted,
      topic: topic,
      responseStyle: 'short',
    );
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
    final payload = {
      'format': 'coinv-export-v1',
      'exported_at': DateTime.now().toUtc().toIso8601String(),
      'settings': {
        'theme': settings.theme,
        'wake_word_enabled': settings.wakeWordEnabled,
        'continuous_listening': settings.continuousListening,
        'privacy_analytics': settings.privacyAnalytics,
        'notifications_enabled': settings.notificationsEnabled,
        'memory_retention_days': settings.memoryRetentionDays,
        'monitoring_enabled': settings.monitoringEnabled,
        'local_only_processing': settings.localOnlyProcessing,
      },
      'data': await _db.exportAll(),
    };
    await SharePlus.instance.share(
      ShareParams(text: const JsonEncoder.withIndent('  ').convert(payload)),
    );
  }

  void clearError() {
    visibleError = null;
    if (mode.phase == VoicePhase.error) {
      mode = ModeState(mode: mode.mode, activatedBy: mode.activatedBy);
    }
    notifyListeners();
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

  String get _embeddingModel =>
      _ai.embeddingModel(allowNetwork: !settings.localOnlyProcessing);

  Future<List<double>> _embed(String text) =>
      _ai.embed(text, allowNetwork: !settings.localOnlyProcessing);

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
    _interventionTimeout?.cancel();
    _speech.cancel();
    _tts.stop();
    super.dispose();
  }
}

extension _TakeLast<E> on List<E> {
  Iterable<E> takeLast(int count) => skip(length > count ? length - count : 0);
}
