package dev.musicx.shared.ui

import com.metrolist.innertubex.InnerTubeHttpException
import dev.musicx.shared.generated.resources.Res
import dev.musicx.shared.generated.resources.error_unknown
import dev.musicx.shared.generated.resources.page_load_failed_with_error
import org.jetbrains.compose.resources.getString

suspend fun Throwable.localizedPageLoadError(): String =
    getString(
        Res.string.page_load_failed_with_error,
        (this as? InnerTubeHttpException)?.status?.toString() ?: this::class.simpleName ?: getString(Res.string.error_unknown),
    )
