package infrastructure

import arrow.core.None
import arrow.core.Some
import com.capsule.atlas.Inputs
import com.capsule.atlas.Outputs
import com.capsule.atlas.models.*
import kotlinx.coroutines.runBlocking
import java.time.Instant
import java.util.*

data class Employee(val id: String, val name: String, val age: Int)

fun main() = runBlocking {
    val url = "eventstore://capsulees/Hello-123?username=admin&password=changeit&isCluster=true&masterOny=false"
    val sd = StreamDefinition.parse(url, hostLookup = { _ -> "localhost:1113" })
    println("Sd => $sd")

    val employee = Employee(UUID.randomUUID().toString(), "Mario", 5000)
    val payload = Json.toJson(employee).toByteArray()
    for (i in 1..5) {
        val de =
                DomainEvent(
                        id = UUID.randomUUID(),
                        type = "Employee",
                        correlationId = null,
                        data = payload,
                        metadata = mapOf(),
                        version = Versioning.Any,
                        timestamp = Instant.now())
        val writeResult = Outputs.writeWithRetries(sd, de)
        println("Wrote Event to EventStore, Result:$writeResult")
    }

    val readResult = Inputs.readWithRetries(sd)
    println("Read back events from stream=Hello-123 and got result=$readResult")
    when (readResult) {
        is Result.Success -> {
            val converted = readResult.value.map { Json.fromJson<Employee>(String(it.data)) }
            val numberOfEvents = converted.size
            println("Converted read results back into internal Types | Total Results=$numberOfEvents")
        }
        is Result.Failure -> print("Failure Occurred: " + readResult.error)
    }

    Inputs.close()
    Outputs.close()
}