package com.example.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.viewmodel.FileViewModel

@Composable
fun FileManagerScreen(viewModel: FileViewModel = viewModel()) {
    val files by viewModel.files.collectAsState()
    val currentDir by viewModel.currentDirectory.collectAsState()

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Current: ${currentDir.path}", style = MaterialTheme.typography.titleSmall)
        LazyColumn {
            itemsIndexed(files, key = { index, fileItem -> "${fileItem.file.absolutePath}_$index" }) { _, fileItem ->
                ListItem(
                    headlineContent = { Text(fileItem.file.name) },
                    modifier = Modifier.clickable {
                        if (fileItem.file.isDirectory) {
                            viewModel.navigateTo(fileItem.file)
                        }
                    }
                )
            }
        }
    }
}
