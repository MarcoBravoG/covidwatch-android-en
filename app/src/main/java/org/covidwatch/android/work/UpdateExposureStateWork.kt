package org.covidwatch.android.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import org.covidwatch.android.data.CovidExposureSummary
import org.covidwatch.android.data.diagnosiskeystoken.DiagnosisKeysTokenRepository
import org.covidwatch.android.data.pref.PreferenceStorage
import org.covidwatch.android.exposurenotification.ExposureNotificationManager
import org.covidwatch.android.exposurenotification.Status
import org.covidwatch.android.ui.Notifications
import org.koin.java.KoinJavaComponent.inject

class UpdateExposureStateWork(
    context: Context,
    private val workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    private val exposureNotification by inject(ExposureNotificationManager::class.java)
    private val diagnosisKeysTokenRepository by inject(DiagnosisKeysTokenRepository::class.java)
    private val preferenceStorage by inject(PreferenceStorage::class.java)
    private val notifications by inject(Notifications::class.java)

    override suspend fun doWork(): Result {
        val token =
            workerParams.inputData.getString(PARAM_TOKEN) ?: return failure(Status.FAILED_INTERNAL)

        val exposureSummaryResult = exposureNotification.getExposureSummary(token)
        val exposureSummary =
            exposureSummaryResult.right ?: return failure(exposureSummaryResult.left)

        // TODO: Check if order of updates is preserved relatively to the calls of [ProvideDiagnosisKeysUseCase]
        // If not, older updates could override new exposure summary
        preferenceStorage.exposureSummary = CovidExposureSummary(
            exposureSummary.daysSinceLastExposure,
            exposureSummary.matchedKeyCount,
            exposureSummary.maximumRiskScore,
            exposureSummary.attenuationDurationsInMinutes,
            exposureSummary.summationRiskScore
        )

        if (exposureSummary.matchedKeyCount > 0) {
            diagnosisKeysTokenRepository.setExposed(token)
            notifications.postExposureNotification()
        } else {
            diagnosisKeysTokenRepository.delete(token)
        }
        return Result.success()
    }

    private fun failure(status: Int?) =
        Result.failure(Data.Builder().putInt(FAILURE, status ?: UNKNOWN_FAILURE).build())

    companion object {
        const val FAILURE = "status"
        const val UNKNOWN_FAILURE = -1
        const val PARAM_TOKEN = "token"
    }
}