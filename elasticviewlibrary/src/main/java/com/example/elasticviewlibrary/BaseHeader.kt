package com.example.elasticviewlibrary

import android.content.Context
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.RelativeLayout
import android.widget.TextView

class BaseHeader(private val context:Context, offset: Int) : ElasticView.HeaderAdapter(offset) {
    private lateinit var view:View
    private lateinit var callBack: ElasticView.PullCallBack
    private var refreshListener: OnRefreshListener? =null
    private val icon by lazy { view.findViewById<ImageView>(R.id.img) }
    private val progressBar by lazy { view.findViewById<ProgressBar>(R.id.headerProgressBar) }
    private val text by lazy { view.findViewById<TextView>(R.id.text) }
    private val tab by lazy { view.findViewById<RelativeLayout>(R.id.tab) }

    //icon的方向
    private val DIRECTION_DOWN = true
    private val DIRECTION_UP = false
    private var direction = DIRECTION_DOWN
    //旋转动画
    private val rotate = AnimationUtils.loadAnimation(context,R.anim.base_header_anim_start_0)
    private val unRotate = AnimationUtils.loadAnimation(context,R.anim.base_header_anim_start_180)

    override fun getHeaderView(viewGroup: ViewGroup): View {
        view = LayoutInflater.from(context).inflate(R.layout.base_header,viewGroup,false)
        return view
    }

    override fun scrollProgress(progress: Int) {
    }

    override fun setPullCallBack(callBack: ElasticView.PullCallBack) {
        this.callBack = callBack
    }

    override fun pullToRefresh() {
        if (direction == DIRECTION_UP){
            icon.clearAnimation()
            //icon.startAnimation(unRotate)
            direction = DIRECTION_DOWN
        }
        text.text = "继续下拉更新"
    }

    override fun releaseToRefresh() {
        if (direction == DIRECTION_DOWN){
            icon.startAnimation(rotate)
            direction = DIRECTION_UP
        }
        text.text = "释放刷新"
    }

    override fun onRefresh() {
        isRefreshing = true
        view.post {
            icon.clearAnimation()
            icon.visibility = View.GONE
            progressBar.visibility = View.VISIBLE
            text.text = "正在更新"
        }
        if (refreshListener == null){
            Log.e("ElasticView BaseHeader","refreshListener is null")
            return
        }
        refreshListener!!.onRefresh()
    }

    fun overRefresh(msg:String){
        isRefreshing = false
        view.post {
            progressBar.visibility = View.GONE
            icon.visibility = View.VISIBLE
            text.text = msg
        }
        callBack.over()

    }

    fun setOnRefreshListener(listener: OnRefreshListener){
        refreshListener = listener
    }

    @FunctionalInterface
    interface OnRefreshListener{
        fun onRefresh()
    }
}