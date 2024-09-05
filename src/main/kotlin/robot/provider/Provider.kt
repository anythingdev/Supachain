@file:Suppress("unused")

package dev.supachain.robot.provider

import dev.supachain.robot.director.DirectorCore
import dev.supachain.robot.provider.responses.CommonResponse
import dev.supachain.robot.tool.ToolConfig
import dev.supachain.robot.tool.ToolMap
import dev.supachain.robot.tool.strategies.ToolUseStrategy

/**
 * Abstract base class for model providers.
 *
 * This class provides a foundation for implementing different providers,
 * each with its own capabilities and configurations. It manages features,
 * handles requests, and encapsulates error handling.
 *
 * @param T The specific type of the `Provider` subclass, enabling fluent configuration using the `invoke` operator.
 *
 * @since 0.1.0-alpha

 */
abstract class Provider<T : Provider<T>>: Actions{
    abstract var maxRetries: Int
    abstract var toolsAllowed: Boolean
    var loopDetection: Boolean = true
    abstract var toolStrategy: ToolUseStrategy
    private val featureMap: Map<Feature, ProviderFeatureRequest> = getFeatureMap()

    /**
     * Executes a request to the AI provider for a specific feature.
     *
     * This function dynamically dispatches the request to the appropriate handler
     * based on the `featureMap`.
     *
     * @param feature The AI feature to be executed (e.g., `Feature.CHAT`, `Feature.EMBEDDING`).
     * @param director The `Director` instance responsible for orchestrating the AI interaction.
     * @return A `CommonResponse` object containing the result of the feature execution.
     * @throws IllegalStateException If the requested feature is not supported by this AI provider.
     *
     * @since 0.1.0-alpha

     */
    internal suspend inline
    fun request(feature: Feature, director: DirectorCore): CommonResponse = this.featureMap[feature]?.invoke(director)
        ?: throw IllegalStateException("Unsupported Feature: The $feature feature is not support by $name")

    @Suppress("UNCHECKED_CAST")
    inline operator
    fun invoke(modify: T.() -> Unit) = (this as T).modify()

    internal val DirectorCore.tools: List<ToolConfig> get()=
        if(toolsAllowed) toolStrategy.getTools(this.toolMap) else emptyList()

}