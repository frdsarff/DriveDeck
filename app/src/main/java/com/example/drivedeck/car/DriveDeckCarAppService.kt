package com.example.drivedeck.car

import android.content.Intent
import androidx.car.app.CarAppService
import androidx.car.app.Session
import androidx.car.app.validation.HostValidator

class DriveDeckCarAppService : CarAppService() {
    override fun createHostValidator(): HostValidator {
        // Development starter only. Restrict hosts before production release.
        return HostValidator.ALLOW_ALL_HOSTS_VALIDATOR
    }

    override fun onCreateSession(): Session = DriveDeckSession()
}

private class DriveDeckSession : Session() {
    override fun onCreateScreen(intent: Intent) = HomeScreen(carContext)
}
