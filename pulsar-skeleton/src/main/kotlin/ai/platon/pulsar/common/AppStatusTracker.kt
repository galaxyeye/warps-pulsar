package ai.platon.pulsar.common

import ai.platon.pulsar.common.message.MiscMessageWriter
import ai.platon.pulsar.common.metrics.AppMetrics
import ai.platon.pulsar.crawl.fetch.CoreMetrics

class AppStatusTracker(
    val metrics: AppMetrics,
    val coreMetrics: CoreMetrics,
    val messageWriter: MiscMessageWriter
)
