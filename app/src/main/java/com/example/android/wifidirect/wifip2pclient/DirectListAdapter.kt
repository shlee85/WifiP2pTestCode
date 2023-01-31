package com.example.android.wifidirect.wifip2pclient

import android.content.Context
import android.text.Layout
import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.android.wifidirect.wifip2pclient.databinding.DirectListThemeBinding

data class SimpleModel(
    val name:String?,
    //val number:String,
    //var isSelected: Boolean
)

class DirectListAdapter (private val context: Context, private val list: ArrayList<SimpleModel>)
    :RecyclerView.Adapter<DirectListAdapter.MainViewHolder>() {

    private lateinit var binding: DirectListThemeBinding
    private var mItemClickListener: MyItemClickListener ?= null

    private var mMainAct = MainActivity()

    /* 콜백으로 사용할 onItemClick를 선언 */
    interface MyItemClickListener {
        fun onItemClick(pos: Int, name: String?)
        fun onItemSelected(pos: Int)
    }

    /* 콜백 등록 */
    fun setMyItemClickListener(listener: MyItemClickListener) {
        this.mItemClickListener = listener
    }

    inner class MainViewHolder(private val binding: DirectListThemeBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: SimpleModel) {
            Log.d(TAG, "bind() start")

            binding.model = item

            this.itemView.setOnClickListener {
                mItemClickListener?.onItemClick(adapterPosition, item.name)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MainViewHolder {
        Log.d(TAG, "onCreateViewHolder()")
        binding = DirectListThemeBinding.inflate(LayoutInflater.from(context), parent, false)

        return MainViewHolder(binding)
    }

    override fun onBindViewHolder(holder: MainViewHolder, position: Int) {
        Log.d(TAG, "onBindViewHolder(), pos -> $position")

        val item = list[position]
        holder.bind(item)
    }

    override fun getItemCount(): Int {
        return list.size
    }

    companion object {
        val TAG: String = DirectListAdapter::class.java.simpleName
    }
}