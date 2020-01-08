import arrow.core.Option
import com.capsule.atlas.Outputs
import com.capsule.atlas.WriteResult
import com.capsule.atlas.extensions.get
import com.capsule.atlas.models.*
import com.capsule.atlas.utils.ExperimentalJson
import com.capsule.atlas.utils.Json
import io.javalin.Javalin
import io.javalin.apibuilder.ApiBuilder.*
import io.javalin.http.Context
import io.javalin.plugin.json.FromJsonMapper
import io.javalin.plugin.json.JavalinJson
import io.javalin.plugin.json.ToJsonMapper
import io.javalin.plugin.metrics.MicrometerPlugin
import io.javalin.plugin.openapi.OpenApiOptions
import io.javalin.plugin.openapi.OpenApiPlugin
import io.javalin.plugin.openapi.annotations.OpenApi
import io.javalin.plugin.openapi.annotations.OpenApiContent
import io.javalin.plugin.openapi.annotations.OpenApiResponse
import io.javalin.plugin.openapi.ui.ReDocOptions
import io.micrometer.core.instrument.Metrics
import io.micrometer.prometheus.PrometheusConfig
import io.micrometer.prometheus.PrometheusMeterRegistry
import io.swagger.v3.oas.models.info.Info
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.future.future
import net.minidev.json.JSONArray
import java.lang.UnsupportedOperationException

object ApiServerBuilder {
    //Note: this need to be moved into Atlas
    fun <T> fromJsonWithClass(x: String, classObj: Class<T>): T =
            com.capsule.atlas.utils.Json.fromJsonWithClass(x, classObj)

    private val toJsonMapper = object : ToJsonMapper {
        override fun map(obj: Any): String = com.capsule.atlas.utils.Json.toJson(obj)
    }

    private fun getOpenApiOptions(): OpenApiOptions =
            OpenApiOptions(Info().description("<CHANGE THIS>"))
                    .toJsonMapper(toJsonMapper)
                    .path("/swagger-api")
                    .reDoc(ReDocOptions("/redoc").title("API Documentation for <CHANGE THIS>"))

    private val prometheusRegistry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)

    init {
        JavalinJson.toJsonMapper = toJsonMapper
        JavalinJson.fromJsonMapper = object : FromJsonMapper {
            override fun <T> map(json: String, targetClass: Class<T>): T = fromJsonWithClass(json, targetClass)
        }
        //Todo: The following micrometer configuration must be pushed into infrastructure.
        //eg: Metrics.initializePrometheus() or something like that.
        Metrics.globalRegistry.add(prometheusRegistry)
    }

    fun createServer(): Javalin {
        return Javalin.create { config ->
            config.registerPlugin(OpenApiPlugin(getOpenApiOptions()))
            config.registerPlugin(MicrometerPlugin())
        }.get("/prometheus") {
            it.result(prometheusRegistry.scrape())
        }
    }
}

@ExperimentalJson
fun main() {
    val app: Javalin = ApiServerBuilder.createServer().start(7000);

    app.get("/", EmployeeController::hello)

    //This is how you configure handler for unhandled exceptions
    app.exception(Exception::class.java) { e, _ ->
        e.printStackTrace()
    }

    //an example of using route builders
    app.routes {
        path("/employee") {
            get {
                it.json(GlobalScope.future {
                    return@future EmployeeController.allEmployees(it)
                })
            }
            get("/:id") {
                it.result(GlobalScope.future { EmployeeController.byId(it) })
            }
            put {
                it.json(GlobalScope.future {
                    return@future EmployeeController.addEmployee(it)
                })
            }
        }
    }

//an example of using futures
    app.get("/delayed") {
        it.result(GlobalScope.future {
            delay(100)
            return@future "Hello, World!"
        })
    }
}

/**
 * Demonstrates an example of how to use OpenApi annotations
 */
typealias Res = OpenApiResponse

typealias Cont = OpenApiContent

object EmployeeController {

    sealed class Device {
        data class Laptop(val model: String) : Device()
        data class Phone(val model: String, val carrier: String) : Device()
    }

    class Employee(val id: Int, val name: String, val device: List<Device>)

    /*
    TMPDIR=/private$TMPDIR docker-compose up
    aws --endpoint-url=http://localhost:4578 es create-elasticsearch-domain --domain-name test
    then u use 4571 for hitting the service
     */
    private val hostLookup: (String) -> String = { _ -> "http://localhost:4571" }

    suspend fun addEmployee(ctx: Context): Any {
        val employeeToPost = ctx.bodyAsClass(Employee::class.java) as Employee
        val sd = StreamDefinition.parse(
                "rest://elastic/employees/_doc/${employeeToPost.id}?rest_operation=put",
                hostLookup, { _ -> Auth.None })
        val de = DomainEvent.empty().copy(
                data = Json.toJsonBytes(employeeToPost)
        )
        return Outputs.writeWithRetries(sd, de)
    }

    @ExperimentalJson
    @OpenApi(responses = [Res("200", [Cont(Employee::class, true, "application/json")])])
    suspend fun allEmployees(ctx: Context): Any {
        val sd = StreamDefinition.parse(
                "rest://elastic/employees/_search?q=*&rest_operation=get",
                hostLookup, { _ -> Auth.None })
        val result = Outputs.writeWithRetries(sd, DomainEvent.empty())
        return result.flatMap { c ->
            when (c) {
                is WriteResult.Rest -> {
                    val map = Json.tryGetFromPath(c.body, "\$.hits.hits[*]._source").map {
                        it.map { h ->
                            Json.fromJson<Array<Employee>>((h as JSONArray).toString())
                        }
                    }
                    map
                }
                else -> throw UnsupportedOperationException()
            }
        }.getOrElse(Option.empty()).get()
    }

    @OpenApi(responses = [Res("200", [Cont(String::class)])])
    fun hello(ctx: Context) {
        ctx.result("hello")
    }

    suspend fun byId(ctx: Context): Any {
        val sd = StreamDefinition.parse(
                "rest://elastic/employees/_doc/${ctx.pathParam("id")}?rest_operation=get",
                hostLookup, { _ -> Auth.None })
        return Outputs.writeWithRetries(sd, DomainEvent.empty())
    }

}
