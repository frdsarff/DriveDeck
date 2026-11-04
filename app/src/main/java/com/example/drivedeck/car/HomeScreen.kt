package com.example.drivedeck.car

import android.util.Log
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.Template
import androidx.car.app.navigation.model.NavigationTemplate

class HomeScreen(carContext: CarContext) : Screen(carContext) {

    private val surfaceRenderer = CarSurfaceRenderer(carContext, lifecycle)

    init {
        // Register directly; do not depend on receiving a future ON_CREATE event.
        surfaceRenderer.register()
    }

    override fun onGetTemplate(): Template {
        Log.d(TAG, "HomeScreen.onGetTemplate: Returning NavigationTemplate for SurfaceCallback")
        val actionStrip = ActionStrip.Builder()
            .addAction(
                Action.Builder()
                    .setTitle("Reload PoC")
                    .setOnClickListener {
                        Log.i(TAG, "ActionStrip 'Reload PoC' clicked")
                        surfaceRenderer.reloadDashboard()
                    }
                    .build()
            )
            .build()

        return NavigationTemplate.Builder()
            .setActionStrip(actionStrip)
            .build()
    }

    companion object {
        private const val TAG = "DriveDeckPoC"
    }
}
