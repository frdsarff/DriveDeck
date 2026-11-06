package com.example.drivedeck.car

import android.util.Log
import com.example.drivedeck.R
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.CarIcon
import androidx.car.app.model.Template
import androidx.core.graphics.drawable.IconCompat
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

        val panAction = Action.Builder(Action.PAN)
            .setIcon(
                CarIcon.Builder(
                    IconCompat.createWithResource(carContext, R.drawable.ic_pan)
                ).build()
            )
            .build()

        return NavigationTemplate.Builder()
            .setActionStrip(actionStrip)
            .setMapActionStrip(ActionStrip.Builder().addAction(panAction).build())
            .build()
    }

    companion object {
        private const val TAG = "DriveDeckPoC"
    }
}
