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
import io.javalin.plugin.openapi.ui.SwaggerOptions
import io.micrometer.core.instrument.Metrics
import io.micrometer.prometheus.PrometheusConfig
import io.micrometer.prometheus.PrometheusMeterRegistry
import io.swagger.v3.oas.models.info.Info
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.future.future

object ApiServerBuilder {
    //Note: this need to be moved into Atlas
    fun <T> fromJsonWithClass(x: String, classObj: Class<T>): T =
            com.capsule.atlas.utils.Json.gson.fromJson(x, classObj)

    private val toJsonMapper = object : ToJsonMapper {
        override fun map(obj: Any): String = com.capsule.atlas.utils.Json.toJson(obj)
    }

    private fun getOpenApiOptions(): OpenApiOptions =
            OpenApiOptions(Info().description("Karyon Provider Service"))
                    .toJsonMapper(toJsonMapper)
                    .path("/swagger-api")
                    .swagger(SwaggerOptions("/swagger").title("API Documentation"))

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
            get(EmployeeController::allEmployees)
            path("/:id") {
                get(EmployeeController::byId)
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

    class Employee(val id: Int, val device: List<Device>)

    private val employees = listOf(
            Employee(1, listOf(Device.Laptop("Macbook Pro"), Device.Phone("IPhone", "T-Mobile"))),
            Employee(2, listOf(Device.Laptop("Macbook Pro"), Device.Phone("IPhone", "T-Mobile")))
    )

    @OpenApi(responses = [Res("200", [Cont(Employee::class, true, "application/json")])])
    fun allEmployees(ctx: Context) {
        ctx.json(employees)
    }

    @OpenApi(responses = [Res("200", [Cont(String::class)])])
    fun hello(ctx: Context) {
        ctx.result("hello")
    }

    fun byId(ctx: Context) {
        val result = when (ctx.pathParam("id").toInt()) {
            1 -> employees[0]
            2 -> employees[1]
            else -> throw Exception("Invalid id")
        }
        ctx.json(result)
    }

}
