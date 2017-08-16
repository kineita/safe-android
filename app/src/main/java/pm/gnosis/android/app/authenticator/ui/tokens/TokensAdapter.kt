package pm.gnosis.android.app.authenticator.ui.tokens

import android.content.Context
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import io.reactivex.subjects.PublishSubject
import kotlinx.android.synthetic.main.fragment_tokens_item.view.*
import pm.gnosis.android.app.authenticator.R
import pm.gnosis.android.app.authenticator.data.db.ERC20Token
import pm.gnosis.android.app.authenticator.di.ForView
import pm.gnosis.android.app.authenticator.di.ViewContext
import javax.inject.Inject


@ForView
class TokensAdapter @Inject constructor(@ViewContext private val context: Context) : RecyclerView.Adapter<TokensAdapter.ViewHolder>() {
    private val items = mutableListOf<ERC20Token>()
    val tokensSelectionSubject: PublishSubject<ERC20Token> = PublishSubject.create()
    val tokenRemovalSubject: PublishSubject<ERC20Token> = PublishSubject.create()
    var itemsClickable = true

    override fun onCreateViewHolder(parent: ViewGroup?, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent?.context).inflate(R.layout.fragment_tokens_item, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder?, position: Int) {
        holder?.bind(items[position])
    }

    override fun getItemCount() = items.size

    fun setItems(items: List<ERC20Token>) {
        this.items.clear()
        this.items.addAll(items)
        notifyDataSetChanged()
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView), View.OnClickListener {
        init {
            itemView.setOnClickListener(this)
            itemView.fragment_tokens_item_delete.setOnClickListener {
                tokenRemovalSubject.onNext(items[adapterPosition])
            }
        }

        fun bind(item: ERC20Token) {
            itemView.fragment_tokens_item_name.text = if (item.name.isNullOrEmpty()) item.address else item.name
            itemView.fragment_tokens_item_delete.visibility = if (item.verified) View.GONE else View.VISIBLE
        }

        override fun onClick(v: View?) {
            if (itemsClickable) {
                tokensSelectionSubject.onNext(items[adapterPosition])
            }
        }
    }
}