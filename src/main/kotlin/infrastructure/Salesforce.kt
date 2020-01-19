package infrastructure

import infrastructure.salesforce.ForceModel
import infrastructure.salesforce.ForceSecurity
import infrastructure.salesforce.ForceSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking
import java.io.BufferedReader
import java.io.InputStreamReader


fun main() = runBlocking {
    val security = ForceSecurity.Jwt(privateKey = loadPrivateKeyFromResource())
    val c = ForceModel.loadContact(ForceSettings(security = security), "0033k00003GJlFJAA1")
    println(c)
}

private fun CoroutineScope.loadPrivateKeyFromResource(): String {
    val resourceAsStream = javaClass.classLoader.getResourceAsStream("key.pem")
    val bf = BufferedReader(InputStreamReader(resourceAsStream!!))
    return bf.readText()
}