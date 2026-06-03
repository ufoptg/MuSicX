/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.utils

import android.net.Uri

object SearchRoutes {
    const val ROUTE = "search/{query}"

    private const val QUERY_ROUTE_PREFIX = "__metrolist_search_query__"

    fun resultRoute(query: String): String =
        "search/${Uri.encode(QUERY_ROUTE_PREFIX + query)}"

    fun decodeQuery(rawQuery: String): String =
        rawQuery.removePrefix(QUERY_ROUTE_PREFIX)
}
