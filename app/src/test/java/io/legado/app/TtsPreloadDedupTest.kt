package io.legado.app

import io.legado.app.data.entities.TTSSegment
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger

/**
 * Tests for TTS preload deduplication logic.
 * Reproduces the duplicate request issue found in server logs.
 */
class TtsPreloadDedupTest {

    @Test
    fun duplicateRequestsForSameSegment() {
        val segments = TTSSegment.aggregate(
            listOf("段落一", "段落二", "段落三", "段落四", "段落五"),
            maxLength = 6
        )
        assertTrue("Should have multiple segments", segments.size >= 2)

        val requestCount = AtomicInteger(0)
        val pendingDownloads = ConcurrentHashMap<String, Boolean>()

        fun simulateDownload(segmentText: String) {
            val key = segmentText.trim()
            if (!pendingDownloads.containsKey(key)) {
                pendingDownloads[key] = true
                requestCount.incrementAndGet()
            }
        }

        simulateDownload(segments[0].text)
        simulateDownload(segments[0].text)
        simulateDownload(segments[0].text)

        assertEquals(
            "Same segment should only be requested once with dedup",
            1, requestCount.get()
        )
    }

    @Test
    fun concurrentDuplicateRequests() {
        val segments = TTSSegment.aggregate(
            listOf("aaa", "bbb", "ccc", "ddd", "eee"),
            maxLength = 8
        )
        val requestCount = AtomicInteger(0)
        val pendingDownloads = ConcurrentHashMap<String, Boolean>()
        val latch = CountDownLatch(10)

        repeat(10) { threadIndex ->
            Thread {
                val segIndex = threadIndex % segments.size
                val key = segments[segIndex].text.trim()
                val wasAbsent = pendingDownloads.putIfAbsent(key, true) == null
                if (wasAbsent) {
                    requestCount.incrementAndGet()
                }
                latch.countDown()
            }.start()
        }

        latch.await()

        assertTrue(
            "Concurrent requests should be deduplicated (got ${requestCount.get()})",
            requestCount.get() <= segments.size
        )
    }

    @Test
    fun preloadShouldTriggerOnSegmentBoundary() {
        val paragraphs = listOf("p0", "p1", "p2", "p3", "p4", "p5")
        val segments = TTSSegment.aggregate(paragraphs, maxLength = 6)
        assertTrue("Should have multiple segments", segments.size >= 2)

        var nowSpeak = 0
        var currentSegmentIndex = TTSSegment.findSegment(segments, nowSpeak)
        val preloadTriggered = mutableListOf<Int>()

        fun updateNextPos() {
            if (nowSpeak < paragraphs.lastIndex) {
                nowSpeak++
                val newSegmentIndex = TTSSegment.findSegment(segments, nowSpeak)
                if (newSegmentIndex >= 0 && newSegmentIndex != currentSegmentIndex) {
                    currentSegmentIndex = newSegmentIndex
                    preloadTriggered.add(currentSegmentIndex)
                }
            }
        }

        repeat(paragraphs.size - 1) { updateNextPos() }

        assertTrue(
            "Preload should trigger at segment boundaries",
            preloadTriggered.isNotEmpty()
        )
    }

    @Test
    fun redundantPreloadAfterBulkDownload() {
        val paragraphs = listOf("aaa", "bbb", "ccc")
        val segments = TTSSegment.aggregate(paragraphs, maxLength = 6)
        val startSegmentIndex = 0

        val downloadedSegments = mutableSetOf<Int>()
        for (i in startSegmentIndex until segments.size) {
            downloadedSegments.add(i)
        }

        val preloadTarget = startSegmentIndex + 1
        val redundant = downloadedSegments.contains(preloadTarget)

        assertTrue(
            "preloadNextSegment(${startSegmentIndex}+1=$preloadTarget) is redundant because " +
                    "segment $preloadTarget was already downloaded in bulk loop",
            redundant
        )
    }
}
