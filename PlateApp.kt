package com.example.platecollector

import android.app.Application
import com.hyperai.hyperlpr3.HyperLPR3
import com.hyperai.hyperlpr3.bean.HyperLPRParameter

class PlateApp : Application() {
    override fun onCreate() {
        super.onCreate()
        val parameter = HyperLPRParameter()
            .setDetLevel(HyperLPR3.DETECT_LEVEL_HIGH)
            .setMaxNum(1)
            .setRecConfidenceThreshold(0.85f)

        HyperLPR3.getInstance().init(this, parameter)

    }
}