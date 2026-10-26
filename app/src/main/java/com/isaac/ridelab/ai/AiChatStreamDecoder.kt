package com.isaac.ridelab.ai

import org.json.JSONArray
import org.json.JSONObject
import java.io.Reader

/** Bounded Chat Completions SSE assembly. Reasoning is retained for tool replay, never rendered. */
internal class AiChatStreamDecoder {
    fun read(reader: Reader, checkActive: () -> Unit = {}): String {
        val text = StringBuilder()
        val reasoning = StringBuilder()
        val calls = sortedMapOf<Int, JSONObject>()
        var finish: String? = null
        var usage: JSONObject? = null
        var done = false
        var total = 0
        var assembled = 0
        val line = StringBuilder()
        val event = StringBuilder()
        fun append(target: StringBuilder, fragment: String) {
            assembled += fragment.length
            check(assembled <= 65_536)
            target.append(fragment)
        }
        fun consumeEvent() {
            val value = event.toString().trim()
            event.clear()
            if (value.isEmpty()) return
            if (value == "[DONE]") { done = true; return }
            val chunk = JSONObject(value)
            check(!chunk.has("error"))
            chunk.optJSONObject("usage")?.let { usage = it }
            val choices = chunk.optJSONArray("choices") ?: return
            for (i in 0 until choices.length()) {
                val choice = choices.getJSONObject(i)
                if (choice.optInt("index", 0) != 0) continue
                val delta = choice.optJSONObject("delta") ?: JSONObject()
                (delta.opt("content") as? String)?.let { append(text, it) }
                (delta.opt("reasoning_content") as? String)?.let { append(reasoning, it) }
                val fragments = delta.optJSONArray("tool_calls") ?: JSONArray()
                for (j in 0 until fragments.length()) {
                    val fragment = fragments.getJSONObject(j)
                    val index = fragment.getInt("index")
                    check(index in 0..5)
                    check(fragment.optString("type", "function") == "function")
                    val call = calls.getOrPut(index) { JSONObject().put("id", "").put("type", "function")
                        .put("function", JSONObject().put("name", "").put("arguments", "")) }
                    fun add(objectValue: JSONObject, name: String, source: JSONObject) {
                        val piece = source.opt(name) as? String ?: return
                        assembled += piece.length; check(assembled <= 65_536)
                        objectValue.put(name, objectValue.getString(name) + piece)
                    }
                    add(call, "id", fragment)
                    fragment.optJSONObject("function")?.let { function ->
                        add(call.getJSONObject("function"), "name", function)
                        add(call.getJSONObject("function"), "arguments", function)
                    }
                }
                (choice.opt("finish_reason") as? String)?.let { finish = it }
            }
        }
        while (!done) {
            checkActive()
            val next = reader.read()
            if (next == -1) break
            // SSE metadata repeats per token; bound wire overhead separately from assembled text.
            total++; check(total <= 2_097_152)
            if (next.toChar() == '\n') {
                val value = line.toString().removeSuffix("\r"); line.clear()
                if (value.isEmpty()) consumeEvent()
                else if (value.startsWith("data:")) {
                    if (event.isNotEmpty()) event.append('\n')
                    event.append(value.removePrefix("data:").removePrefix(" "))
                }
            } else { line.append(next.toChar()); check(line.length <= 65_536) }
        }
        // A disconnected stream or partial function arguments must never become a successful report.
        check(done && finish != null)
        val message = JSONObject().put("role", "assistant").put("content", text.toString())
        if (reasoning.isNotEmpty()) message.put("reasoning_content", reasoning.toString())
        if (calls.isNotEmpty()) {
            check(calls.keys.toList() == (0 until calls.size).toList())
            message.put("tool_calls", JSONArray(calls.values.toList()))
        }
        val result = JSONObject().put("choices", JSONArray().put(JSONObject().put("message", message).put("finish_reason", finish)))
        usage?.let { result.put("usage", it) }
        return result.toString()
    }
}
