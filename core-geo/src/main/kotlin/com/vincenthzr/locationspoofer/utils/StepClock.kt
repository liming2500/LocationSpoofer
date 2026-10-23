package com.vincenthzr.locationspoofer.utils

/**
 * 计步时钟：把"会话起点 + 相对时间"换算成一路单调递增的累计步数。
 *
 * 配置里的 `start_timestamp` 会被反复重写（就位等待、摇杆、路线暂停/继续各写各的），
 * 不同监听器读到的配置也不一定是同一份。若每次都直接用配置里的时间戳计算"从开始到现在的步数"，
 * 一旦读到较早的时间戳，就会把已经走过的那段时间重新累计一遍——累计值往前跳一大截，
 * App 用步数增量算出的步频随之接近翻倍（3 m/s 显示 300+）。
 *
 * 这里的规则是：
 * 1. 只有时间戳**严格晚于**当前锚点时才换锚，并把已走的步数接续为新的基准（相位连续）；
 * 2. 往回读到的更早时间戳不参与计时，始终按当前锚点算；
 * 3. 结果单调不减，任何调用顺序都不会让累计步数倒退或跳变。
 *
 * 因为步数只由 (锚点, 当前时间, 步频, 会话) 决定，同一进程里多次调用、多个监听器之间
 * 得到的结果完全一致，不会重复计数。
 */
class StepClock(initialSteps: Double = 0.0) {

    /** 当前锚点对应的累计步数（换锚时作为新起点） */
    var base: Double = initialSteps
        private set

    /** 最近一次算出的累计步数（连续值：整数部分是步数，小数部分是当前这一步的相位） */
    var steps: Double = initialSteps
        private set

    /** 最近一次采用的会话起点（毫秒时间戳），0 表示还没有锚点 */
    var anchorTs: Long = 0L
        private set

    /** 热重载后恢复状态 */
    @Synchronized
    fun restore(base: Double, steps: Double, anchorTs: Long) {
        this.base = base
        this.steps = steps
        this.anchorTs = anchorTs
    }

    /**
     * 静止（速度为 0）时的时间推进：不产生步数，把锚点挪到 [now]。
     *
     * 就位等待可能持续几分钟，若锚点停在开始等待的时刻，之后一恢复运动就会把整段等待时间
     * 一次性折算成几百步补进去，App 看到的步数瞬间暴涨，算出的步频又会被拉高。
     */
    @Synchronized
    fun pause(now: Long) {
        if (anchorTs == 0L) anchorTs = now
        if (now > anchorTs) {
            anchorTs = now
            base = steps
        }
    }

    /**
     * 推进到 [now] 时刻的累计步数。
     *
     * @param startTimestamp 配置里的会话起点；只在比当前锚点更晚时才生效
     * @param sessionFor 按锚点取真实度会话的工厂——噪声种子必须和锚点一致，
     *   否则同一时刻在不同锚点下算出的起伏不同，步数会出现回跳
     */
    @Synchronized
    fun advance(
        startTimestamp: Long,
        now: Long,
        cadenceSpm: Double,
        sessionFor: (Long) -> MotionRealism.Session
    ): Double {
        if (anchorTs == 0L) {
            anchorTs = startTimestamp
        } else if (startTimestamp > anchorTs) {
            // 会话确实往后推进了：先把旧锚点到新起点之间的步数结算掉，
            // 再把它接续成新锚点的基准。直接换基准会把这段间隔整段丢掉（每秒写一次时间戳时步数只剩一半）。
            val settleSec = ((startTimestamp.coerceAtMost(now) - anchorTs).coerceAtLeast(0L)) / 1000.0
            val settled = base + sessionFor(anchorTs).steps(cadenceSpm, settleSec)
            if (settled > steps) steps = settled
            base = steps
            anchorTs = startTimestamp
        }

        val elapsedSec = ((now - anchorTs).coerceAtLeast(0L)) / 1000.0
        val computed = base + sessionFor(anchorTs).steps(cadenceSpm, elapsedSec)
        if (computed > steps) steps = computed
        return steps
    }
}
