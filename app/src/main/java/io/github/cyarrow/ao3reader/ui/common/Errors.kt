package io.github.cyarrow.ao3reader.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.cyarrow.ao3reader.R
import io.github.cyarrow.ao3reader.ao3.Ao3Exception
import java.io.IOException

@Composable
fun errorMessage(e: Throwable): String = when (e) {
    is Ao3Exception.RateLimited -> stringResource(R.string.error_rate_limited)
    is Ao3Exception.NotFound -> stringResource(R.string.error_not_found)
    is Ao3Exception.NeedsLogin -> stringResource(R.string.error_needs_login)
    is Ao3Exception.Unavailable -> stringResource(R.string.error_unavailable)
    is Ao3Exception.Parse -> stringResource(R.string.error_parse, e.message.orEmpty())
    is IOException -> stringResource(R.string.error_network)
    else -> e.message ?: e.javaClass.simpleName
}
