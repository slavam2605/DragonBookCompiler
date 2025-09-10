package statistics

object StatsHolder {
    val globalStats = mutableMapOf<Class<*>, StatsData>()
    val perFunctionStats = mutableMapOf<String, MutableMap<Class<*>, StatsData>>()

    inline fun <reified T : StatsData> get() =
        globalStats[T::class.java] as T?
            ?: error("No global data of type ${T::class.java.simpleName}")

    inline fun <reified T : StatsData> get(functionName: String) =
        perFunctionStats[functionName]?.get(T::class.java) as T?
            ?: error("No function data of type ${T::class.java.simpleName} for function $functionName")

    fun clear() {
        globalStats.clear()
        perFunctionStats.clear()
    }
}

abstract class StatsData {
    protected val key get() = this::class.java

    fun record() {
        if (this is PerFunctionStatsData) {
            error("Trying to record ${key.simpleName} as global data, but the data is per-function")
        }

        StatsHolder.globalStats.put(key, this)?.let { oldData ->
            System.err.println("Rewriting ${key.simpleName}, old value: $oldData, new value: $this")
        }
    }
}

abstract class PerFunctionStatsData : StatsData() {
    fun record(functionName: String) {
        StatsHolder.perFunctionStats.getOrPut(functionName) { mutableMapOf() }.put(key, this)?.let { oldData ->
            System.err.println("Rewriting ${key.simpleName} for function $functionName, old value: $oldData, new value: $this")
        }
    }
}