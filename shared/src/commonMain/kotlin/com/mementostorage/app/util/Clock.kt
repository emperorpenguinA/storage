package com.mementostorage.app.util

import kotlinx.datetime.Clock

fun nowEpochMillis(): Long = Clock.System.now().toEpochMilliseconds()
