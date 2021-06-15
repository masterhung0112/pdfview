package com.hungknow.pdfsdk.link

import com.hungknow.pdfsdk.models.LinkTapEvent

interface LinkHandler {
    /**
     * Called when link was tapped by user
     *
     * @param event current event
     */
    fun handleLinkEvent(event: LinkTapEvent)
}
