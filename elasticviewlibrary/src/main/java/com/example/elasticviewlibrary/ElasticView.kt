package com.example.elasticviewlibrary

/**
 * PullView 布局内仅有一个View。headerView和footerView用户设置适配器
 */
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.core.view.NestedScrollingParent2
import androidx.core.view.ViewCompat
import androidx.core.view.get
import kotlin.math.abs

class ElasticView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) :
    LinearLayout(context, attrs, defStyleAttr) {
    private val TAG = "PullView"
    //down事件的记录
    private var oldY = 0f
    //目标子view
    private val middleView: View by lazy {
        if (childCount >= 2)
            getChildAt(1)
        else
            getChildAt(0)
    }
    //header
    private var headerView: View? = null
    private var headerAdapter: HeaderAdapter? = null
    //footer
    private var footerView: View? = null
    private var footerAdapter: FooterAdapter? = null
    //是否需要拦截
    private var isNeedToIntercept = false
    //弹回的动画时间
    private var animTimeLong = 300L
    private var animTimeShort = animTimeLong / 3
    //阻尼系数
    private var damping = 0.5f
    private var dampingTemp = 0.5f
    //阻尼系数是否需要逐减
    private var isDecrement = true
    //第一根手指
    private var firstFingerId = -1
    //当前活跃的触控点
    private var currentActionFingerId = -1
    //是否切换了手指
    private var isCheckFinger = false
    //fling是否停止
    private var stopFling = false

    private val refreshCallBack = object : PullCallBack {
        override fun over() {
            postDelayed({
                springBack(-headerAdapter!!.offset,300)
            },300)
        }
    }
    private val loadCallBack = object : PullCallBack {
        override fun over() {
            postDelayed({
                springBack(footerAdapter!!.offset,300)
            },300)
        }
    }

    init {
        //禁止裁剪布局,使得在页面外的view依然能显示
        this.clipChildren = false
        this.clipToPadding = false
    }

    //拦截所有消息
    override fun onInterceptTouchEvent(ev: MotionEvent?): Boolean {
        return true
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent?): Boolean {
        //Log.e(TAG,"get message")
        if ((headerAdapter != null && headerAdapter!!.isRefreshing) || (footerAdapter != null && footerAdapter!!.isLoading)) {
            //正在刷新中，拦截所有事件
            return true
        }
        if (event == null)
            return true
        if (firstFingerId == -1 && (event.action and event.actionMasked) != MotionEvent.ACTION_DOWN)
            return true

        when (event.action and event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                currentActionFingerId = event.getPointerId(event.actionIndex)
                firstFingerId = currentActionFingerId
                oldY = getOldXY(event, event.actionIndex)
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                currentActionFingerId = event.getPointerId(event.actionIndex)
                isCheckFinger = true
                return true
            }
            MotionEvent.ACTION_POINTER_UP -> {
                if (firstFingerId == event.getPointerId(event.actionIndex)) {
                    firstFingerId = currentActionFingerId
                    return true
                }
                currentActionFingerId = firstFingerId
                oldY = event.getY(event.findPointerIndex(currentActionFingerId))
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                //如果满足滑动条件则不共享move事件
                var index = event.findPointerIndex(currentActionFingerId)
                while (index == -1) {
                    //没能获取到信息
                    currentActionFingerId = event.getPointerId(event.actionIndex)
                    firstFingerId = currentActionFingerId
                    index = event.findPointerIndex(currentActionFingerId)
                }
                val newXY = getOldXY(event, index)
                if (isCheckFinger) {
                    oldY = newXY
                    isCheckFinger = false
                    return true
                }
                val offset = oldY - newXY
                oldY = newXY
                if (isNeedToIntercept) {
                    onScrolled(offset)
                    return true
                    //canScrollVertically(1)滑动到底部返回false，canScrollVertically(-1)滑动到顶部返回false
                } else if (canScroll(-1) && offset < -2f || canScroll(1) && offset > 2f) {
                    onScrolled(offset)
                    isNeedToIntercept = true
                    return true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (isNeedToIntercept) {//需要拦截，不共享
                    //清空手指记录
                    firstFingerId = -1
                    currentActionFingerId = -1
                    isNeedToIntercept = false
                    oldY = 0f
                    if (headerAdapter != null && getScrollXY() <= -headerAdapter!!.offset) {
                        springBack(getScrollXY() + headerAdapter!!.offset, 300)
                        headerAdapter!!.onRefresh()
                        return true
                    } else if (footerAdapter != null && getScrollXY() >= footerAdapter!!.offset) {
                        springBack(getScrollXY() - footerAdapter!!.offset, 300)
                        footerAdapter!!.onLoad()
                        return true
                    }
                    springBack(getScrollXY(), 300)
                    return true
                }
            }
        }
        //最后共享满足条件所有消息
        middleView.dispatchTouchEvent(event)
        return true
    }

    /**
     * 根据布局orientation属性判断横向纵向滑动是否触及边缘
     */
    private fun canScroll(direction: Int): Boolean {
        //canScrollVertically(1)滑动到底部返回false，canScrollVertically(-1)滑动到顶部返回false
        //canScrollHorizontally(1)滑动到右侧底部返回false，canScrollHorizontally(-1)滑动到左侧顶部返回false
        return if (orientation == VERTICAL) {
            !middleView.canScrollVertically(direction)
        } else {
            !middleView.canScrollHorizontally(direction)
        }
    }

    /**
     * @param index 当前活跃的手指
     * 获取event的X值或者Y值
     */
    private fun getOldXY(event: MotionEvent, index: Int): Float {
        return if (orientation == VERTICAL) {
            event.getY(index)
        } else {
            event.getX(index)
        }
    }

    /**
     * 获取X或者Y的滚动值
     */
    private fun getScrollXY(): Int {
        return if (orientation == VERTICAL) {
            scrollY
        } else {
            scrollX
        }
    }

    //对滚动消息初步处理，调整滚动值，更新状态
    private fun onScrolled(deltaY: Float) {
        if (!isNeedToIntercept)
            isNeedToIntercept = true
        if ((getScrollXY() < 0 && deltaY > 2) || (getScrollXY() > 0 && deltaY < -2)) {
            var offset = deltaY * damping
            if (offset < 0 && (-offset > getScrollXY())
                || offset > 0 && -offset < getScrollXY()
            ) {
                offset = (-getScrollXY()).toFloat()
            }
            scroll(offset.toInt())
        } else if (canScroll(-1) && deltaY < -2f || canScroll(1) && deltaY > 2f) {//下拉和上滑
            val offset = deltaY * dampingTemp
            scroll(offset.toInt())
            if (isDecrement) {
                calcDamping()
            }
        }
        if (getScrollXY() < 0) {
            if (headerAdapter == null)
                return
            if (getScrollXY() < -headerAdapter!!.offset) {
                headerAdapter!!.releaseToRefresh()
            } else {
                headerAdapter!!.scrollProgress(abs(getScrollXY()))
                headerAdapter!!.pullToRefresh()
            }
        } else {
            if (footerAdapter == null)
                return
            if (getScrollXY() > footerAdapter!!.offset) {
                footerAdapter!!.releaseToLoad()
            } else {
                footerAdapter!!.scrollProgress(abs(getScrollXY()))
                footerAdapter!!.pullToLoad()
            }
        }
    }

    //滚动视图
    private fun scroll(delta: Int) {
        if (delta == 0) return
        if (orientation == VERTICAL) {
            scrollBy(0, delta)
        } else {
            scrollBy(delta, 0)
        }
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
//        Log.e(TAG,"springBack=$offset currentXY =${getScrollXY()}")
        val animator = ValueAnimator.ofInt(0, -offset)
        animator.duration = animTime
        val oy = getScrollXY()    //当前getScrollXY()
        animator.addUpdateListener { animation ->
            if (orientation == VERTICAL)
                scrollTo(scrollX, oy + animation.animatedValue as Int)
            else
                scrollTo(oy + animation.animatedValue as Int, scrollY)
        }
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
        headerView = view
        addView(headerView, 0)//最底层
        headerView!!.post {
            val height = headerView!!.measuredHeight
            val width = headerView!!.measuredWidth
            var layoutParams: LayoutParams?
            //使其位于布局范围之外
            if (orientation == VERTICAL) {
                layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, height)
                layoutParams.topMargin = -height
            } else {
                layoutParams = LayoutParams(width, LayoutParams.MATCH_PARENT)
                layoutParams.leftMargin = -width
            }
            headerView!!.layoutParams = layoutParams
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
        footerView = view
        addView(footerView, 2)

    }

    /**
     * 将自身置于底层,仅适用于相对布局、帧布局 RelativeLayout，FrameLayout
     */
    fun sendToBack() {
        val group = parent as ViewGroup
        val count = group.childCount
        for (i in 0 until count) {
            if (group[i] != this) {
                group[i].bringToFront()//置顶除自身外的所有view
            }
        }
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