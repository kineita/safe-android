package io.gnosis.safe.ui.settings.owner.keystone

import android.animation.Animator
import android.os.Bundle
import android.os.Parcelable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.content.res.AppCompatResources
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import androidx.navigation.Navigation
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.paging.LoadState
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import com.keystone.module.Account
import com.keystone.module.Extra
import com.keystone.module.MultiAccounts
import com.keystone.module.OkxExtra
import io.gnosis.data.models.Owner
import io.gnosis.data.models.OwnerTypeConverter
import io.gnosis.safe.R
import io.gnosis.safe.ScreenId
import io.gnosis.safe.databinding.FragmentKeystoneOwnerSelectionBinding
import io.gnosis.safe.di.components.ViewComponent
import io.gnosis.safe.ui.base.fragment.BaseViewBindingFragment
import io.gnosis.safe.ui.settings.owner.selection.DerivedOwnerListAdapter
import io.gnosis.safe.ui.settings.owner.selection.DerivedOwnerPagingProvider
import io.gnosis.safe.ui.settings.owner.selection.DerivedOwners
import kotlinx.coroutines.launch
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.RawValue
import pm.gnosis.svalinn.common.utils.visible
import java.math.BigInteger
import javax.inject.Inject

// Value wrappers to pass data between fragments
@Parcelize
data class KeystoneAccount (
    var chain: String,
    var path: String,
    var publicKey: String,
    var name: String,
    var xfp: String,
    val note: String?,
    var extraChainId: Int,
    var chainCode: String,
    var extendedPublicKey: String
) : Parcelable {
    fun account(): Account {
      return Account(
          chain, path, publicKey, name, xfp, note,
          Extra(OkxExtra(extraChainId)), chainCode, extendedPublicKey
      )
    }

    constructor(account: Account) : this(
        account.chain,
        account.path,
        account.publicKey,
        account.name,
        account.xfp,
        account.note,
        account.extra.okx.chainId,
        account.getChainCode(),
        account.getExtendedPublicKey()
    )
}

@Parcelize
data class KeystoneMultiAccount (
    val masterFingerprint: String,
    val keys: List<KeystoneAccount>,
    val device: String?,
    val deviceId: String?,
    val deviceVersion: String?,
) : Parcelable {
    fun multiAccounts(): MultiAccounts {
        return MultiAccounts(
            masterFingerprint,
            keys.map { it.account() },
            device,
            deviceId,
            deviceVersion
        )
    }

    constructor(account: MultiAccounts) : this(
        account.masterFingerprint,
        account.keys.map { KeystoneAccount(it) },
        account.device,
        account.deviceId,
        account.deviceVersion
    )
}

class KeystoneOwnerSelectionFragment : BaseViewBindingFragment<FragmentKeystoneOwnerSelectionBinding>(),
    DerivedOwnerListAdapter.OnOwnerItemClickedListener {

    override fun screenId() = ScreenId.KEYSTONE_OWNER_SELECTION

    override suspend fun chainId(): BigInteger? = null

    private val navArgs by navArgs<KeystoneOwnerSelectionFragmentArgs>()
    private val hdKey: Account? by lazy { navArgs.hdKey?.account() }
    private val multiHDKeys: MultiAccounts? by lazy { navArgs.multiHDKeys?.multiAccounts() }
    private val maxPages: Int
        get() = if (multiHDKeys != null) 1 else DerivedOwnerPagingProvider.MAX_PAGES

    @Inject
    lateinit var viewModel: KeystoneOwnerSelectionViewModel

    private lateinit var adapter: DerivedOwnerListAdapter

    override fun inject(component: ViewComponent) {
        component.inject(this)
    }

    override fun viewModelProvider() = this

    override fun inflateBinding(inflater: LayoutInflater, container: ViewGroup?): FragmentKeystoneOwnerSelectionBinding =
        FragmentKeystoneOwnerSelectionBinding.inflate(inflater, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = DerivedOwnerListAdapter()
        adapter.setListener(this)
        adapter.addLoadStateListener { loadState ->
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {

                if (loadState.refresh is LoadState.Loading && adapter.itemCount == 0) {
                    binding.progress.visible(true)
                }

                if (viewModel.state.value?.viewAction is DerivedOwners && loadState.refresh is LoadState.NotLoading && adapter.itemCount == 0) {
                    binding.showMoreOwners.visible(false)
                } else {

                    binding.nextButton.isEnabled =
                        adapter.itemCount > 0 && adapter.getSelectedOwnerIndex() == 0L && adapter.peek(0)?.disabled == false
                    binding.progress.visible(false)
                    binding.showMoreOwners.visible(adapter.pagesVisible < maxPages)
                }
            }
        }

        with(binding) {
            backButton.setOnClickListener {
                Navigation.findNavController(it).navigateUp()
            }
            nextButton.setOnClickListener {

                val (address, path, sourceFingerprint) = viewModel.getOwnerData()

                findNavController().navigate(
                    KeystoneOwnerSelectionFragmentDirections.actionKeystoneOwnerSelectionFragmentToOwnerEnterNameFragment(
                        ownerAddress = address,
                        ownerKey = "",
                        fromSeedPhrase = false,
                        ownerType = OwnerTypeConverter().toValue(Owner.Type.KEYSTONE),
                        derivationPathWithIndex = path,
                        sourceFingerprint = sourceFingerprint
                    )
                )
            }
            owners.adapter = adapter
            owners.layoutManager = LinearLayoutManager(requireContext())
        }

        viewModel.state.observe(viewLifecycleOwner, Observer { state ->
            state.viewAction.let { viewAction ->
                when (viewAction) {
                    is DerivedOwners -> {
                        with(binding) {
                            showMoreOwners.setOnClickListener {
                                val dividerItemDecoration = DividerItemDecoration(context, LinearLayoutManager.VERTICAL)
                                dividerItemDecoration.setDrawable(AppCompatResources.getDrawable(requireContext(), R.drawable.divider)!!)
                                owners.addItemDecoration(dividerItemDecoration)
                                adapter.pagesVisible++
                                val visualFeedback = it.animate().alpha(0.0f)
                                visualFeedback.duration = 100
                                visualFeedback.setListener(object : Animator.AnimatorListener {

                                    override fun onAnimationRepeat(animation: Animator) {}

                                    override fun onAnimationEnd(animation: Animator) {
                                        adapter.notifyDataSetChanged()
                                        showMoreOwners.alpha = 1.0f
                                    }

                                    override fun onAnimationCancel(animation: Animator) {}

                                    override fun onAnimationStart(animation: Animator) {}
                                })
                                visualFeedback.start()
                                showMoreOwners.text = getString(R.string.signing_owner_selection_more)
                                showMoreOwners.visible(adapter.pagesVisible < maxPages)
                            }
                        }
                        lifecycleScope.launch {
                            adapter.submitData(viewAction.newOwners)
                        }
                    }
                }
            }
        })

        hdKey?.let {
            viewModel.loadFirstDerivedOwner(it)
        }
        multiHDKeys?.let {
            viewModel.loadFirstDerivedOwner(it)
        }
    }

    override fun onOwnerClicked(ownerIndex: Long) {
        binding.nextButton.isEnabled = true
        viewModel.setOwnerIndex(ownerIndex)
    }
}
