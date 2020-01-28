package infrastructure.salesforce

import com.capsule.atlas.models.Auth
import com.capsule.atlas.utils.Json
import com.squareup.moshi.JsonClass
import kotlinx.coroutines.runBlocking
import org.cometd.bayeux.Channel
import org.cometd.bayeux.Message
import org.cometd.bayeux.Promise
import org.cometd.bayeux.client.ClientSession
import org.cometd.bayeux.client.ClientSessionChannel
import org.cometd.client.BayeuxClient
import org.cometd.client.transport.ClientTransport
import org.cometd.client.transport.LongPollingTransport
import org.cometd.common.JacksonJSONContextClient
import org.eclipse.jetty.client.HttpClient
import org.eclipse.jetty.client.api.Request
import org.eclipse.jetty.util.ssl.SslContextFactory

object ForceChangeDataSubscription {

    @JsonClass(generateAdapter = true)
    data class ChangePayload(
            val data: Data,
            val channel: String
    ) {
        companion object {
            fun loadFromMessage(channel :String, raw : Map<String, Any?>): ChangePayload {
                //the easiest way is to serialize the map into json and then map it back
                val jsonified = Json.toJson(raw)
                println("json => $jsonified")
                val data = Json.fromJson<Data>(jsonified)
                //salesforce puts changed fields as part of payload which also has "ChangeEventHeader"
                //so read those fields and write into our transient changes
                val payload = raw["payload"] as Map<String, Any>
                val changedFields = payload.minus(Payload::ChangeEventHeader.name)
                val payloadWithChangedFields = data.payload.copy(changesMade = changedFields)
                return ChangePayload(channel = channel,
                        data = data.copy(payload = payloadWithChangedFields))
            }
        }
    }

    @JsonClass(generateAdapter = true)
    data class Data(
            val schema: String,
            val payload: Payload,
            val event: Event
    )

    @JsonClass(generateAdapter = true)
    data class Payload(
            val ChangeEventHeader: ChangeEventHeader,
            @Transient
            val changesMade: Map<String, Any> = mapOf()
    )

    @JsonClass(generateAdapter = true)
    data class Event(
            val replayId: String
    )

    @JsonClass(generateAdapter = true)
    data class ChangeEventHeader(
            val entityName: String,
            val recordIds: List<String>,
            val changeType: String,
            val changedFields: List<String>,
            val changeOrigin: String,
            val transactionKey: String,
            val sequenceNumber: String,
            val commitTimestamp: String,
            val commitUser: String,
            val commitNumber: String
    )

    fun start(force: ForceSettings) {
        val httpClient = HttpClient(SslContextFactory.Client())
        httpClient.start()

        val jsonContext = JacksonJSONContextClient()
        val transportOptions = HashMap<String, Any>(mapOf(ClientTransport.JSON_CONTEXT_OPTION to jsonContext))

        val httpTransport = object : LongPollingTransport(transportOptions, httpClient) {
            //this is where we set the bearer token so the calls actually work
            override fun customize(request: Request) {
                val bearerToken = runBlocking {
                    val x = force.security.authLookup.invoke("") as Auth.OAuth
                    x.generator.invoke()
                }
                request.header("Authorization", "Bearer $bearerToken")
            }
        }

        val client = BayeuxClient(force.forCometd(), httpTransport)
        //for task events, receive all that has happened
        //-2 => Subscriber receives all events, including past events that are within the retention window and new events.
        //client.addExtension(ReplayExtension(channel="/data/TaskChangeEvent", replayId=28640))
        client.addExtension(ReplayExtension(channel="/data/ChangeEvents", replayId=28645))
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
                //commit user in payload tells us who changed it.
                println("ALL-CHANGES ->> $m")
            }
//            client.getChannel("/data/ContactChangeEvent").subscribe { _, m ->
//                //commit user in payload tells us who changed it.
//                println("Contact Changed => $m")
//            }
//            client.getChannel("/data/TaskChangeEvent").subscribe { c, m ->
//                //commit user in payload tells us who changed it.
//                val cp = ChangePayload.loadFromMessage(channel = m.channel, raw = m.dataAsMap)
//                println("TaskChangeEvent ->>>> $cp")
//            }
        }
    }
}

class ReplayExtension(private val channel: String, private val replayId : Int) : ClientSession.Extension {
    private var _incomingReplayId: Int = replayId
    override fun incoming(session: ClientSession, message: Message.Mutable, promise: Promise<Boolean>) {
        super.incoming(session, message, promise)
        if(message["channel"] == channel) {
            _incomingReplayId =
                    ((message.data as Map<*, *>)["event"] as Map<*, *>)["replayId"] as Int
            println("$channel => Replay($_incomingReplayId)")
        }
    }

    override fun outgoing(session: ClientSession, message: Message.Mutable, promise: Promise<Boolean>) {
        super.outgoing(session, message, promise)
        if(message["channel"]=="/meta/subscribe" && message["subscription"] == channel) {
            val mp = mapOf(channel to _incomingReplayId)
            if(message["ext"] == null)
                message["ext"] = mutableMapOf<String, Any>()
            val ext = message["ext"] as MutableMap<String,Any>
            ext["replay"] = mp
            println("ReplayEnabled -> Channel=$channel. StartAtExclusive=$_incomingReplayId")
        }
    }
}