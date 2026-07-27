package app.maoyankanshu.novel.selfuse.ui.screens

import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.maoyankanshu.novel.selfuse.BuildConfig
import app.maoyankanshu.novel.selfuse.LibraryStore
import app.maoyankanshu.novel.selfuse.ReaderPreferences

@Composable
fun ProfileScreen(
    contentPadding: PaddingValues,
    onLibraryRestored: () -> Unit,
) {
    val context = LocalContext.current
    val preferences = remember { ReaderPreferences.get(context) }
    var fontSize by remember { mutableIntStateOf(preferences.fontSize()) }
    var nightMode by remember { mutableStateOf(preferences.nightMode()) }
    var showAbout by remember { mutableStateOf(false) }

    val createBackup = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        try {
            context.contentResolver.openOutputStream(uri)?.use { stream ->
                LibraryStore.get(context).exportTo(stream)
            }
            Toast.makeText(context, "书库备份已保存", Toast.LENGTH_SHORT).show()
        } catch (_: Exception) {
            Toast.makeText(context, "备份文件无法使用", Toast.LENGTH_SHORT).show()
        }
    }

    val restoreBackup = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        try {
            val count = context.contentResolver.openInputStream(uri)?.use { stream ->
                LibraryStore.get(context).importFrom(stream)
            } ?: 0
            Toast.makeText(context, "已恢复 $count 本书", Toast.LENGTH_SHORT).show()
            onLibraryRestored()
        } catch (_: Exception) {
            Toast.makeText(context, "备份文件无法使用", Toast.LENGTH_SHORT).show()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "阅读设置",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = "默认字号：${fontSize}sp",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.semantics { contentDescription = "默认字号 $fontSize sp" },
        )

        OutlinedButton(
            onClick = {
                preferences.setFontSize(fontSize - 1)
                fontSize = preferences.fontSize()
            },
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = "减小默认字号" },
        ) {
            Text("减小默认字号")
        }
        OutlinedButton(
            onClick = {
                preferences.setFontSize(fontSize + 1)
                fontSize = preferences.fontSize()
            },
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = "增大默认字号" },
        ) {
            Text("增大默认字号")
        }
        OutlinedButton(
            onClick = {
                preferences.setNightMode(!nightMode)
                nightMode = preferences.nightMode()
            },
            modifier = Modifier
                .fillMaxWidth()
                .semantics {
                    contentDescription = if (nightMode) "关闭夜间阅读" else "开启夜间阅读"
                },
        ) {
            Text(if (nightMode) "关闭夜间阅读" else "开启夜间阅读")
        }

        Spacer(Modifier.height(8.dp))

        OutlinedButton(
            onClick = { createBackup.launch("笔趣阁自用版书库备份.zip") },
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = "备份本地书库" },
        ) {
            Text("备份本地书库")
        }
        OutlinedButton(
            onClick = { restoreBackup.launch(arrayOf("application/zip", "*/*")) },
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = "从备份恢复书库" },
        ) {
            Text("从备份恢复书库")
        }
        OutlinedButton(
            onClick = {
                context.startActivity(Intent(Settings.ACTION_DISPLAY_SETTINGS))
            },
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = "打开系统显示设置" },
        ) {
            Text("系统显示设置")
        }
        OutlinedButton(
            onClick = { showAbout = true },
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = "关于笔趣阁自用版" },
        ) {
            Text("关于笔趣阁（自用）")
        }

        Spacer(Modifier.height(8.dp))
        Text(
            text = "账户、支付、广告和远端推送不会迁入自用版。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    if (showAbout) {
        AlertDialog(
            onDismissRequest = { showAbout = false },
            title = { Text("笔趣阁（自用） ${BuildConfig.VERSION_NAME}") },
            text = {
                Text(
                    "这是一个独立维护的本地阅读器。\n\n" +
                        "书籍、进度、书签、历史和备份默认只保存在你的设备中。\n\n" +
                        "在线导入只接入中文维基文库的公共领域或自由许可文本；" +
                        "其他网络文件仅在你主动提供直链时下载。",
                )
            },
            confirmButton = {
                TextButton(onClick = { showAbout = false }) {
                    Text("知道了")
                }
            },
        )
    }
}
