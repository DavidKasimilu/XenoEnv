package com.example.ui.viewmodel

import android.os.Environment
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

data class FileItem(val file: File)

class FileViewModel : ViewModel() {
    private val _currentDirectory = MutableStateFlow(Environment.getExternalStorageDirectory())
    val currentDirectory = _currentDirectory.asStateFlow()

    private val _files = MutableStateFlow<List<FileItem>>(emptyList())
    val files = _files.asStateFlow()

    init {
        loadFiles()
    }

    fun loadFiles() {
        val directory = _currentDirectory.value
        val files = directory.listFiles()?.map { FileItem(it) } ?: emptyList()
        _files.value = files.sortedWith(compareBy({ !it.file.isDirectory }, { it.file.name.lowercase() }))
    }

    fun navigateTo(directory: File) {
        if (directory.isDirectory) {
            _currentDirectory.value = directory
            loadFiles()
        }
    }
}
