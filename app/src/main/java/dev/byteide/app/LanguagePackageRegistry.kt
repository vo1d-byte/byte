package dev.byteide.app

import android.content.Context
import org.json.JSONObject

data class LanguagePack(
    val spec: ToolchainPackageSpec,
    val category: String,
    val description: String,
    val supportedAbis: List<String>
)
data class LanguagePackCatalog(val version: String, val packages: List<LanguagePack>)

object LanguagePackageRegistry {
    fun load(context: Context): LanguagePackCatalog {
        val root = JSONObject(context.assets.open("language-packs.json").bufferedReader().use { it.readText() })
        require(root.optInt("schemaVersion") == 1) { "Unsupported language catalog schema" }
        val array = root.optJSONArray("packages") ?: error("Catalog packages must be an array")
        val packs = (0 until array.length()).map { index ->
            val item = array.getJSONObject(index)
            val executables = item.optJSONArray("executablePaths")
            val args = item.optJSONArray("languageServerArguments")
            val abis = item.optJSONArray("supportedAbis")
            LanguagePack(
                spec = ToolchainPackageSpec(
                    packageId = item.getString("packageId"),
                    displayName = item.getString("displayName"),
                    languageId = item.getString("languageId"),
                    version = item.getString("version"),
                    artifactUrl = item.getString("artifactUrl"),
                    sha256 = item.getString("sha256"),
                    archiveFormat = item.optString("archiveFormat", "zip"),
                    executablePaths = (0 until (executables?.length() ?: 0)).map { executables!!.getString(it) },
                    languageServerExecutable = item.optString("languageServerExecutable").takeIf { it.isNotBlank() },
                    languageServerArguments = (0 until (args?.length() ?: 0)).map { args!!.getString(it) }
                ),
                category = item.optString("category", "Language"),
                description = item.optString("description", ""),
                supportedAbis = (0 until (abis?.length() ?: 0)).map { abis!!.getString(it) }
            )
        }
        require(packs.map { it.spec.packageId }.distinct().size == packs.size) { "Duplicate package IDs in catalog" }
        return LanguagePackCatalog(root.optString("catalogVersion", "unknown"), packs)
    }
}
