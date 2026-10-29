package com.example.drivedeck.car

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template

class HomeScreen(carContext: CarContext) : Screen(carContext) {
    override fun onGetTemplate(): Template {
        val items = ItemList.Builder()
            .addItem(
                Row.Builder()
                    .setTitle("DriveDeck 2.0")
                    .addText("Android Auto starter is connected.")
                    .build()
            )
            .addItem(
                Row.Builder()
                    .setTitle("Custom web UI")
                    .addText("Phone preview is available from the app icon.")
                    .build()
            )
            .addItem(
                Row.Builder()
                    .setTitle("Next milestone")
                    .addText("Add supported media and navigation integrations.")
                    .build()
            )
            .build()

        return ListTemplate.Builder()
            .setTitle("DriveDeck 2.0")
            .setHeaderAction(Action.APP_ICON)
            .setSingleList(items)
            .build()
    }
}
