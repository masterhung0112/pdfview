package com.hungknow.pdfsdk

import android.graphics.RectF
import com.hungknow.pdfsdk.models.PagePart
import com.hungknow.pdfsdk.utils.Constants.Cache.CACHE_SIZE
import com.hungknow.pdfsdk.utils.Constants.Cache.THUMBNAILS_CACHE_SIZE
import com.hungknow.pdfsdk.utils.Constants.Companion.Cache.CACHE_SIZE
import com.hungknow.pdfsdk.utils.Constants.Companion.Cache.THUMBNAILS_CACHE_SIZE
import java.util.PriorityQueue

class CacheManager {
    private val orderComparator = PagePartComparator()

    private var activeCache = PriorityQueue(CACHE_SIZE, orderComparator)
    private var passiveCache = PriorityQueue(CACHE_SIZE, orderComparator)
    var thumbnails = mutableListOf<PagePart>()

    private val passiveActiveLock = Any()

    val pageParts: List<PagePart>
        get() {
            synchronized((passiveActiveLock)) {
                val parts = mutableListOf<PagePart>()
                parts.addAll(passiveCache)
                parts.addAll(activeCache)
                return parts
            }
        }
    fun cachePart(part: PagePart) {
        synchronized(passiveActiveLock) {
            // If cache too big, remove and recycle
            makeAFreeSpace()

            // Then add part
            activeCache.offer(part)
        }
    }

    fun makeANewSet() {
        synchronized (passiveActiveLock) {
            passiveCache.addAll(activeCache)
            activeCache.clear()
        }
    }

    private fun makeAFreeSpace() {
        synchronized (passiveActiveLock) {
            while ((activeCache.size + passiveCache.size) >= CACHE_SIZE && !passiveCache.isEmpty()) {
                passiveCache.poll().renderedBitmap?.recycle()
            }

            while ((activeCache.size + passiveCache.size) >= CACHE_SIZE && !activeCache.isEmpty()) {
                activeCache.poll().renderedBitmap?.recycle()
            }
        }
    }

    fun cacheThumbnail(part: PagePart) {
        synchronized(thumbnails) {
            // If cache too big, remove and recycle
            while (thumbnails.size >= THUMBNAILS_CACHE_SIZE) {
                thumbnails.removeAt(0).renderedBitmap?.recycle()
            }

            // Then add thumbnail
            addWithoutDuplicates(thumbnails, part)
        }
    }

    fun upPartIfContained(page: Int, pageRelativeBounds: RectF, toOrder: Int): Boolean {
        val fakePart = PagePart(page, null, pageRelativeBounds, false, 0)

        var found: PagePart?
        synchronized(passiveActiveLock) {
            found = find(passiveCache, fakePart)
            if (found != null) {
                passiveCache.remove(found)
                found!!.cacheOrder = toOrder
                activeCache.offer(found)
                return true
            }
            return find(activeCache, fakePart) != null
        }
    }

    // Return true if already contains the described PagePart
    fun containsThumbnail(page: Int, pageRelativeBounds: RectF): Boolean {
        val fakePart = PagePart(page, null, pageRelativeBounds, true, 0)
        synchronized(thumbnails) {
            thumbnails.forEach {
                if (it == fakePart) {
                    return true
                }
            }
            return false
        }
    }

    fun recycle() {
        synchronized(passiveActiveLock) {
            passiveCache.forEach {
                it.renderedBitmap?.recycle()
            }
            passiveCache.clear()
            activeCache.forEach {
                it.renderedBitmap?.recycle()
            }
            activeCache.clear()
        }
        synchronized(thumbnails) {
            thumbnails.forEach {
                it.renderedBitmap?.recycle()
            }
            thumbnails.clear()
        }
    }

    // Add part if it doesn't exist, recycle bitmap otherwise
    private fun addWithoutDuplicates(collection: MutableList<PagePart>, newPart: PagePart) {
        collection.forEach {
            if (it.equals(newPart)) {
                newPart.renderedBitmap?.recycle()
                return
            }
        }
        collection.add(newPart)
    }

    internal class PagePartComparator : Comparator<PagePart> {
        override fun compare(part1: PagePart, part2: PagePart): Int {
            if (part1.cacheOrder === part2.cacheOrder) {
                return 0
            }
            return if (part1.cacheOrder > part2.cacheOrder) 1 else -1
        }
    }

    companion object {
        private fun find(vector: PriorityQueue<PagePart>, fakePart: PagePart): PagePart? {
            vector.forEach {
                if (it == fakePart) {
                    return it
                }
            }

            return null
        }
    }
}