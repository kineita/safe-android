package io.gnosis.safe.ui.settings.app.passcode

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import androidx.core.widget.doOnTextChanged
import androidx.lifecycle.Observer
import androidx.navigation.fragment.findNavController
import io.gnosis.safe.R
import io.gnosis.safe.ScreenId
import io.gnosis.safe.databinding.FragmentPasscodeBinding
import io.gnosis.safe.di.components.ViewComponent
import io.gnosis.safe.ui.base.BaseStateViewModel
import io.gnosis.safe.ui.base.fragment.BaseViewBindingFragment
import io.gnosis.safe.utils.showConfirmDialog
import pm.gnosis.svalinn.common.utils.showKeyboardForView
import pm.gnosis.svalinn.common.utils.snackbar
import pm.gnosis.svalinn.common.utils.visible
import javax.inject.Inject


class EnterPasscodeFragment : BaseViewBindingFragment<FragmentPasscodeBinding>() {

    override fun screenId() = ScreenId.PASSCODE_ENTER

    @Inject
    lateinit var viewModel: PasscodeViewModel

    override fun inflateBinding(inflater: LayoutInflater, container: ViewGroup?): FragmentPasscodeBinding =
        FragmentPasscodeBinding.inflate(inflater, container, false)

    override fun inject(component: ViewComponent) {
        component.inject(this)
    }

    override fun onResume() {
        super.onResume()
        binding.input.showKeyboardForView()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel.state.observe(viewLifecycleOwner, Observer {
            when (val viewAction = it.viewAction) {
                is PasscodeViewModel.AllOwnersRemoved -> {
                    snackbar(requireView(), R.string.passcode_disabled)
                    findNavController().navigateUp()
                }
                is BaseStateViewModel.ViewAction.ShowError -> {
                    binding.errorMessage.setText(R.string.settings_passcode_owner_removal_failed)
                    binding.errorMessage.visible(true)
                }
                is PasscodeViewModel.PasscodeWrong -> {
                    binding.errorMessage.setText(R.string.settings_passcode_wrong_passcode)
                    binding.errorMessage.visible(true)
                    binding.input.setText("")
                }
                is PasscodeViewModel.PasscodeCorrect -> {
                    findNavController().navigateUp()
                }
            }
        })

        with(binding) {
            createPasscode.setText(R.string.settings_passcode_enter_your_current_passcode)
            helpText.visible(false)

            backButton.setOnClickListener {
                findNavController().navigateUp()
            }

            status.visibility = View.INVISIBLE

            val digits = listOf(digit1, digit2, digit3, digit4, digit5, digit6)
            input.showKeyboardForView()

            //Disable done button
            input.setOnEditorActionListener { _, actionId, _ ->
                actionId == EditorInfo.IME_ACTION_DONE
            }

            input.doOnTextChanged(onSixDigitsHandler(digits, requireContext()) { digitsAsString ->
                viewModel.unlockWithPasscode(digitsAsString)
            })

            errorMessage.setText(R.string.settings_passcode_wrong_passcode)
            actionButton.setText(R.string.settings_passcode_forgot_your_passcode)
            actionButton.setOnClickListener {
                input.hideSoftKeyboard()
                binding.errorMessage.visibility = View.INVISIBLE

                showConfirmDialog(
                    requireContext(),
                    message = R.string.settings_passcode_confirm_disable_passcode,
                    confirm = R.string.settings_passcode_disable_passcode,
                    confirmColor = R.color.error
                ) {
                    viewModel.onForgotPasscode()
                }
            }
        }
    }
}