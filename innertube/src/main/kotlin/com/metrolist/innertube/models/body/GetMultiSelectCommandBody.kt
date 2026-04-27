package com.metrolist.innertube.models.body

import com.metrolist.innertube.models.Context
import kotlinx.serialization.Serializable

@Serializable
data class GetMultiSelectCommandBody(
    val context: Context,
    val selectedItems: List<String>,
    val multiSelectParams: String? = null,
)
