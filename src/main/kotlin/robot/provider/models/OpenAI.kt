@file:Suppress("unused")

package dev.supachain.robot.provider.models

import dev.supachain.ExperimentalAPI
import dev.supachain.Extension
import dev.supachain.Modifiable
import dev.supachain.robot.*
import dev.supachain.robot.messenger.Messenger
import dev.supachain.robot.messenger.Role
import dev.supachain.robot.messenger.messaging.CommonLogProbContainer
import dev.supachain.robot.messenger.messaging.FunctionCall
import dev.supachain.robot.messenger.messaging.ToolCall
import dev.supachain.robot.messenger.messaging.Usage
import dev.supachain.robot.provider.*
import dev.supachain.robot.tool.ToolChoice
import dev.supachain.robot.tool.ToolConfig
import dev.supachain.robot.tool.strategies.BackAndForth
import dev.supachain.robot.tool.strategies.ToolUseStrategy
import dev.supachain.utilities.toJson
import io.ktor.http.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/*
░░░░░░░░░░░░░░░░░░░░░░░░░░       ░░░  ░░░░  ░░        ░░  ░░░░░░░░       ░░░        ░░       ░░░░░░░░░░░░░░░░░░░░░░░░░░░
▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒  ▒▒▒▒  ▒▒  ▒▒▒▒  ▒▒▒▒▒  ▒▒▒▒▒  ▒▒▒▒▒▒▒▒  ▒▒▒▒  ▒▒  ▒▒▒▒▒▒▒▒  ▒▒▒▒  ▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒
▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓       ▓▓▓  ▓▓▓▓  ▓▓▓▓▓  ▓▓▓▓▓  ▓▓▓▓▓▓▓▓  ▓▓▓▓  ▓▓      ▓▓▓▓       ▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓
██████████████████████████  ████  ██  ████  █████  █████  ████████  ████  ██  ████████  ███  ███████████████████████████
██████████████████████████       ████      ███        ██        ██       ███        ██  ████  ██████████████████████████
*/
/**
 * Configuration for interacting with OpenAI's API as an AI Robot.
 *
 * This class provides settings to control how the robot communicates with OpenAI's language models and services.
 * It includes parameters for customizing the model's behavior, network communication, and tool usage.
 *
 * @property chatModel              The chat model to use (e.g., "gpt-4o"). ID of the model to use. See OpenAI the model
 *                                  endpoint compatibility table for details on which models work with the Chat API.
 * @property frequencyPenalty       Number between -2.0 and 2.0. Positive values penalize new tokens based on their
 *                                  existing frequency in the text so far, decreasing the model's likelihood to repeat
 *                                  the same line verbatim.
 * @property logitBias              Modify the likelihood of specified tokens appearing in the completion. Accepts a
 *                                  JSON object that maps tokens (specified by their token ID in the tokenizer) to an
 *                                  associated bias value from -100 to 100. Mathematically, the bias is added to the
 *                                  logits generated by the model prior to sampling. The exact effect will vary per
 *                                  model, but values between -1 and 1 should decrease or increase likelihood of
 *                                  selection; values like -100 or 100 should result in a ban or exclusive selection of
 *                                  the relevant token.
 * @property logProbabilities       Whether to return log probabilities of the output tokens or not. If true, returns
 *                                  the log probabilities of each output token returned in the content of message
 * @property maxRetries             Maximum retry attempts for failed requests.
 * @property maxTokens              The maximum number of tokens that can be generated in the chat completion. The total
 *                                  length of input tokens and generated tokens is limited by the model's context length
 * @property maxToolExecutions      Maximum number of tool calls allowed.
 * @property models                 A reference to the available OpenAI models.
 * @property n                      How many chat completion choices to generate for each input message. Note that you
 *                                  will be charged based on the number of generated tokens across all choices.
 *                                  Keep n as 1 to minimize costs.
 * @property parallelToolCalling    Whether to execute multiple tool calls concurrently.
 * @property presencePenalty        Number between -2.0 and 2.0. Positive values penalize new tokens based on whether
 *                                  they appear in the text so far, increasing the model's likelihood to talk about
 *                                  new topics.
 * @property seed                   This feature is in Beta. If specified, our system will make the best effort to sample
 *                                  deterministically, such that repeated requests with the same seed and parameters
 *                                  should return the same result. Determinism is not guaranteed, and you should refer
 *                                  to the system_fingerprint response parameter to monitor changes in the backend.
 * @property stop                   Up to 4 sequences where the API will stop generating further tokens.
 * @property temperature            What sampling temperature to use, between 0 and 2. Higher values like 0.8 will make
 *                                  the output more random, while lower values like 0.2 will make it more focused and
 *                                  deterministic. We generally recommend altering this or top_p but not both.
 * @property topP                   An alternative to sampling with temperature, called nucleus sampling, where the
 *                                  model considers the results of the tokens with top_p probability mass. So 0.1 means
 *                                  only the tokens comprising the top 10% probability mass are considered. We generally
 *                                  recommend altering this or temperature but not both.
 * @property toolChoice             Controls which (if any) tool is called by the model. none means the model will not
 *                                  call any tool and instead generates a message. auto means the model can pick between
 *                                  generating a message or calling one or more tools. required means the model must
 *                                  call one or more tools
 * @property user                   A unique identifier representing your end-user, which can help OpenAI to monitor and
 *                                  detect abuse
 * @property url                    Base URL for the OpenAI API.
 * @property network                Configuration for network communication settings.
 * @property networkClient          The client settings for making API requests.
 * @property toolsAllowed           Indicates whether the use of tools is permitted.
 *
 * @since 0.1.0
 */
@Suppress("unused", "MemberVisibilityCanBePrivate")
class OpenAI : Provider<OpenAI>(), OpenAIActions, OpenAIModels {
    override val actions: Actions = this
    override var name: String = "Open AI"
    override var url: String = "https://api.openai.com"
    override var maxRetries: Int = 3
    override val networkClient: NetworkClient by lazy { KTORClient(network) }
    val network: NetworkConfig = NetworkConfig()
    override var toolsAllowed: Boolean = true
    override var toolStrategy: ToolUseStrategy = BackAndForth
    override var messenger: Messenger = Messenger(this)

    var apiKey: String = ""
    var chatModel: String = models.chat.gpt4o
    var frequencyPenalty = 0.0
    var logProbabilities: Boolean = false
    var logitBias: Map<String, Int> = emptyMap()
    var maxTokens: Int = 2048
    var maxToolExecutions: Int = 4
    var n: Int = 1
    var parallelToolCalling: Boolean = true
    var presencePenalty = 0.0
    val seed: Int? = null
    var stop: List<String> = emptyList()
    var temperature: Double = 0.5
    var topP: Double = 0.0
    val toolChoice = ToolChoice.AUTO
    val user: String? = null

    internal val headers get() = mutableMapOf(HttpHeaders.Authorization to "Bearer $apiKey")

    companion object : Modifiable<OpenAI>({ OpenAI() })

    override val self = { this }

    override fun onToolResult(result: String) {
        messenger.send(TextMessage(Role.FUNCTION, result))
    }

    override fun onReceiveMessage(message: Message) {
        messenger.send(message)
    }
}

@Suppress("unused")
/*
░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░      ░░░       ░░░        ░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░
▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒  ▒▒▒▒  ▒▒  ▒▒▒▒  ▒▒▒▒▒  ▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒
▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓  ▓▓▓▓  ▓▓       ▓▓▓▓▓▓  ▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓
██████████████████████████████████████████████        ██  ███████████  █████████████████████████████████████████████████
██████████████████████████████████████████████  ████  ██  ████████        ██████████████████████████████████████████████
*/

interface OpenAIAPI {

    @Serializable
    data class OpenAIMessage(
        val role: Role,
        val content: String? = null,
        val refusal: String? = null,
        @SerialName("tool_calls")
        val toolCalls: List<ToolCall>? = null,
        val name: String? = null
    ) {
        constructor(message: Message) : this(message.role(), message.text().value)
    }


    /*
    ░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░      ░░░  ░░░░  ░░░      ░░░        ░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░
    ▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒  ▒▒▒▒  ▒▒  ▒▒▒▒  ▒▒  ▒▒▒▒  ▒▒▒▒▒  ▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒
    ▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓  ▓▓▓▓▓▓▓▓        ▓▓  ▓▓▓▓  ▓▓▓▓▓  ▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓
    ███████████████████████████████████████  ████  ██  ████  ██        █████  ██████████████████████████████████████████
    ████████████████████████████████████████      ███  ████  ██  ████  █████  ██████████████████████████████████████████
    */
    @Serializable
    data class ChatRequest(
        val messages: List<OpenAIMessage>,
        val model: String,
        @SerialName("frequency_penalty") val frequencyPenalty: Double,
        @SerialName("logit_bias") val logitBias: Map<String, Int>?,
        @SerialName("logprobs") val logProbabilities: Boolean,
        @SerialName("max_tokens") val maxTokens: Int?,
        val n: Int,
        @SerialName("parallel_tool_calls") val parallelToolCalls: Boolean,
        @SerialName("presence_penalty") val presencePenalty: Double,
        val seed: Int?,
        val stop: List<String>?,
        val stream: Boolean,
        val temperature: Double,
        @SerialName("top_p") val topP: Double,
        val tools: List<CommonTool.OpenAI>,
        @SerialName("tool_choice") val toolChoice: ToolChoice,
        val user: String?
    ) : CommonChatRequest

    @Serializable
    data class ChatResponse(
        val id: String,
        @SerialName("object") val type: String,
        val created: Int,
        val model: String,
        val choices: List<Choice>,
        val usage: Usage,
        @SerialName("service_tier")
        val serviceTier: String? = null,
        val systemFingerprint: String? = null
    ) : Message {
        @Serializable
        data class Choice(
            val index: Int,
            val message: OpenAIMessage,
            @SerialName("finish_reason")
            private val finishReason: String,
            @SerialName("logprobs")
            val logProbability: CommonLogProbContainer? = null,
        )

        override fun text() = TextContent(choices[0].message.content ?: "")
        override fun role() = Role.ASSISTANT
        override fun functions(): List<FunctionCall> = with(choices[0].message) {
            (toolCalls?.filter { it.type == "function" }?.map { it.function } ?: emptyList())
        }

        override fun contents(): List<Message.Content> = listOf(text())

        override fun toString(): String = this.toJson()
    }

    @Serializable
    @ExperimentalAPI
    data class EmbedRequest(
        val model: String,
        val input: List<String>,
        val dimensions: Int? = null,
        @SerialName("encoding_format") val returnFormat: String? = null,
        val user: String? = null
    ) : CommonEmbedRequest

    @Serializable
    @ExperimentalAPI
    data class ModerationRequest(
        val input: String,
        val model: String
    ) : CommonModRequest

    @Serializable
    @ExperimentalAPI
    data class WriteAudioRequest(
        val model: String = "whisper-1",
        val input: String,
        val file: String,
        val temperature: Double? = null,
        val language: String? = null,
        @SerialName("response_format") val responseFormat: String? = null,
        val prompt: String? = null
    ) : CommonAudioRequest

    @Serializable
    @ExperimentalAPI
    data class WriteImageRequest(
        val prompt: String,
        val n: Int = 1,
        val size: String = "1024x1024",
        @SerialName("response_format") val responseFormat: String? = null,
        val user: String? = null
    ) : CommonImageRequest
}

/*
░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░  ░░░░  ░░░      ░░░       ░░░        ░░  ░░░░░░░░░      ░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░
▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒   ▒▒   ▒▒  ▒▒▒▒  ▒▒  ▒▒▒▒  ▒▒  ▒▒▒▒▒▒▒▒  ▒▒▒▒▒▒▒▒  ▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒
▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓        ▓▓  ▓▓▓▓  ▓▓  ▓▓▓▓  ▓▓      ▓▓▓▓  ▓▓▓▓▓▓▓▓▓      ▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓
███████████████████████████████  █  █  ██  ████  ██  ████  ██  ████████  ██████████████  ███████████████████████████████
███████████████████████████████  ████  ███      ███       ███        ██        ███      ████████████████████████████████
 */

@Suppress("unused")

interface OpenAIModels {

    /**
     * Provides convenient access to OpenAI model names for various tasks.
     *
     * This object serves as a central repository for OpenAI model identifiers,
     * categorized by their primary functions (chat, embedding, moderation).
     *
     * @since 0.1.0
     */
    val models get() = Models


    object Models {
        /** Models suitable for chat completions. */
        val chat = Chat

        /** Models designed for generating text embeddings. */
        val embedding = Embed

        /** Models specializing in content moderation. */
        val moderation = Moderation
    }

    /**
     * Object containing names for OpenAI's chat models.
     */
    @Suppress("unused")
    object Chat {
        val gpt4o get() = "gpt-4o"
        val gpt4Turbo get() = "gpt-4-turbo"
        val gpt4TurboPreview get() = "gpt-4-turbo-preview"
        val gpt4V0125Preview get() = "gpt-4-0125-preview"
        val gpt4V1106Preview get() = "gpt-4-1106-preview"
        val gpt4 get() = "gpt-4"
        val gpt4V0613 get() = "gpt-4-0613"
        val gpt35Turbo get() = "gpt-3.5-turbo"
        val gpt35TurboV0125 get() = "gpt-3.5-turbo-0125"
        val gpt35TurboV1106 get() = "gpt-3.5-turbo-1106"
        val gpt35TurboInstruct get() = "gpt-3.5-turbo-instruct"
    }

    /**
     * Object containing names for OpenAI's text embedding models.
     */
    @Suppress("unused")
    object Embed {
        val textEmbedding3Large get() = "text-embed-3-large"
        val textEmbedding3Small get() = "text-embed-3-small"
        val textEmbeddingAda002 get() = "text-embed-3-ada-002"
    }

    /**
     * Object containing names for OpenAI's moderation models.
     */
    @Suppress("unused")
    object Moderation {
        val textModerationLatest get() = "text-moderation-latest"
        val textModerationStable get() = "text-moderation-stable"
        val textModeration007 get() = "text-moderation-007"
    }
}

fun List<Message>.asOpenAIMessage() = this.map { OpenAIAPI.OpenAIMessage(it) }

@Suppress("unused")
/*
░░░░░░░░░░░░░░░░░░░░░░░░░░░      ░░░░      ░░░        ░░        ░░░      ░░░   ░░░  ░░░      ░░░░░░░░░░░░░░░░░░░░░░░░░░░
▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒  ▒▒▒▒  ▒▒  ▒▒▒▒  ▒▒▒▒▒  ▒▒▒▒▒▒▒▒  ▒▒▒▒▒  ▒▒▒▒  ▒▒    ▒▒  ▒▒  ▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒
▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓  ▓▓▓▓  ▓▓  ▓▓▓▓▓▓▓▓▓▓▓  ▓▓▓▓▓▓▓▓  ▓▓▓▓▓  ▓▓▓▓  ▓▓  ▓  ▓  ▓▓▓      ▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓
██████████████████████████        ██  ████  █████  ████████  █████  ████  ██  ██    ████████  ██████████████████████████
██████████████████████████  ████  ███      ██████  █████        ███      ███  ███   ███      ███████████████████████████
 */
/**
 * Defines actions for interacting with the OpenAI API.
 *
 * This sealed interface specifies the set of operations that a client can perform
 * against the OpenAI API, including chat completion, embedding generation, and moderation.
 *
 * It extends the `NetworkOwner` and `Actions` interfaces, providing access to network capabilities
 * and common action definitions. It also serves as an `Extension` for the `OpenAI` configuration class.
 *
 * Implementations of this interface are expected to provide concrete logic for executing these actions,
 * typically by making HTTP requests to the OpenAI API endpoints.
 *
 * @since 0.1.0
 */
private sealed interface OpenAIActions : NetworkOwner, Extension<OpenAI>, Actions {
    override suspend
    fun chat(tools: List<ToolConfig>): OpenAIAPI.ChatResponse =
        with(self()) {
            return post(
                "$url/v1/chat/completions", OpenAIAPI.ChatRequest(
                    messenger.messages().asOpenAIMessage(),
                    chatModel,
                    frequencyPenalty,
                    logitBias,
                    logProbabilities,
                    maxTokens,
                    n,
                    parallelToolCalling,
                    presencePenalty,
                    seed,
                    stop,
                    network.streamable,
                    temperature,
                    topP,
                    tools.asOpenAITools(),
                    toolChoice,
                    user
                ), headers
            )
        }
}

