package com.ismartcoding.plain.ui.file

import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.ismartcoding.lib.brv.utils.bindingAdapter
import com.ismartcoding.lib.brv.utils.linear
import com.ismartcoding.lib.brv.utils.setup
import com.ismartcoding.lib.channel.receiveEvent
import com.ismartcoding.lib.extensions.*
import com.ismartcoding.lib.helpers.CoroutinesHelper.withIO
import com.ismartcoding.lib.helpers.FormatHelper
import com.ismartcoding.plain.LocalStorage
import com.ismartcoding.plain.R
import com.ismartcoding.plain.data.enums.ActionSourceType
import com.ismartcoding.plain.databinding.DialogFilesBinding
import com.ismartcoding.plain.extensions.formatDateTime
import com.ismartcoding.plain.features.ActionEvent
import com.ismartcoding.plain.features.Permission
import com.ismartcoding.plain.features.PermissionResultEvent
import com.ismartcoding.plain.features.Permissions
import com.ismartcoding.plain.features.audio.AudioPlayerService
import com.ismartcoding.plain.features.audio.DPlaylistAudio
import com.ismartcoding.plain.features.file.FileSystemHelper
import com.ismartcoding.plain.features.locale.LocaleHelper
import com.ismartcoding.plain.features.theme.AppThemeHelper
import com.ismartcoding.plain.ui.BaseDialog
import com.ismartcoding.plain.ui.PdfViewerDialog
import com.ismartcoding.plain.ui.TextEditorDialog
import com.ismartcoding.plain.ui.audio.AudioPlayerDialog
import com.ismartcoding.plain.ui.extensions.*
import com.ismartcoding.plain.ui.helpers.DialogHelper
import com.ismartcoding.plain.ui.helpers.FileSortHelper
import com.ismartcoding.plain.ui.models.IDataModel
import com.ismartcoding.plain.ui.preview.PreviewDialog
import com.ismartcoding.plain.ui.preview.PreviewItem
import com.ismartcoding.plain.ui.preview.TransitionHelper
import kotlinx.coroutines.launch
import kotlin.io.path.Path
import kotlin.io.path.moveTo

class FilesDialog : BaseDialog<DialogFilesBinding>() {
    val viewModel: FilesViewModel by viewModels()


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        immersionBar {
            statusBarColor(R.color.canvas)
            titleBar(binding.layout)
            titleBarMarginTop(statusBarHeight)
            transparentNavigationBar()
            statusBarDarkFont(!AppThemeHelper.isDarkMode())
        }

        updatePasteAction()

        binding.bottomAction.run {
            initMenu(R.menu.action_files)
            onMenuItemClick {
                FilesBottomMenuHelper.onMenuItemClick(requireContext(), this@FilesDialog, this)
            }
        }

        binding.initToggleMode(viewLifecycleOwner, viewModel)
        initEvents()

        binding.toolbar.run {
            initMenu(R.menu.files)
            menu.findItem(R.id.show_hidden).isChecked = LocalStorage.showHiddenFiles
            FileSortHelper.getSelectedSortItem(menu, LocalStorage.fileSortBy).highlightTitle(requireContext())
            onBack {
                onBackPressed()
            }
            onSearch { q ->
                if (viewModel.searchQ != q) {
                    viewModel.searchQ = q
                    binding.list.page.refresh()
                }
            }

            onMenuItemClick {
                FilesTopMenuHelper.onMenuItemClick(lifecycleScope, requireContext(), viewModel, binding, this)
            }
        }

        val rv = binding.list.rv
        rv.linear().setup {
            addType<FileModel>(R.layout.item_file)
            onBind {
                val m = getModel<FileModel>()
                if (m.data.path.isVideoFast() || m.data.path.isImageFast()) {
                    TransitionHelper.put(m.data.path, itemView.findViewById(R.id.image))
                }
            }

            R.id.container.onLongClick {
                viewModel.toggleMode.value = true
                rv.bindingAdapter.setChecked(bindingAdapterPosition, true)
            }

            checkable(onItemClick = {
                val m = getModel<FileModel>()
                if (m.data.isDir) {
                    navigateTo(m.data.path)
                } else if (m.data.path.isVideoFast()) {
                    val items = getModelList<FileModel>()
                    PreviewDialog().show(
                        items = items.filter { !it.data.isDir && it.data.path.isVideoFast() }.map { s -> PreviewItem(s.data.path, s.data.path) },
                        initKey = m.data.path,
                    )
                } else if (m.data.path.isImageFast()) {
                    val items = getModelList<FileModel>()
                    PreviewDialog().show(
                        items = items.filter { !it.data.isDir && it.data.path.isImageFast() }.map { s -> PreviewItem(s.data.path, s.data.path) },
                        initKey = m.data.path,
                    )
                } else if (m.data.path.isAudioFast()) {
                    AudioPlayerService.play(requireContext(), DPlaylistAudio.fromPath(context, m.data.path))
                    AudioPlayerDialog().show()
                    Permissions.checkNotification()
                } else if (m.data.path.isTextFile()) {
                    TextEditorDialog(m.data.path).show()
                } else if (m.data.path.isPdfFile()) {
                    PdfViewerDialog(m.data.path).show()
                }
            }, onChecked = {
                updateBottomActions()
                updateTitle()
            })
        }

        binding.list.page.onRefresh {
            updateList()
        }
        binding.breadcrumb.navigateTo = {
            navigateTo(it.path)
        }
        updateBreadcrumb()
        checkPermission()
    }

    override fun onBackPressed() {
        if (viewModel.toggleMode.value == true) {
            viewModel.toggleMode.value = false
            return
        }

        if (viewModel.cutFiles.isNotEmpty() || viewModel.copyFiles.isNotEmpty()) {
            viewModel.cutFiles.clear()
            viewModel.copyFiles.clear()
            binding.pasteAction.performHide()
            return
        }

        if (viewModel.path == viewModel.root) {
            dismiss()
        } else {
            navigateTo(viewModel.path.substringBeforeLast('/'))
        }
    }

    private fun updatePasteAction() {
        binding.pasteAction.run {
            setNavigationOnClickListener {
                viewModel.cutFiles.clear()
                viewModel.copyFiles.clear()
                binding.pasteAction.performHide()
            }

            findViewById<MaterialButton>(R.id.paste).setSafeClick {
                lifecycleScope.launch {
                    if (viewModel.cutFiles.isNotEmpty()) {
                        DialogHelper.showLoading()
                        withIO {
                            viewModel.cutFiles.forEach {
                                val dstFile = java.io.File(viewModel.path + "/" + it.id.getFilenameFromPath())
                                if (!dstFile.exists()) {
                                    Path(it.id).moveTo(dstFile.toPath(), true)
                                } else {
                                    Path(it.id).moveTo(Path(dstFile.newPath()), true)
                                }
                            }
                            viewModel.cutFiles.clear()
                        }
                        DialogHelper.hideLoading()
                        binding.list.page.refresh()
                        binding.pasteAction.performHide()
                    } else if (viewModel.copyFiles.isNotEmpty()) {
                        DialogHelper.showLoading()
                        withIO {
                            viewModel.copyFiles.forEach {
                                val dstFile = java.io.File(viewModel.path + "/" + it.id.getFilenameFromPath())
                                if (!dstFile.exists()) {
                                    java.io.File(it.id).copyRecursively(dstFile, true)
                                } else {
                                    java.io.File(it.id)
                                        .copyRecursively(java.io.File(dstFile.newPath()), true)
                                }
                            }
                            viewModel.copyFiles.clear()
                        }
                        DialogHelper.hideLoading()
                        binding.list.page.refresh()
                        binding.pasteAction.performHide()
                    }
                }

            }
        }
    }

    private fun initEvents() {
        receiveEvent<PermissionResultEvent> {
            checkPermission()
        }
        receiveEvent<ActionEvent> { event ->
            if (event.source == ActionSourceType.FILE) {
                binding.list.page.refresh()
            }
        }
    }

    fun updateBreadcrumb() {
        binding.breadcrumb.setData(viewModel.breadcrumbs, viewModel.getAndUpdateSelectedIndex())
    }

    private fun navigateTo(path: String) {
        viewModel.path = path
        updateBreadcrumb()
        binding.list.rv.bindingAdapter.checkedPosition.clear()
        binding.list.page.showLoading()
    }

    private fun checkPermission() {
        binding.list.checkPermission(Permission.WRITE_EXTERNAL_STORAGE)
        binding.breadcrumb.isVisible = Permission.WRITE_EXTERNAL_STORAGE.can()
    }

    private fun updateList() {
        lifecycleScope.launch {
            val p = viewModel.path
            val items = withIO {
                if (viewModel.searchQ.isNotEmpty()) FileSystemHelper.search(viewModel.searchQ, p, LocalStorage.showHiddenFiles) else FileSystemHelper.getFilesList(
                    p,
                    LocalStorage.showHiddenFiles,
                    LocalStorage.fileSortBy
                )
            }
            if (p != viewModel.path) {
                updateList()
                return@launch
            }

            val bindingAdapter = binding.list.rv.bindingAdapter
            val toggleMode = bindingAdapter.toggleMode
            val checkedItems = bindingAdapter.getCheckedModels<FileModel>()
            binding.list.page.addData(items.map { f ->
                FileModel(f).apply {
                    title = f.name
                    this.toggleMode = toggleMode
                    isChecked = checkedItems.any { it.data.path == f.path }
                    if (f.isDir) {
                        startIconId = R.drawable.ic_folder
                        val count = f.children
                        subtitle = LocaleHelper.getQuantityString(R.plurals.items, count) + ", " + f.updatedAt.formatDateTime()
                    } else {
                        if (f.path.isImageFast() || f.path.isVideoFast()) {
                            image = f.path
                        } else if (f.path.isAudioFast()) {
                            startIconId = R.drawable.ic_file_audio
                        } else {
                            startIconId = R.drawable.ic_file
                        }
                        subtitle = FormatHelper.formatBytes(f.size) + ", " + f.updatedAt.formatDateTime()
                    }
                }
            })
            updateTitle()
        }
    }

    fun updateTitle() {
        binding.toolbar.updateFilesTitle(viewModel, binding.list.rv)
    }

    fun showPasteAction() {
        binding.pasteAction.isVisible = true
        binding.pasteAction.performShow()
        val titleView = binding.pasteAction.findViewById<TextView>(R.id.custom_title)
        if (viewModel.cutFiles.isNotEmpty()) {
            titleView.text = LocaleHelper.getQuantityString(R.plurals.moving_items, viewModel.cutFiles.size)
        } else if (viewModel.copyFiles.isNotEmpty()) {
            titleView.text = LocaleHelper.getQuantityString(R.plurals.copying_items, viewModel.copyFiles.size)
        }
        val context = binding.list.page.context
        binding.list.page.updateLayoutParams<FrameLayout.LayoutParams> {
            bottomMargin = context.px(R.dimen.size_hhl)
        }
    }

    private fun hidePasteAction() {
        viewModel.cutFiles.clear()
        viewModel.copyFiles.clear()
        binding.pasteAction.performHide()
        binding.list.page.updateLayoutParams<FrameLayout.LayoutParams> {
            bottomMargin = 0
        }
    }

    private fun updateBottomActions() {
        val items = binding.list.rv.bindingAdapter.getCheckedModels<IDataModel>()
        val count = items.size
        if (count > 0) {
            binding.bottomAction.performShow()
            binding.bottomAction.menu.findItem(R.id.decompress)?.isVisible = count == 1 && items[0].data.id.endsWith(".zip")
            binding.bottomAction.menu.findItem(R.id.rename)?.isVisible = count == 1
        } else {
            binding.bottomAction.performHide()
        }
    }
}

