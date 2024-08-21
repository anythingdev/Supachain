package dev.supachain.robot.tool.stategies

import dev.supachain.robot.director.*
import dev.supachain.robot.director.directive.Directive
import dev.supachain.robot.messenger.asSystemMessage
import dev.supachain.robot.messenger.messaging.CommonResponse
import dev.supachain.robot.messenger.messaging.Message
import dev.supachain.robot.tool.ToolConfig
import dev.supachain.robot.tool.ToolMap

/**
 * Represents a tool use strategy where the AI interacts with tools iteratively until a final answer is reached.
 *
 * This data object implements the `ToolUseStrategy` interface and defines the behavior for handling function calls
 * in a back-and-forth manner. It processes responses from the AI provider, executes requested function calls,
 * manages call history, and intervenes when potential loops are detected.
 *
 * @since 0.1.0-alpha
 * @author Che Andre
 */
internal data object BackAndForth : ToolUseStrategy {
    /**
     * Invokes the back-and-forth tool use strategy.
     *
     * This function handles the core logic of the `BackAndForth` strategy. It processes the AI provider's response,
     * executes requested function calls, and manages the conversation flow.
     *
     * @param director The `Director` instance responsible for orchestrating the AI interaction.
     * @param response The `CommonResponse` received from the AI provider.
     * @param directive The `Directive` object containing instructions and restrictions for the AI's actions.
     * @param name The name of the current function being executed (for logging).
     * @param args The arguments passed to the function.
     * @param callHistory A mutable map to track the history of function calls and their results.
     */
    suspend operator fun invoke(
        director: Director<*, *, *>,
        response: CommonResponse,
        directive: Directive,
        name: String,
        args: Array<Any?>,
        callHistory: MutableMap<String, String>
    ) = with(director) {
        if (response.requestedFunctions.isNotEmpty()) {
            var result: CallResult = Success
            for (call in response.requestedFunctions) {
                result = call(callHistory)
                when (result) {
                    Success -> continue
                    Recalled -> {
                        interveneWithoutTools(callHistory);
                        break
                    }

                    is Error -> {
                        messenger(result.exception.asSystemMessage())
                        break
                    }
                }
            }

            if (result == Success) messenger(seekCompletionMessage)
            handleProviderMessaging(directive, name, args, callHistory)
        }
    }

    /**
     * Intervenes in the AI's response generation by providing context about previous function calls.
     *
     * This function is triggered when the AI seems to be stuck in a loop during function calls. It takes the following steps:
     *
     * 1. **Fetches Last User Message:** Retrieves the last message sent by the user in the current conversation.
     * 2. **Adds Contextual Information:** Appends a note to the user's message summarizing the results of all previous
     *    function calls made so far. This helps the AI break out of potential loops and reconsider its approach.
     * 3. **Starts New Conversation:** Initiates a new conversation thread within the `Messenger`. This effectively
     *    resets the context and prevents the AI from fixating on the previous loop.
     * 4. **Sends Messages:** Adds the predefined `completionMessageWhenToolFailed` (which likely instructs the AI to
     *    re-evaluate) and the modified user message to the new conversation. This prompts the AI to process the
     *    information again with the added context.
     *
     * @param callMap A map containing the history of function calls and their results.
     *
     * @since 0.1.0-alpha
     * @author Che Andre
     */
    private fun Director<*, *, *>.interveneWithoutTools(callMap: MutableMap<String, String>) {
        val userMessage = messenger.lastUserMessage().copy()
            .apply {
                content += " \nNote the following may contain the answer." +
                        "[${callMap.map { "${it.key} has result ${it.value}." }}]. " +
                        "If you see the answer, say it in the desired format."
            }

        messenger.newConversation()
        messenger(completionMessageWhenToolFailed, userMessage)
    }

    private val seekCompletionMessage
        get() = ("If you know the final answer after reading the result content from a function call, respond " +
                "with ONLY the answer in the require format")
            .asSystemMessage()

    private val completionMessageWhenToolFailed
        get() = "You must find the answer in the user message and format it to the required format.".asSystemMessage()

    override fun message(director: Director<*, *, *>): Message? = null

    override fun getTools(toolMap: ToolMap): List<ToolConfig> = toolMap.values.toList()
}