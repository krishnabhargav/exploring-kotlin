import arrow.core.None
import arrow.core.Option
import arrow.core.Some
import kotlinx.coroutines.*
import java.util.concurrent.CancellationException
import java.util.concurrent.Executors
import java.util.concurrent.Future

val executor = Executors.newFixedThreadPool(2)

fun futureExample(futureId: Int, sleepTime: Long): Future<Int> {
    return executor.submit<Int> {
        println("Future waiting. Id=$futureId")
        Thread.sleep(sleepTime)
        println("Future done. Id=$futureId")
        futureId
    }
}

/**
 * This function converts a java.util.concurrent.Future into Deferred so that we can integrate it into coroutines.
 * The implementation is nonblocking as we spin and check if the future was completed or cancelled
 * if the deferred itself is cancelled or the future is cancelled, we will throw cancellation exception
 */
fun <T> Future<T>.asAsync(waitTime: Long = 10): Deferred<T?> {
    val x = this
    return GlobalScope.async {
        while (isActive && !x.isDone && !x.isCancelled)
            delay(waitTime)
        if (isActive && x.isDone) x.get()
        else throw CancellationException()
    }
}

fun main() = runBlocking {
    //    val futures = (0..200).map { futureExample(it, 2000).asAsync(10) }.toList()
//    futures.forEach { it.await() }
//    executor.shutdown()
    val x: Int? = 10
    val y: Int? = 20
    println(compareValues(x, y))

    val o1 = Some(10)
    val o2 = Some(20)

    match(o1, o2)
    val a : Any = 10
    val asInt: Int? = a as? Int
    println(asInt)
}

private fun <T> match(expected: Option<T>, actual: Option<T>): Boolean {
//    return when {
//        expected.isDefined() && actual.isDefined() -> true
//        expected.isEmpty() && actual.isEmpty() -> true
//        expected === None -> false
//        actual === None -> false
//        else -> false
//    }
    return when {
        expected != None && actual != None -> true
        expected == None && actual == None -> true
        else -> false
    }
}