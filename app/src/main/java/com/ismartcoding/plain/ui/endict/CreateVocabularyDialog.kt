package com.ismartcoding.plain.ui.endict

import android.os.Bundle
import android.view.View
import androidx.lifecycle.lifecycleScope
import com.ismartcoding.lib.channel.sendEvent
import com.ismartcoding.plain.LocalStorage
import com.ismartcoding.plain.VocabularyQuery
import com.ismartcoding.plain.api.BoxApi
import com.ismartcoding.plain.databinding.DialogCreateVocabularyBinding
import com.ismartcoding.plain.ui.BaseBottomSheetDialog
import com.ismartcoding.plain.ui.extensions.setSafeClick
import com.ismartcoding.plain.features.locale.LocaleHelper
import com.ismartcoding.plain.features.vocabulary.VocabularyList
import com.ismartcoding.lib.helpers.CoroutinesHelper.withIO
import com.ismartcoding.plain.features.VocabularyCreatedEvent
import com.ismartcoding.plain.ui.helpers.DialogHelper
import com.ismartcoding.plain.ui.views.LoadingButtonView
import kotlinx.coroutines.launch

class CreateVocabularyDialog : BaseBottomSheetDialog<DialogCreateVocabularyBinding>() {
    override fun getSubmitButton(): LoadingButtonView {
        return binding.button
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.template.selectValue = "none"
        updateTemplateRow()
        binding.button.setSafeClick {
            if (hasInputError()) {
                return@setSafeClick
            }

            lifecycleScope.launch {
                doCreateAsync()
            }
        }
        addFormItem(binding.name)
        addFormItem(binding.template)
    }

    private suspend fun doCreateAsync() {
        blockFormUI()
        val template = binding.template.selectValue
        if (template == "none") {
            addOrUpdateDbAsync(setOf())
            dismiss()
            return
        }

        val r = withIO {
            BoxApi.mixQueryAsync(VocabularyQuery(template))
        }

        if (!r.isSuccess()) {
            unblockFormUI()
            DialogHelper.showErrorDialog(requireContext(), r.getErrorMessage())
            return
        }

        r.response?.data?.vocabulary?.let {
            addOrUpdateDbAsync(it.words.toSet())
        }

        dismiss()
    }

    private suspend fun addOrUpdateDbAsync(words: Set<String>) {
        withIO {
            VocabularyList.addOrUpdateAsync("") {
                boxId = LocalStorage.selectedBoxId
                name = binding.name.text
                this.words = words.distinct().toMutableList()
            }
        }
        sendEvent(VocabularyCreatedEvent())
    }

    private fun updateTemplateRow() {
        binding.template.run {
            setValueText(LocaleHelper.getString("vocabulary_${selectValue}"))
            setClick {
                SelectTemplateDialog { id ->
                    selectValue = id
                    updateTemplateRow()
                }.show()
            }
        }
    }
}