package compiler.backend.arm64.registerAllocation

import compiler.ir.IRVar

class GraphColoringBucketQueue(private val bucketCount: Int) {
    private val buckets = arrayOfNulls<MutableSet<IRVar>?>(bucketCount)
    private val nextBucket = IntArray(bucketCount) { -1 }
    private val prevBucket = IntArray(bucketCount) { -1 }
    private val weightMap = hashMapOf<IRVar, Int>()
    private var lastBucket = -1

    private fun getOrCreateBucket(weight: Int): MutableSet<IRVar> {
        if (buckets[weight] == null) {
            buckets[weight] = mutableSetOf()
        }
        return buckets[weight]!!
    }

    /**
     * Add a new element to the queue before next/prev pointers are initialized
     */
    fun preInitAdd(irVar: IRVar, weight: Int) {
        val bucket = getOrCreateBucket(weight)
        bucket.add(irVar)
        weightMap[irVar] = weight
    }

    /**
     * Initialize all internal data structures
     */
    fun init() {
        for (i in 0 until bucketCount) {
            val bucket = getOrCreateBucket(i)
            if (bucket.isEmpty()) continue
            prevBucket[i] = lastBucket
            if (lastBucket >= 0) nextBucket[lastBucket] = i
            lastBucket = i
        }
    }

    /**
     * Returns and removes an element with the highest weight
     */
    fun maxPoll(): IRVar {
        val bucket = getOrCreateBucket(lastBucket)
        val irVar = bucket.first()
        bucket.remove(irVar)
        weightMap.remove(irVar)
        if (bucket.isEmpty()) {
            lastBucket = prevBucket[lastBucket]
            if (lastBucket >= 0) nextBucket[lastBucket] = -1
        }
        return irVar
    }

    /**
     * Decreases the weight of an element by one
     */
    fun decrementWeight(irVar: IRVar) {
        val weight = weightMap[irVar] ?: return
        weightMap[irVar] = weight - 1

        // Remove from `weight` bucket
        val currentBucket = getOrCreateBucket(weight)
        currentBucket.remove(irVar)
        if (currentBucket.isEmpty()) {
            val prev = prevBucket[weight]
            val next = nextBucket[weight]
            if (prev >= 0) nextBucket[prev] = next
            if (next >= 0) prevBucket[next] = prev
            if (lastBucket == weight) lastBucket = prev
        }

        // Add to `weight - 1` bucket
        val minusOneBucket = getOrCreateBucket(weight - 1)
        if (minusOneBucket.isEmpty()) {
            val prev = prevBucket[weight] // Because if it is empty, it has the same prev as weight
            val next = if (currentBucket.isNotEmpty()) weight else nextBucket[weight]
            if (prev >= 0) nextBucket[prev] = weight - 1
            if (next >= 0) prevBucket[next] = weight - 1
            prevBucket[weight - 1] = prev
            nextBucket[weight - 1] = next
            if (lastBucket < weight - 1) lastBucket = weight - 1
        }
        minusOneBucket.add(irVar)
    }

    fun isNotEmpty() = lastBucket >= 0
}