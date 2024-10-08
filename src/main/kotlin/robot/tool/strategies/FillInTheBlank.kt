package dev.supachain.robot.tool.strategies

import dev.supachain.robot.director.RobotCore
import dev.supachain.robot.director.asFunctionCall
import dev.supachain.robot.messenger.ToolResultAction
import dev.supachain.robot.messenger.ToolResultMessage
import dev.supachain.robot.provider.models.CommonMessage
import dev.supachain.robot.provider.models.TextMessage
import dev.supachain.robot.provider.models.asAssistantMessage
import dev.supachain.robot.provider.models.asSystemMessage
import dev.supachain.robot.tool.ToolConfig
import dev.supachain.robot.tool.asKFunctionString
import dev.supachain.utilities.templates

/**
 * Represents a tool-use strategy where the Provider fills in placeholders within a template response.
 * It allows the Provider to combine or chain multiple tool calls to achieve a complex task.
 *
 * This data object implements the `ToolUseStrategy` interface. It handles responses that contain
 * placeholders for function calls. The strategy directly processes these placeholders by executing the corresponding
 * functions and inserting their results into the response template.
 *
 * @since 0.1.0
 */
data object FillInTheBlank : ToolUseStrategy {
    /**
     * Internal class representing the result of the tool execution within the fill-in-the-blank strategy.
     *
     * This class holds the action to be taken after the tool's execution and the list of messages generated
     * from filling the placeholders in the template.
     *
     * @property action Defines the action to take, such as completing the interaction or replacing the content.
     * @property messages A mutable list of messages generated during the placeholder filling.
     *
     * @since 0.1.0
     */
    internal class Result(
        override var action: ToolResultAction = ToolResultAction.Complete,
        override val messages: MutableList<CommonMessage> = mutableListOf(),
    ) : ToolResultMessage

    /**
     * Constructs a system message listing available functions and providing usage instructions.
     *
     * This function generates a system message that informs the Provider about the available functions
     * and how to use them within response templates.
     *
     * @param toolSet The list of available tools in the current session.
     * @return A `TextMessage` object representing the system message with detailed instructions.
     *
     * @since 0.1.0
     */
    override fun onRequestMessage(toolSet: List<ToolConfig>): TextMessage =
        if (toolSet.isEmpty()) "Answer to the best of your ability".asSystemMessage()
        else
            ("Declared Functions: [${toolSet.asKFunctionString()}, " +
                    "fun doubleArrayOf(vararg elements: Double): DoubleArray, " +
                    "fun floatArrayOf(vararg elements: Float): FloatArray, " +
                    "fun longArrayOf(vararg elements: Long): LongArray, " +
                    "fun intArrayOf(vararg elements: Int): IntArray, " +
                    "fun charArrayOf(vararg elements: Char): CharArray, " +
                    "fun shortArrayOf(vararg elements: Short): ShortArray, " +
                    "fun byteArrayOf(vararg elements: Byte): ByteArray, " +
                    "fun booleanArrayOf(vararg elements: Boolean): BooleanArray]. " +
                    "These functions are already declared for you, " +
                    "you can just call it in your template string if available. " +
                    "#Example:\n" +
                    "If this question requires you to add, " +
                    "and an add function is available do not show the results. " +
                    "You must write as a kotlin template string except $ is \u200B . " +
                    "For instance if the question was [can you add b + c?], you would say something like " +
                    "[The answer is \u200B{add(b, c)}]. If the function you want is !in Declared Functions, " +
                    "then just answer to your best ability what you know and show the results. " +
                    "Remember to add \u200B in front of `{` like \u200B{...} instead of \${...}. " +
                    "Sometimes you need nested function calls. " +
                    "For instance if the question was [can you add a * b + c?], " +
                    "you can write something like [The answer is \u200B{add(multiple(a, b), c)}]. " +
                    "For instance if the question was [can you add d + a * b + c - x?], " +
                    "you can write something like [The answer is \u200B{subtract(add(add(d, multiple(a, b)), c)}, x)]" +
                    "For instance if the question was [v + w * x / y - z], " +
                    "you can say something like [The answer to the equation is \u200B{minus(add(v, divide(multiplies(w, x), y)), z)}]. " +
                    "You must follows these instructions. " +
                    "1. If you knew the answer to a + b, but a function called `add` is in Declared Functions, " +
                    "you must always write you answer call function in template string. I.e [The answer to a + b is \u200B{add(a + b]}]. " +
                    "2. If you know the answer to something, but there is a function for that something. " +
                    "Use tha function in the template string." +
                    "3. In terms of arrays, say you had a, b, c in an array, " +
                    "never write [a, b, c] literal form, always write the most suitable function call " +
                    "like intArrayOf(a, b, c) or arrayOf(a, b, c) etc.\n" +
                    "4. You are not allowed to use kotlin named arguments. For instance for fun z(a: Int, b:Int), " +
                    "you cannot write `We have \u200B{z(a=0, b=1)}`. You can write `We have \u200B{z(0, 1)}`.\n" +
                    "5. Whenever you encounter an arithmetic operation or any calculation, " +
                    "instead of directly writing the expression (e.g., \u200B{11 * 71}), " +
                    "wrap the operation inside a descriptive function or method (e.g., \u200B{multiply(11 * 71)}). " +
                    "This approach should be applied not just for multiplication, but for any calculation, " +
                    "such as addition, subtraction, division, or more complex operations. " +
                    "The function names should clearly represent the operation or purpose " +
                    "(e.g., \u200B{add(5 + 3)}, \u200B{subtract(10 - 2)}, " +
                    "\u200B{calculateArea(length * width)}), " +
                    "enhancing the clarity and maintainability of the code.\n" +
                    "Remember: If a function is not in Declared Functions, " +
                    "Do not make up tool. For instance if were to sum a and b and " +
                    "there isn't some kind of sum/add function. Just guess the answer" +
                    "I.e `whats 2 - 1` you would say `The answer is 1` be guessing and not templating. " +
                    "In all Kotlin code, strictly adhere to using positional arguments when calling functions. " +
                    "Do not use named arguments e.g., You cannot write `myFunction(arg2 = \"value\")`. " +
                    "You must not write named arguments, i.e Do not write `taxRate(amount = 90000.5)`."
                    ).asSystemMessage()

    /**
     * Invokes the fill-in-the-blank tool use strategy.
     *
     * This function processes a response from the AI provider by executing any function calls
     * embedded within the response template and replacing them with their results.
     *
     * @param robot The `RobotCore` instance responsible for managing the AI interaction.
     * @param response The `Message` received from the AI provider, potentially containing function calls.
     * @return A `ToolResultMessage` object containing the processed result after template evaluation.
     *
     * @since 0.1.0
     */
    operator fun invoke(robot: RobotCore<*, *, *>, response: CommonMessage): ToolResultMessage = with(robot) {
        val template = response.text().toString().templates()
        val results = template.expressions.map { it.asFunctionCall()().toString() }
        Result(ToolResultAction.ReplaceAndComplete).apply {
            messages.add(template.fill(results).asAssistantMessage())
        }
    }

    override fun getTools(tools: List<ToolConfig>): List<ToolConfig> = emptyList()
}