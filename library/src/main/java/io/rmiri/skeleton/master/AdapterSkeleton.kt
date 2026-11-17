package io.rmiri.skeleton.master

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewTreeObserver.OnGlobalLayoutListener
import androidx.recyclerview.widget.RecyclerView
import io.rmiri.skeleton.utils.CLog

/**
 * Created by Rasoul Miri on 12/3/17.
 */
abstract class AdapterSkeleton<T, VH : RecyclerView.ViewHolder?> : RecyclerView.Adapter<VH?>() {
    @JvmField
    protected var context: Context? = null
    @JvmField
    protected var items: MutableList<T?>? = mutableListOf<T?>()
    @JvmField
    protected var isCanSetAdapterListener: IsCanSetAdapterListener? = null
    @JvmField
    var skeletonConfig: SkeletonConfig = SkeletonConfig()

    protected fun measureHeightRecyclerViewAndItem(recyclerView: RecyclerView, idLayout: Int) {
        val viewTreeObserver = recyclerView.getViewTreeObserver()
        viewTreeObserver.addOnGlobalLayoutListener(object : OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                // Initial SkeletonDetail and set in adapter

                skeletonConfig.setRecyclerViewHeight(
                    recyclerView.getHeight().toFloat()
                ) // Height recyclerView

                val view =
                    (context!!.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater).inflate(
                        idLayout,
                        null
                    )
                view.getRootView()
                    .measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
                skeletonConfig.setItemHeight(
                    view.getRootView().getMeasuredHeight().toFloat()
                ) // Height Item
                skeletonConfig.setNumberItemShow(Math.round(skeletonConfig.getRecyclerViewHeight() / skeletonConfig.getItemHeight()) + 1) // Number item skeleton in adapter

                CLog.i(
                    ("skeletonConfig.getItemHeight == " + skeletonConfig.getItemHeight()
                            + "   skeletonConfig.getRecyclerViewHeight  " + skeletonConfig.getRecyclerViewHeight()
                            + "   skeletonConfig.getNumberItemShow  " + skeletonConfig.getNumberItemShow())
                )

                // Remove ViewTreeObserver
                recyclerView.getViewTreeObserver().removeOnGlobalLayoutListener(this)
                isCanSetAdapterListener!!.isCanSet()
            }
        })
    }

    override fun getItemCount(): Int {
        if (skeletonConfig.isSkeletonIsOn()) {
            if (skeletonConfig.getItemHeight() == 0f) {
                CLog.i("getItemCount ==> getItemHeight() is zero : " + "1")
                return 1
            } else {
                CLog.i("getItemCount ==> getNumberItemShow: " + skeletonConfig.getNumberItemShow())
                return skeletonConfig.getNumberItemShow()
            }
        } else {
            CLog.i("getItemCount ==> items.size(): " + items!!.size)
            return items!!.size
        }
    }

    fun addMoreDataAndSkeletonFinish(dataObjects: ArrayList<T?>) {
        // Add new data to dataObjects

        this.items = ArrayList<T?>()
        this.items!!.addAll(dataObjects)

        // Set false show Skeleton
        skeletonConfig.setSkeletonIsOn(false)

        // If size zero
        if (this.items == null || this.items!!.size == 0) {
            notifyDataSetChanged()
        }

        if (skeletonConfig.getNumberItemShow() > this.items!!.size) {
            notifyItemRangeRemoved(
                this.items!!.size,
                skeletonConfig.getNumberItemShow()
            ) // remove extra items skeleton
        }

        notifyItemRangeChanged(0, this.items!!.size, 1) // Update data for skeleton
    }

    //==============================================================================================
    // Getter and Setter
    fun getContext(): Context {
        return context!!
    }

    fun setContext(context: Context) {
        this.context = context
    }

    fun getIsCanSetAdapterListener(): IsCanSetAdapterListener {
        return isCanSetAdapterListener!!
    }

    fun setIsCanSetAdapterListener(IsCanSetAdapterListener: IsCanSetAdapterListener) {
        this.isCanSetAdapterListener = IsCanSetAdapterListener
    }
}
