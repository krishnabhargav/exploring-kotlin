package infrastructure.salesforce

import com.capsule.atlas.models.Auth
import kotlinx.coroutines.runBlocking
import org.cometd.bayeux.Channel
import org.cometd.bayeux.Message
import org.cometd.bayeux.Promise
import org.cometd.bayeux.client.ClientSession
import org.cometd.bayeux.client.ClientSessionChannel
import org.cometd.client.BayeuxClient
import org.cometd.client.transport.ClientTransport
import org.cometd.client.transport.LongPollingTransport
import org.cometd.common.JSONContext
import org.cometd.common.JacksonJSONContextClient
import org.eclipse.jetty.client.HttpClient
import org.eclipse.jetty.client.api.Request
import org.eclipse.jetty.util.ssl.SslContextFactory

object Subscription {


    fun start(force: ForceSettings) {
        val sslContextFactory = SslContextFactory.Client()
        val httpClient = HttpClient(sslContextFactory)
        httpClient.start()
        val jsonContext: JSONContext.Client = JacksonJSONContextClient()
        val options = HashMap<String, Any>(mapOf(ClientTransport.JSON_CONTEXT_OPTION to jsonContext))
        val httpTransport = object : LongPollingTransport(options, httpClient) {
            override fun customize(request: Request) {
                val bearerToken = runBlocking {
                    val x = force.security.authLookup.invoke("") as Auth.OAuth
                    x.generator.invoke()
                }
                request.header("Authorization", "Bearer $bearerToken")
            }
        }
        val client = BayeuxClient(force.forCometd(), httpTransport)
        val extension = object : ClientSession.Extension {
            //var replayId : Map<String, Any> = mapOf("/data/ChangeEvents" to -2 )
            override fun incoming(session: ClientSession, message: Message.Mutable, promise: Promise<Boolean>?) {
//                if(message.ext!=null && message.ext["replay"] != null)
//                    replayId = message.ext["replay"] as Map<String, Any>
                super.incoming(session, message, promise)
            }
            override fun outgoing(session: ClientSession?, message: Message.Mutable, promise: Promise<Boolean>?) {
                if(message.ext!=null) {
                    message.ext["replay"] = mapOf("/data/ChangeEvents" to -2 )
                }
                super.outgoing(session, message, promise)
            }
        }
        client.addExtension(extension)
        val subscribeListener = ClientSessionChannel.MessageListener { _, message ->
            println(message)
        }
        client.getChannel(Channel.META_SUBSCRIBE).addListener(subscribeListener)
        val handshakeListener = ClientSessionChannel.MessageListener { _, message ->
            println("Handshake Listener => ${message.isSuccessful}")
        }
        client.getChannel(Channel.META_HANDSHAKE).addListener(handshakeListener)
        client.handshake { m ->
            println("Handshake Response => $m")
        }
        val shaken = client.waitFor(15000, BayeuxClient.State.CONNECTED)
        if (shaken) {
            println("Handshake completed : subscribing to change events")
            client.getChannel("/data/ChangeEvents").subscribe { _, m ->
                println("Data ${m.dataAsMap}")
            }
        }
    }
}