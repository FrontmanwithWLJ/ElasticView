**Function**
	orientation参数的设置决定当前布局滑动为纵向滑动还是横向滑动
	setHeaderAdapter()
	setFooterAdapter()//如果没有设置这两个adapter就只是一个弹性滑动的layout
	setDamping(damping:Float,decrement:Boolean)//设置阻尼系数，是否递减
	setAnimTime()//设置弹回动画时间
	/**
	* @param progress 值位于\[0,offset\],其中offset是adapter设置的开始刷新或者加载的距离
	*/
	scrollProgress(progress: Int)
**Usage**

[![](https://jitpack.io/v/FrontmanwithWLJ/ElasticView.svg)](https://jitpack.io/#FrontmanwithWLJ/ElasticView)

Add it in your root build.gradle at the end of repositories:

	allprojects {
		repositories {
			...
			maven { url 'https://jitpack.io' }
		}
	}

Step 2. Add the dependency

	dependencies {
	        implementation 'com.github.FrontmanwithWLJ:ElasticView:1.1.0'
	}


***
**Version:1.0.0**
通过ElasticView嵌套View，达到列表弹性滑动，拖动。ElasticView是通过拦截所有触摸事件，分析是否需要向子View发送触摸事件，未支持fling
**Version:1.1.0**
实现NestedScrollParent2,处理子View的滑动事件，现在能处理fling事件了，但是目前仅支持实现了NestedScrollChild的列表View
