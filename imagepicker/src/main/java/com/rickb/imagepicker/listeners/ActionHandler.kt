package com.rickb.imagepicker.listeners

interface ActionHandler {
    fun onImageClick(isSelected: Boolean): Boolean

    /**
     * Should be called when the camera icon is clicked.
     */
    fun requestCameraImage()

    /**
     * requests the next page with images.
     */
    fun requestNextPage(page: Int)
}