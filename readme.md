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
	        implementation 'com.github.FrontmanwithWLJ:ElasticView:1.0.0'
	}


***
通过ElasticView嵌套View，达到列表弹性滑动，拖动。ElasticView是通过拦截所有触摸事件，分析是否需要向子View发送触摸事件
