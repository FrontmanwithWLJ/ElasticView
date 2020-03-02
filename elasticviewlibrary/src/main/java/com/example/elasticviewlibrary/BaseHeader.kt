package com.example.elasticviewlibrary

import android.content.Context
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.ImageView
import android.widget.TextView

class BaseHeader(val context:Context,offset: Int) : ElasticView.HeaderAdapter(offset) {
    private lateinit var view:View
    private lateinit var callBack: ElasticView.PullCallBack
    private lateinit var refreshListener: OnRefreshListener
    private val icon by lazy { view.findViewById<ImageView>(R.id.img) }
    private val text by lazy { view.findViewById<TextView>(R.id.text) }
    //icon的方向
    private val DOWN = true
    private val UP = false
    private var direction = DOWN
    //旋转动画
    private val rotate = AnimationUtils.loadAnimation(context,R.anim.base_header_anim_start_0)
    private val unRotate = AnimationUtils.loadAnimation(context,R.anim.base_header_anim_start_180)

    override fun getHeaderView(viewGroup: ViewGroup): View {
        view = LayoutInflater.from(context).inflate(R.layout.base_header,viewGroup,false)
        return view
    }

    override fun scrollProgress(progress: Int) {
        val temp = progress.toFloat()/offset
        icon.scaleX = temp
        icon.scaleY = temp
//        Log.e("SL","progress=$temp")
        icon.rotation = 180*temp
    }

    override fun setPullCallBack(callBack: ElasticView.PullCallBack) {
        this.callBack = callBack
    }

    override fun pullToRefresh() {
        if (direction == UP){
            //icon.startAnimation(unRotate)
            direction = DOWN
        }
        text.text = "继续下拉更新"
    }

    override fun releaseToRefresh() {
        if (direction == DOWN){
            icon.rotation = 180F
            //icon.startAnimation(rotate)
            direction = UP
        }
        text.text = "释放刷新"
    }

    override fun onRefresh() {
        isRefreshing = true
        text.text = "正在更新"
        refreshListener.onRefresh()
    }

    fun overRefresh(msg:String){
        isRefreshing = false
        text.text = msg
        icon.startAnimation(unRotate)
        view.post{
            callBack.over()
        }
        direction = DOWN
    }

    fun setOnRefreshListener(listener: OnRefreshListener){
        refreshListener = listener
    }

    @FunctionalInterface
    interface OnRefreshListener{
        fun onRefresh()
    }
}