package com.tomsdiary

import android.content.Context
import android.content.SharedPreferences

/**
 * Configuration for the LLM (OpenAI-compatible API).
 * 
 * The app can now use any OpenAI-compatible endpoint, including:
 * - Local vLLM instances (http://localhost:8001/v1)
 * - Ollama (http://localhost:11434/v1)
 * - Any other OpenAI-compatible API
 * 
 * Update these values in the app settings or modify the defaults here.
 */
object LLMConfig {
    
    // Default configuration - can be overridden in SharedPreferences
    const val DEFAULT_BASE_URL = "http://localhost:8001/v1"
    const val DEFAULT_API_KEY = "placeholder"  // Most local LLMs don't require a real key
    const val DEFAULT_MODEL = "default"
    
    fun getBaseUrl(context: Context): String {
        val prefs = context.getSharedPreferences("toms_diary", Context.MODE_PRIVATE)
        return prefs.getString("llm_base_url", DEFAULT_BASE_URL) ?: DEFAULT_BASE_URL
    }
    
    fun getApiKey(context: Context): String {
        val prefs = context.getSharedPreferences("toms_diary", Context.MODE_PRIVATE)
        return prefs.getString("llm_api_key", DEFAULT_API_KEY) ?: DEFAULT_API_KEY
    }
    
    fun getModel(context: Context): String {
        val prefs = context.getSharedPreferences("toms_diary", Context.MODE_PRIVATE)
        return prefs.getString("llm_model", DEFAULT_MODEL) ?: DEFAULT_MODEL
    }
    
    fun setBaseUrl(context: Context, baseUrl: String) {
        val prefs = context.getSharedPreferences("toms_diary", Context.MODE_PRIVATE)
        prefs.edit().putString("llm_base_url", baseUrl).apply()
    }
    
    fun setApiKey(context: Context, apiKey: String) {
        val prefs = context.getSharedPreferences("toms_diary", Context.MODE_PRIVATE)
        prefs.edit().putString("llm_api_key", apiKey).apply()
    }
    
    fun setModel(context: Context, model: String) {
        val prefs = context.getSharedPreferences("toms_diary", Context.MODE_PRIVATE)
        prefs.edit().putString("llm_model", model).apply()
    }
}
