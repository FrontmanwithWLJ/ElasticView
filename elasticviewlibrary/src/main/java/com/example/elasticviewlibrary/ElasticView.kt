package com.example.elasticviewlibrary

/**
 * ElasticView 布局内仅有一个View。
 * headerView和footerView用户设置适配器时添加
 * 拦截所有触摸事件不靠谱，太多不确定因素
 * 还是用NestedScrollParent
 * 设置orientation以确定布局纵横滑动
 */
import android.animation.Animator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.os.Looper
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.OverScroller
import android.widget.Scroller
import androidx.core.animation.addListener
import androidx.core.view.NestedScrollingParent2
import androidx.core.view.ViewCompat
import androidx.core.view.get
import java.util.concurrent.locks.ReentrantLock
import kotlin.math.abs

class ElasticView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) :
    LinearLayout(context, attrs, defStyleAttr), NestedScrollingParent2 {
    private val TAG = "ElasticView"
    //header
    private var headerAdapter: HeaderAdapter? = null
    //footer
    private var footerAdapter: FooterAdapter? = null
    //弹回的动画时间
    private var animTimeLong = 300L
    private var animTimeShort = animTimeLong / 2
    //阻尼系数
    private var damping = 0.5f
    private var dampingTemp = 0.5f
    //阻尼系数是否需要逐减
    private var isDecrement = true
    //标记布局是否移动
    private var isMove = false
    //标记是否出fling状态
    private var isFling = false
    //当前是否允许fling
    private var allowFling = true
    //弹回动画锁
    private val lock = ReentrantLock()

    //加载完成的回调
    private val refreshCallBack = object : PullCallBack {
        override fun over() {
            headerAdapter!!.isRefreshing = false
            postDelayed({
                springBack(getScrollXY(), 300)
            }, 300)
        }
    }
    private val loadCallBack = object : PullCallBack {
        override fun over() {
            footerAdapter!!.isLoading = false
            postDelayed({
                springBack(getScrollXY(), 300)
            }, 300)
        }
    }

    init {
        //禁止裁剪布局,使得在页面外的view依然能显示
        this.clipChildren = false
        this.clipToPadding = false
    }


    //处理嵌套滑动
    override fun onNestedScrollAccepted(child: View, target: View, axes: Int, type: Int) {}

    //此处无须判断child类型
    override fun onStartNestedScroll(child: View, target: View, axes: Int, type: Int): Boolean {
        return (axes and when (orientation) {
            HORIZONTAL -> ViewCompat.SCROLL_AXIS_HORIZONTAL
            else -> ViewCompat.SCROLL_AXIS_VERTICAL
        }) != 0
    }

    /**
     * @param consumed 记录parent消耗的距离，consumed[0]-->X  [1]-->y
     */
    override fun onNestedPreScroll(target: View, dx: Int, dy: Int, consumed: IntArray, type: Int) {
        if (!shouldScroll()) {//处于加载状态不允许child滑动
            consumed[0] = dx
            consumed[1] = dy
            return
        }
        //判断是否滑动到边界,滑动到边界的情况交给parent处理
        if (canScroll(target, dx = dx, dy = dy)) {
            if (type == ViewCompat.TYPE_TOUCH) {
                if (!isMove)
                    isMove = true
            } else if (type == ViewCompat.TYPE_NON_TOUCH) {
                if (!allowFling) return//禁止fling
                isFling = true
                if (abs(getScrollXY()) >= 100 && isFling) {
                    allowFling = false//禁止fling
                    springBack(getScrollXY(), animTimeLong)
                    return
                }
            }
            //吃掉所有位移
            consumed[0] = dx
            consumed[1] = dy
            scrollBy((dx * dampingTemp).toInt(), (dy * dampingTemp).toInt())
            calcDamping()
        } else {//此处有两种情况 一是未到边界的滑动，二是已经移动过布局，但是现在开始反向滑动了
            if (isMove) {
                val temp = if (orientation == VERTICAL) dy * damping else dx * damping
                val scrollOffset = getScrollXY()
                //防止越界，如果数据越界就设为边界值
                val offset =
                    if (scrollOffset <= 0 && temp > -scrollOffset) -scrollOffset
                    else if (scrollOffset >= 0 && temp < -scrollOffset) -scrollOffset
                    else temp.toInt()

                scrollBy(offset, offset)
                calcDamping()
                consumed[0] = dx
                consumed[1] = dy
            }
        }
    }

    override fun onNestedScroll(
        target: View,
        dxConsumed: Int,
        dyConsumed: Int,
        dxUnconsumed: Int,
        dyUnconsumed: Int,
        type: Int
    ) {
    }

    //子view停止移动
    override fun onStopNestedScroll(target: View, type: Int) {
        if (!shouldScroll()) return
        //Log.e(TAG,"first move =$isMove fling =$isFling type=$type")
        if (!isMove && isFling) {
            allowFling = true
            isFling = false
            return
        }
        if (!isMove) return
        val scrollOffset = getScrollXY()
        isMove = false
        if (headerAdapter != null && scrollOffset < 0 && scrollOffset < -headerAdapter!!.offset) {
            springBack(headerAdapter!!.offset + scrollOffset, animTimeShort)
            headerAdapter!!.isRefreshing = true
            headerAdapter!!.onRefresh()
            return
        }
        if (footerAdapter != null && scrollOffset > 0 && scrollOffset < footerAdapter!!.offset) {
            springBack(scrollOffset - footerAdapter!!.offset, animTimeShort)
            footerAdapter!!.isLoading = true
            footerAdapter!!.onLoad()
            return
        }
        springBack(scrollOffset, animTimeLong)

    }

    override fun scrollBy(x: Int, y: Int) {
        //根据布局选择移动水平垂直
        if (orientation == VERTICAL) {
            super.scrollBy(0, y)
        } else {
            super.scrollBy(x, 0)
        }
        val scrollOffset = getScrollXY()
        //更新控件header，footer状态
        if (scrollOffset < 0) {
            if (headerAdapter == null) return
            if (-scrollOffset <= headerAdapter!!.offset) {
                headerAdapter!!.scrollProgress(-scrollOffset)
                headerAdapter!!.pullToRefresh()
            } else {
                headerAdapter!!.releaseToRefresh()
            }
        } else {
            if (footerAdapter == null) return
            if (scrollOffset <= footerAdapter!!.offset) {
                footerAdapter!!.scrollProgress(-scrollOffset)
                footerAdapter!!.pullToLoad()
            } else {
                footerAdapter!!.releaseToLoad()
            }
        }
    }

    /**
     * 根据布局orientation属性判断横向纵向滑动是否触及边缘
     */
    private fun canScroll(child: View, direction: Int = 0, dx: Int = 0, dy: Int = 0): Boolean {
        //canScrollVertically(1)滑动到底部返回false，canScrollVertically(-1)滑动到顶部返回false
        //canScrollHorizontally(1)滑动到右侧底部返回false，canScrollHorizontally(-1)滑动到左侧顶部返回false
        return if (orientation == VERTICAL) {
            if (dy == 0)
                !child.canScrollVertically(direction)
            else
                !child.canScrollVertically(dy)
        } else {
            if (dx == 0)
                !child.canScrollHorizontally(direction)
            else
                !child.canScrollHorizontally(dx)
        }
    }

    /**
     * 获取X或者Y的滚动值
     */
    private fun getScrollXY(): Int {
        return if (orientation == VERTICAL)
            scrollY
        else
            scrollX
    }

    //判断当前是否处于刷新加载状态
    private fun shouldScroll(): Boolean {
        return !((headerAdapter != null && headerAdapter!!.isRefreshing)
                || (footerAdapter != null && footerAdapter!!.isLoading))
    }

    /**
     * 设置滑动阻尼系数
     * @param isDecrement 是否随距离增大阻尼系数
     */
    fun setDamping(damping: Float, isDecrement: Boolean = true) {
        this.damping = damping
        this.dampingTemp = damping
        this.isDecrement = isDecrement
    }

    /**
     * @param time 设置弹回动画的执行事时间
     */
    fun setAnimTime(time: Long) {
        animTimeLong = time
    }

    //计算阻尼变化
    private fun calcDamping() {
        //val offset = abs(getScrollXY()).toDouble()
        //双曲正切函数(e^x-e^(-x))/(e^x+e^(-x)),随着x递增，y从零开始增加无限趋近于0
        //dampingTemp = damping * (1-((exp(offset) - exp(-offset))/(exp(offset) + exp(-offset)))).toFloat()
        var count = (abs(getScrollXY()) / animTimeShort).toInt()
        if (count == 0) {
            count = 1
        }
        dampingTemp = damping / count
    }

    //弹回动画
    private fun springBack(offset: Int, animTime: Long) {
        if (lock.isLocked || !shouldScroll()) return
        lock.lock()
        val animator = ValueAnimator.ofInt(0, -offset)
        animator.duration = animTime
        val oy = getScrollXY()    //当前getScrollXY()
        animator.addUpdateListener { animation ->
            if (orientation == VERTICAL)
                scrollTo(scrollX, oy + animation.animatedValue as Int)
            else
                scrollTo(oy + animation.animatedValue as Int, scrollY)
        }
        animator.addListener(object : Animator.AnimatorListener {
            override fun onAnimationRepeat(animation: Animator?) {}

            override fun onAnimationEnd(animation: Animator?) {
                lock.unlock()
            }

            override fun onAnimationCancel(animation: Animator?) {}

            override fun onAnimationStart(animation: Animator?) {}
        })
        post { animator.start() }
    }

    fun setHeaderAdapter(adapter: HeaderAdapter) {
        headerAdapter = adapter
        headerAdapter!!.setPullCallBack(refreshCallBack)
        addHeaderView(headerAdapter!!.getHeaderView(this))
    }

    private fun addHeaderView(view: View) {
        if (childCount == 3) {
            Log.e(TAG, "only support three views")
            return
        }
        addView(view, 0)//最底层
        view.post {
            val height = view.measuredHeight
            val width = view.measuredWidth
            val layoutParams: LayoutParams?
            //使其位于布局范围之外
            if (orientation == VERTICAL) {
                layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, height)
                layoutParams.topMargin = -height
            } else {
                layoutParams = LayoutParams(width, LayoutParams.MATCH_PARENT)
                layoutParams.leftMargin = -width
            }
            view.layoutParams = layoutParams
        }
    }

    fun setFooterAdapter(adapter: FooterAdapter) {
        footerAdapter = adapter
        footerAdapter!!.setPullCallBack(loadCallBack)
        addFooterView(footerAdapter!!.getFooterView(this))
    }

    //相比于HeaderView，footerView处于middleView的最后，无须设置marginTop
    private fun addFooterView(view: View) {
        if (childCount == 3) {
            Log.e(TAG, "only support three views")
            return
        }
        addView(view, 2)

    }


    abstract class HeaderAdapter(var offset: Int) {
        var isRefreshing = false
        abstract fun getHeaderView(viewGroup: ViewGroup): View
        /**
         * @param progress in 0 .. offset
         */
        abstract fun scrollProgress(progress: Int)

        abstract fun setPullCallBack(callBack: PullCallBack)
        //下拉刷新
        abstract fun pullToRefresh()

        //释放刷新
        abstract fun releaseToRefresh()

        //刷新中
        abstract fun onRefresh()
    }

    abstract class FooterAdapter(var offset: Int) {
        var isLoading = false
        abstract fun getFooterView(viewGroup: ViewGroup): View
        /**
         * @param progress in 0 .. offset
         */
        abstract fun scrollProgress(progress: Int)

        abstract fun setPullCallBack(callBack: PullCallBack)
        //上拉加载
        abstract fun pullToLoad()

        //释放加载
        abstract fun releaseToLoad()

        //加载中
        abstract fun onLoad()
    }

    interface PullCallBack {
        fun over()
    }

}