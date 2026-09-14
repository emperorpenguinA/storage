package com.mementostorage.app.util

import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

object IdGenerator {
    @OptIn(ExperimentalUuidApi::class)
    fun newId(): String = Uuid.random().toString()
}
