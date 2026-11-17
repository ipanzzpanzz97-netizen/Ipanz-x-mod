package com.ipanz.mod.x7pro

import android.app.Application
import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ipanz.mod.x7pro.cleaner.AppCacheCandidate
import com.ipanz.mod.x7pro.cleaner.CleanExecutionSummary
import com.ipanz.mod.x7pro.cleaner.CleanerScanProgress
import com.ipanz.mod.x7pro.cleaner.DeepCleanerRepository
import com.ipanz.mod.x7pro.cleaner.DeepScanResult
import com.ipanz.mod.x7pro.cleaner.RamBoostResult
import com.ipanz.mod.x7pro.game.GameBoostExecutionResult
import com.ipanz.mod.x7pro.game.HyperGameManager
import com.ipanz.mod.x7pro.game.InstalledGameApp
import com.ipanz.mod.x7pro.hardware.HardwareCheckResult
import com.ipanz.mod.x7pro.hardware.HardwareTelemetryRepository
import com.ipanz.mod.x7pro.hardware.IPanzHardwareGatekeeper
import com.ipanz.mod.x7pro.hardware.LiveSystemTelemetry
import com.ipanz.mod.x7pro.permissions.PermissionCenter
import com.ipanz.mod.x7pro.permissions.PermissionSnapshot
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import rikka.shizuku.Shizuku

private val Context.ipanzPrefs by preferencesDataStore(name = "ipanz_mod_prefs")

data class CleanerUiState(
    val isScanning: Boolean = false,
    val scanProgress: CleanerScanProgress = CleanerScanProgress(),
    val scanResult: DeepScanResult? = null,
    val lastCleanSummary: CleanExecutionSummary? = null,
    val isBoostingRam: Boolean = false,
    val lastRamBoost: RamBoostResult? = null,
    val heavyCacheApps: List<AppCacheCandidate> = emptyList(),
    val isLoadingApps: Boolean = false,
    val useShizukuElevation: Boolean = false
)

data class HyperGameUiState(
    val enableAutoDnd: Boolean = true,
    val lockManualBrightness: Boolean = true,
    val isBoosting: Boolean = false,
    val lastBoostResult: GameBoostExecutionResult? = null,
    val installedGames: List<InstalledGameApp> = emptyList(),
    val isLoadingGames: Boolean = false
)

class IPanzViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private val KEY_USE_SHIZUKU = booleanPreferencesKey("use_shizuku")
        private val KEY_AUTO_DND = booleanPreferencesKey("auto_dnd")
        private val KEY_LOCK_BRIGHTNESS = booleanPreferencesKey("lock_brightness")
    }

    private val appContext = application.applicationContext
    private val telemetryRepo = HardwareTelemetryRepository(appContext)
    private val cleanerRepo = DeepCleanerRepository()
    private val ramBooster = ServiceRegistry.getRamBooster(appContext)
    private val gameManager = HyperGameManager(appContext, ramBooster)
    val voiceEngine = ServiceRegistry.getVoiceEngine(appContext)

    private val _hardwareCheck = MutableStateFlow(IPanzHardwareGatekeeper.verifyHardware())
    val hardwareCheck: StateFlow<HardwareCheckResult> = _hardwareCheck.asStateFlow()

    private val _telemetry = MutableStateFlow(telemetryRepo.captureSnapshot())
    val telemetry: StateFlow<LiveSystemTelemetry> = _telemetry.asStateFlow()

    val permissions: StateFlow<PermissionSnapshot> = PermissionCenter.snapshot
    val hudState: StateFlow<HudTelemetryState> = ServiceRegistry.hudState
    val voiceState = voiceEngine.uiState

    private val _cleanerState = MutableStateFlow(CleanerUiState())
    val cleanerState: StateFlow<CleanerUiState> = _cleanerState.asStateFlow()

    private val _gameState = MutableStateFlow(HyperGameUiState())
    val gameState: StateFlow<HyperGameUiState> = _gameState.asStateFlow()

    private var scanJob: Job? = null

    private val shizukuPermissionListener =
        Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
            if (requestCode == PermissionCenter.SHIZUKU_REQUEST_CODE) {
                val granted = grantResult == android.content.pm.PackageManager.PERMISSION_GRANTED
                if (granted) {
                    ramBooster.bindShizukuIfAuthorized()
                } else {
                    viewModelScope.launch {
                        appContext.ipanzPrefs.edit { it[KEY_USE_SHIZUKU] = false }
                    }
                }
                refreshPermissions()
            }
        }

    init {
        try {
            Shizuku.addRequestPermissionResultListener(shizukuPermissionListener)
        } catch (_: Throwable) {
        }
        refreshPermissions()
        observePreferences()
        viewModelScope.launch {
            telemetryRepo.observeTelemetry(1500L).collect { snap ->
                _telemetry.value = snap
            }
        }
        viewModelScope.launch {
            voiceEngine.initializeModel()
        }
        loadGamesAndCacheApps()
    }

    override fun onCleared() {
        try {
            Shizuku.removeRequestPermissionResultListener(shizukuPermissionListener)
        } catch (_: Throwable) {
        }
        super.onCleared()
    }

    private fun observePreferences() {
        viewModelScope.launch {
            appContext.ipanzPrefs.data
                .map { prefs ->
                    Triple(
                        prefs[KEY_USE_SHIZUKU] ?: false,
                        prefs[KEY_AUTO_DND] ?: true,
                        prefs[KEY_LOCK_BRIGHTNESS] ?: true
                    )
                }
                .collect { (shizuku, dnd, bright) ->
                    _cleanerState.update { it.copy(useShizukuElevation = shizuku) }
                    _gameState.update {
                        it.copy(enableAutoDnd = dnd, lockManualBrightness = bright)
                    }
                    if (shizuku) {
                        ramBooster.bindShizukuIfAuthorized()
                    }
                }
        }
    }

    fun refreshPermissions() {
        PermissionCenter.refresh(appContext)
        voiceEngine.checkEarphoneSafety()
    }

    fun setUseShizuku(enabled: Boolean) {
        viewModelScope.launch {
            appContext.ipanzPrefs.edit { it[KEY_USE_SHIZUKU] = enabled }
            if (enabled) {
                if (!PermissionCenter.requestShizukuPermission()) {
                    _cleanerState.update { it.copy(useShizukuElevation = false) }
                } else {
                    ramBooster.bindShizukuIfAuthorized()
                }
            } else {
                ramBooster.unbindShizuku()
            }
            refreshPermissions()
        }
    }

    fun startDeepStorageScan() {
        scanJob?.cancel()
        scanJob = viewModelScope.launch {
            _cleanerState.update {
                it.copy(
                    isScanning = true,
                    lastCleanSummary = null,
                    scanProgress = CleanerScanProgress(0, "", "Memulakan imbasan storan UFS 4.0...")
                )
            }
            val result = cleanerRepo.scanStorage { progress ->
                _cleanerState.update { it.copy(scanProgress = progress) }
            }
            _cleanerState.update {
                it.copy(
                    isScanning = false,
                    scanResult = result
                )
            }
        }
    }

    fun cancelDeepScan() {
        scanJob?.cancel()
        _cleanerState.update { it.copy(isScanning = false) }
    }

    fun toggleJunkSelection(path: String) {
        _cleanerState.update { state ->
            val res = state.scanResult ?: return@update state
            val updated = res.junkFiles.map {
                if (it.path == path) it.copy(selected = !it.selected) else it
            }
            state.copy(scanResult = res.copy(junkFiles = updated))
        }
    }

    fun toggleDuplicateSelection(sha256: String, path: String) {
        _cleanerState.update { state ->
            val res = state.scanResult ?: return@update state
            val updatedGroups = res.duplicateGroups.map { group ->
                if (group.sha256 != sha256) {
                    group
                } else {
                    group.copy(
                        files = group.files.map { entry ->
                            if (entry.path == path) {
                                entry.copy(selectedForDeletion = !entry.selectedForDeletion)
                            } else {
                                entry
                            }
                        }
                    )
                }
            }
            state.copy(scanResult = res.copy(duplicateGroups = updatedGroups))
        }
    }

    fun toggleHeavyFileSelection(path: String) {
        _cleanerState.update { state ->
            val res = state.scanResult ?: return@update state
            val updated = res.heavyFiles.map {
                if (it.path == path) it.copy(selectedForDeletion = !it.selectedForDeletion) else it
            }
            state.copy(scanResult = res.copy(heavyFiles = updated))
        }
    }

    fun cleanSelectedJunk() {
        val current = _cleanerState.value.scanResult ?: return
        val targets = current.junkFiles.filter { it.selected }.map { it.path }
        if (targets.isEmpty()) return
        executePathCleanup(targets)
    }

    fun cleanSelectedDuplicates() {
        val current = _cleanerState.value.scanResult ?: return
        val targets = current.duplicateGroups.flatMap { g ->
            g.files.filter { it.selectedForDeletion }.map { it.path }
        }
        if (targets.isEmpty()) return
        executePathCleanup(targets)
    }

    fun cleanSelectedHeavyFiles() {
        val current = _cleanerState.value.scanResult ?: return
        val targets = current.heavyFiles.filter { it.selectedForDeletion }.map { it.path }
        if (targets.isEmpty()) return
        executePathCleanup(targets)
    }

    private fun executePathCleanup(paths: List<String>) {
        viewModelScope.launch {
            _cleanerState.update { it.copy(isScanning = true) }
            val summary = cleanerRepo.deletePaths(paths)
            val updated = cleanerRepo.scanStorage { progress ->
                _cleanerState.update { it.copy(scanProgress = progress) }
            }
            _cleanerState.update {
                it.copy(
                    isScanning = false,
                    scanResult = updated,
                    lastCleanSummary = summary
                )
            }
            _telemetry.value = telemetryRepo.captureSnapshot()
        }
    }

    fun loadGamesAndCacheApps() {
        viewModelScope.launch {
            _cleanerState.update { it.copy(isLoadingApps = true) }
            _gameState.update { it.copy(isLoadingGames = true) }

            val cacheApps = ramBooster.inspectHeavyCacheApps()
            val games = gameManager.loadInstalledGames()

            _cleanerState.update { it.copy(heavyCacheApps = cacheApps, isLoadingApps = false) }
            _gameState.update { it.copy(installedGames = games, isLoadingGames = false) }
        }
    }

    fun boostRamNow() {
        viewModelScope.launch {
            _cleanerState.update { it.copy(isBoostingRam = true) }
            val useShizuku = _cleanerState.value.useShizukuElevation &&
                PermissionCenter.snapshot.value.shizukuGranted
            val res = ramBooster.boostRamAndTrimCaches(useShizuku = useShizuku)
            _cleanerState.update {
                it.copy(isBoostingRam = false, lastRamBoost = res)
            }
            _telemetry.value = telemetryRepo.captureSnapshot()
        }
    }

    fun openAppStorageSettings(packageName: String) {
        ramBooster.openAppStorageSettings(packageName)
    }

    fun setAutoDnd(enabled: Boolean) {
        viewModelScope.launch {
            appContext.ipanzPrefs.edit { it[KEY_AUTO_DND] = enabled }
        }
    }

    fun setLockBrightness(enabled: Boolean) {
        viewModelScope.launch {
            appContext.ipanzPrefs.edit { it[KEY_LOCK_BRIGHTNESS] = enabled }
        }
    }

    fun activateOneTapGameBoost(targetGamePkg: String? = null) {
        viewModelScope.launch {
            _gameState.update { it.copy(isBoosting = true) }
            val state = _gameState.value
            val useShizuku = _cleanerState.value.useShizukuElevation &&
                PermissionCenter.snapshot.value.shizukuGranted
            val res = gameManager.activatePreGameBoost(
                enableAutoDnd = state.enableAutoDnd,
                lockManualBrightness = state.lockManualBrightness,
                useShizuku = useShizuku,
                targetGamePackage = targetGamePkg
            )
            _gameState.update { it.copy(isBoosting = false, lastBoostResult = res) }
            _telemetry.value = telemetryRepo.captureSnapshot()
            if (targetGamePkg != null) {
                gameManager.launchGamePackage(targetGamePkg)
            }
        }
    }

    fun restorePostGameSettings() {
        gameManager.restoreEnvironmentSettings(
            restoreDnd = _gameState.value.enableAutoDnd,
            restoreAutoBrightness = _gameState.value.lockManualBrightness
        )
    }
}
