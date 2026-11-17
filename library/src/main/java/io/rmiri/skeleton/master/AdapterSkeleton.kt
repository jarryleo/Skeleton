package io.rmiri.skeleton.master

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewTreeObserver.OnGlobalLayoutListener
import androidx.recyclerview.widget.RecyclerView
import io.rmiri.skeleton.BuildConfig
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
        recyclerView.getViewTreeObserver()
            .addOnGlobalLayoutListener(object : OnGlobalLayoutListener {
                override fun onGlobalLayout() {
                    // 先摘掉监听：保证只执行一次，且后续任何 return 都不会让监听器随 RecyclerView 泄漏
                    // (viewTreeObserver 可能已合并失效，需从当前存活的 observer 上移除)
                    val liveObserver = recyclerView.getViewTreeObserver()
                    if (liveObserver.isAlive) {
                        liveObserver.removeOnGlobalLayoutListener(this)
                    }

                    val context = context ?: run {
                        CLog.i("measureHeightRecyclerViewAndItem: context is null, forgot setContext()?")
                        return
                    }
                    val canSetListener = isCanSetAdapterListener ?: run {
                        CLog.i("measureHeightRecyclerViewAndItem: isCanSetAdapterListener is null, forgot setIsCanSetAdapterListener()?")
                        return
                    }

                    // Initial SkeletonDetail and set in adapter
                    skeletonConfig.setRecyclerViewHeight(
                        recyclerView.getHeight().toFloat()
                    ) // Height recyclerView

                    val view =
                        (context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater).inflate(
                            idLayout,
                            null
                        )
                    view.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
                    skeletonConfig.setItemHeight(
                        view.getMeasuredHeight().toFloat()
                    ) // Height Item

                    // itemHeight 为 0 时(如根布局高度 match_parent 在 UNSPECIFIED 下测得 0)，
                    // 除法会得到 21 亿级别的条目数导致 RecyclerView 挂死/OOM，这里退化为 1 条
                    skeletonConfig.setNumberItemShow(
                        if (skeletonConfig.getItemHeight() > 0f) {
                            Math.round(skeletonConfig.getRecyclerViewHeight() / skeletonConfig.getItemHeight()) + 1
                        } else {
                            1
                        }
                    ) // Number item skeleton in adapter

                    CLog.i(
                        ("skeletonConfig.getItemHeight == " + skeletonConfig.getItemHeight()
                                + "   skeletonConfig.getRecyclerViewHeight  " + skeletonConfig.getRecyclerViewHeight()
                                + "   skeletonConfig.getNumberItemShow  " + skeletonConfig.getNumberItemShow())
                    )

                    canSetListener.isCanSet()
                }
            })
    }

    override fun getItemCount(): Int {
        // 滚动/布局期间会被高频调用，日志仅在 debug 构建拼接，避免无谓的字符串分配
        if (skeletonConfig.isSkeletonIsOn()) {
            if (skeletonConfig.getItemHeight() == 0f) {
                if (BuildConfig.DEBUG) {
                    CLog.i("getItemCount ==> getItemHeight() is zero : 1")
                }
                return 1
            } else {
                if (BuildConfig.DEBUG) {
                    CLog.i("getItemCount ==> getNumberItemShow: " + skeletonConfig.getNumberItemShow())
                }
                return skeletonConfig.getNumberItemShow()
            }
        } else {
            if (BuildConfig.DEBUG) {
                CLog.i("getItemCount ==> items.size(): " + items?.size)
            }
            return items?.size ?: 0
        }
    }

    fun addMoreDataAndSkeletonFinish(dataObjects: ArrayList<T?>) {
        // Add new data to dataObjects
        this.items = ArrayList<T?>()
        this.items!!.addAll(dataObjects)

        // Set false show Skeleton
        skeletonConfig.setSkeletonIsOn(false)

        val newSize = this.items!!.size
        if (newSize == 0) {
            // 数据为空时整表刷新即可；继续补发 range 通知会移除不存在的条目，
            // 触发 RecyclerView 一致性崩溃(Inconsistency detected)
            notifyDataSetChanged()
            return
        }

        if (skeletonConfig.getNumberItemShow() > newSize) {
            // 只移除骨架态多出来的那部分条目；移除数量超过实际存在的条目数
            // 同样会触发 RecyclerView 的 IndexOutOfBoundsException
            notifyItemRangeRemoved(
                newSize,
                skeletonConfig.getNumberItemShow() - newSize
            ) // remove extra items skeleton
        }

        notifyItemRangeChanged(0, newSize, 1) // Update data for skeleton
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
