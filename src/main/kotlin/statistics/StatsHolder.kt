package statistics

object StatsHolder {
    val globalStats = mutableMapOf<Class<*>, MutableList<StatRecord>>()
    val perFunctionStats = mutableMapOf<String, MutableMap<Class<*>, MutableList<StatRecord>>>()

    inline fun <reified T : StatsData> get(subKey: Any? = null) =
        globalStats.getOrPut(T::class.java) { mutableListOf() }
            .find { it.subKey == subKey }?.data as T?
            ?: error("No global data of type ${T::class.java.simpleName}")

    inline fun <reified T : StatsData> get(functionName: String, subKey: Any? = null) =
        perFunctionStats[functionName]?.getOrPut(T::class.java) { mutableListOf() }
            ?.find { it.subKey == subKey }?.data as T?
            ?: error("No function data of type ${T::class.java.simpleName} for function $functionName")

    fun clear() {
        globalStats.clear()
        perFunctionStats.clear()
    }
}

data class StatRecord(val subKey: Any?, val data: StatsData)

abstract class StatsData(protected val subKey: Any? = null) {
    protected val key get() = this::class.java

    fun record() {
        if (this is PerFunctionStatsData) {
            error("Trying to record ${key.simpleName} as global data, but the data is per-function")
        }

        val list = StatsHolder.globalStats.getOrPut(key) { mutableListOf() }
        list.find { it.subKey == subKey }?.let { oldData ->
            System.err.println("Rewriting ${key.simpleName}, old value: $oldData, new value: $this")
            list.remove(oldData)
        }
        list.add(StatRecord(subKey, this))
    }
}

abstract class PerFunctionStatsData(subKey: Any? = null) : StatsData(subKey) {
    fun record(functionName: String) {
        val perFunction = StatsHolder.perFunctionStats.getOrPut(functionName) { mutableMapOf() }
        val list = perFunction.getOrPut(key) { mutableListOf() }
        list.find { it.subKey == subKey }?.let { oldData ->
            System.err.println("Rewriting ${key.simpleName} for function $functionName, old value: $oldData, new value: $this")
            list.remove(oldData)
        }
        list.add(StatRecord(subKey, this))
    }
}