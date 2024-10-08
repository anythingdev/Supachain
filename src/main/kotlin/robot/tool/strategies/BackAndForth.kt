package dev.supachain.robot.tool.strategies


import dev.supachain.robot.director.*
import dev.supachain.robot.messenger.ToolResultAction
import dev.supachain.robot.messenger.ToolResultMessage
import dev.supachain.robot.provider.Provider
import dev.supachain.robot.provider.models.CommonMessage
import dev.supachain.robot.provider.models.asSystemMessage
import dev.supachain.robot.tool.ToolConfig

/**
 * Represents a tool usage strategy where the Provider uses tools in a back and forth protocol until a final answer
 * is reached.
 *
 * This `ToolUseStrategy` defines a back-and-forth behavior where the Provider interacts with external tools iteratively.
 * It processes responses from an Provider, executes the necessary function calls via the tools, tracks call history,
 * and intervenes when potential looping behavior is detected. The strategy is designed to ensure that the interaction
 * progresses towards a conclusive result, either by retrying, updating, or completing the task based on the tool's result.
 *
 * @since 0.1.0
 */
data object BackAndForth : ToolUseStrategy {

    /**
     * Internal class representing the result of a tool's execution within the back-and-forth strategy.
     *
     * This class stores the result action, the generated messages from the tool execution, and a call history map
     * to track the function calls and their results.
     *
     * @property action The action to be taken based on the tool's result, such as retrying, updating, or completing the task.
     * @property messages A mutable list of messages generated during the tool execution.
     * @property callHistory A mutable map tracking the function calls made and their corresponding results.
     *
     * @since 0.1.0
     */
    internal class Result(
        override var action: ToolResultAction = ToolResultAction.Complete,
        override val messages: MutableList<CommonMessage> = mutableListOf(),
        val callHistory: MutableMap<String, String> = mutableMapOf()
    ) : ToolResultMessage

    /**
     * Executes the back-and-forth tool use strategy.
     *
     * This function is the core logic for handling back-and-forth interaction with the Provider. It processes the
     * response, executes the function calls, tracks the call history, and handles various result actions like
     * retrying, updating, or completing the conversation based on tool results.
     *
     * @param robot The `RobotCore` instance responsible for orchestrating AI interaction and tool usage.
     * @param lastUserMessage The last user message before the tool response, used for context in
     * potential loop scenarios and interventions.
     * @param response The response generated by the AI provider.
     * @param provider The `Provider` responsible for handling tool messages and responses (this will be decoupled in future updates).
     * @param toolResult The result of the previous tool call, if applicable.
     * @return A `ToolResultMessage` object that encapsulates the action to be taken and any messages generated during the interaction.
     *
     * @since 0.1.0
     */
    internal operator fun invoke(
        robot: RobotCore<*, *, *>,
        lastUserMessage: CommonMessage,
        response: CommonMessage,
        provider: Provider<*>, // TODO Decouple Provider
        toolResult: Result?
    ): ToolResultMessage = with(robot) {
        val result = toolResult ?: Result()
        if (response.calls().isNotEmpty()) {

            var callStatus: CallStatus
            for (call in response.calls()) {
                try {
                    callStatus = call.function(result.callHistory)
                    when (callStatus) {
                        Success -> {
                            val callResult = result.callHistory.asIterable().last().value
                            provider.onToolResult(call, callResult) // Todo decouple
                            result.action = ToolResultAction.Update
                        }

                        Recalled -> {
                            result.messages.add(toolRecallMessage)
                            result.messages.add(interventionMessage(lastUserMessage, result.callHistory))
                            result.action = ToolResultAction.Retry
                            break
                        }

                        is Error -> {
                            result.messages.add((callStatus.exception.asSystemMessage()))
                            result.action = ToolResultAction.Retry
                            break
                        }
                    }
                } catch (e: Exception) {
                    logger.error("Illegal call: $call in ${response.calls()}")
                    throw e
                }
            }
        } else result.action = ToolResultAction.Complete
        result
    }


    /**
     * Provides a message to intervene when the AI is stuck in a loop, adding context from previous function calls.
     *
     * This method is triggered when a loop is detected during the AI's tool interaction. It modifies the user’s last message
     * to include contextual information about previous function call results, helping the AI break out of the loop and
     * reconsider its approach. Additionally, it resets the conversation by starting a new thread with this modified message.
     *
     * @param lastUserMessage The last message from the user in the current conversation.
     * @param callMap A map containing the history of function calls and their results, used to add context to the intervention message.
     * @return A system message that provides the necessary intervention for the AI to reprocess the conversation.
     *
     * @since 0.1.0
     */
    private fun interventionMessage(lastUserMessage: CommonMessage, callMap: MutableMap<String, String>) =
        ("${lastUserMessage.text()} \nNote the following may contain the answer." +
                "[${callMap.map { "${it.key} has result ${it.value}." }}]. " +
                "If you see the answer, say it in the desired format.").asSystemMessage()

    private val completionMessage
        get() = ("If you know the final answer after reading the result content from a function call, respond " +
                "with ONLY the answer in the require format")
            .asSystemMessage()

    private val toolRecallMessage
        get() = "You must find the answer in the user message and format it to the required format.".asSystemMessage()

    override fun onRequestMessage(toolSet: List<ToolConfig>): CommonMessage? = null
    override fun getTools(tools: List<ToolConfig>): List<ToolConfig> = tools
}