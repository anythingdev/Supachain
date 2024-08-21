/*
░░░░░░░░░░░░░░░░░░░░░       ░░░        ░░       ░░░        ░░░      ░░░        ░░░      ░░░       ░░░░░░░░░░░░░░░░░░░░░░
▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒  ▒▒▒▒  ▒▒▒▒▒  ▒▒▒▒▒  ▒▒▒▒  ▒▒  ▒▒▒▒▒▒▒▒  ▒▒▒▒  ▒▒▒▒▒  ▒▒▒▒▒  ▒▒▒▒  ▒▒  ▒▒▒▒  ▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒
▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓  ▓▓▓▓  ▓▓▓▓▓  ▓▓▓▓▓       ▓▓▓      ▓▓▓▓  ▓▓▓▓▓▓▓▓▓▓▓  ▓▓▓▓▓  ▓▓▓▓  ▓▓       ▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓
█████████████████████  ████  █████  █████  ███  ███  ████████  ████  █████  █████  ████  ██  ███  ██████████████████████
█████████████████████       ███        ██  ████  ██        ███      ██████  ██████      ███  ████  █████████████████████
*/

package dev.supachain.robot.director

import dev.supachain.robot.answer.Answer
import dev.supachain.robot.messenger.Messenger
import dev.supachain.robot.director.directive.Directive
import dev.supachain.robot.provider.Provider
import dev.supachain.robot.tool.ToolMap
import dev.supachain.utilities.*
import kotlinx.coroutines.CompletableJob
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import kotlin.coroutines.CoroutineContext
import kotlin.reflect.KVisibility

internal interface DirectorCore {
    val defaultProvider: Provider<*>
    val directives: MutableMap<String, Directive>
    val toolMap: ToolMap
    val messenger: Messenger
    var defaultSeed: Int?

    val messages get() = messenger.messages()
    val tools get() = defaultProvider.toolStrategy.getTools(this.toolMap)
    val allTools get() = toolMap.values.toList()
}

/**
 * Core class for orchestrating AI interactions and managing tool execution.
 *
 * This class serves as the central hub for communication between the user, the AI provider,
 * and the available tools. It handles the following key responsibilities:
 *
 * - **Directive Management:** Stores a collection of `Directive` objects that define the possible
 *    actions or operations the AI can perform based on user requests.
 * - **Tool Integration:** Maintains a `ToolMap` that associates tool names with their
 *    configurations (`ToolConfig`), allowing for dynamic tool execution.
 * - **Message Handling:** Utilizes a `Messenger` to store and manage the conversation history
 *   between the user and the AI, including system instructions, user prompts, AI responses, and tool outputs.
 * - **Provider Interaction:**  Communicates with the AI provider using the `defaultProvider`,
 *   sending requests and receiving responses.
 * - **Tool Execution:**  Provides a `toolProxy` object to facilitate the execution of tool functions
 *   when requested by the AI.
 *
 * @param P The type of the AI provider used for generating responses and potentially executing tool calls.
 * @param API The interface defining the methods available for interacting with the AI and its tools.
 * @param ToolType The type of the tool class containing the available tool functions.
 *
 * @property defaultProvider The default AI provider for handling requests.
 * @property toolMap A map of tool names to their configurations.
 * @property directives A map of directive names to their corresponding `Directive` objects.
 * @property defaultSeed An optional seed value for controlling the randomness of AI-generated responses.
 * @property messenger An instance of `Messenger` for managing conversation history.
 * @property toolProxy A proxy object for executing tool functions.
 * @property toolProxyObject A lazy-initialized function that creates an instance of the `ToolType` class
 *                           containing the tool functions.
 *
 * @since 0.1.0-alpha
 * @author Che Andre
 */
data class Director<P : Provider<*>, API : Any, ToolType : Any>(
    override var defaultProvider: P,
    override val toolMap: ToolMap = mutableMapOf(),
    override val directives: MutableMap<String, Directive> = mutableMapOf(),
    override var defaultSeed: Int? = null,
    override val job: CompletableJob = SupervisorJob(),
    override val coroutineContext: CoroutineContext = Dispatchers.IO + job,
    override val messenger: Messenger = Messenger(),
) : DirectiveHandling<ToolType>, DirectorCore, RunsInBackground, Extensions {

    override val toolProxy by lazy { toolProxyObject() }
    lateinit var toolProxyObject: () -> ToolType

    @Suppress("unused")
    private val logger: Logger by lazy { LoggerFactory.getLogger(Director::class.java) }

    /**
     * Registers a tool interface by extracting its configuration from each method and corresponding annotations.
     *
     * This function utilizes reflection to identify methods annotated with @[Tool] and @[Parameters],
     * subsequently creating [RobotTool] objects from these methods and adding them to the toolset.
     * The function also supports the inclusion of methods from superinterfaces.
     *
     * @param ToolInterface The type of the tool to be registered. This should be an interface containing
     *                 methods annotated with `@Tool` and `@Parameters`.
     * @return An instance of `ExtendedClient<CLIENT, API>`, allowing for method chaining.
     *
     * @since 0.1.0-alpha
     * @author Che Andre
     */
    internal inline fun <reified ToolInterface : ToolType> setToolset(): Director<P, API, *> = this.also {
        if (defaultProvider.toolsAllowed) {
            toolProxyObject = {
                if (ToolInterface::class.visibility != KVisibility.PUBLIC)
                    throw IllegalArgumentException("Your class must not have private visibility")
                createObjectFromNoArgClass<ToolInterface>()
            }

            val tools = ToolInterface::class.getToolMethods().map { it.toToolConfig() }
            logger.debug(
                Debug("Director"),
                "[Configuration]\nUses Toolset: {},\nTools: {}",
                ToolInterface::class.simpleName,
                tools
            )
            tools.forEach { toolMap[it.function.name] = it }
        }
    }

    /**
     * Creates a proxy instance of the specified API interface.
     *
     * This function maps the methods of the API interface to their corresponding [Directive]
     * objects and creates a proxy that intercepts method calls and handles them using the provided
     * toolset and configurations.
     *
     * @param API The type of the API interface.
     * @return A proxy instance implementing the specified API interface.
     *
     * @since 0.1.0-alpha
     * @author Che Andre
     * */
    internal inline fun <reified API : Any> setUpDirectives(): API {
        val directiveInterface = API::class
        directives.putAll(directiveInterface.getDirectives().associateBy { it.name })
        return directiveInterface by { parent, name, args, returnType ->
            Answer<Any>(returnType, scope.async { handleDirectiveRequest(parent, name, args) })
        }
    }
}